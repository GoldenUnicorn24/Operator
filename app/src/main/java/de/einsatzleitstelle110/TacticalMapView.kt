package de.einsatzleitstelle110

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*
import org.json.JSONArray

/**
 * Original native dispatch map: bundled geography, original car silhouettes and honest simulation
 * routes. No browser, network tiles or borrowed game assets.
 */
class TacticalMapView(
    context: Context,
    private val game: GameController,
    private val onCall: (IncidentState) -> Unit,
    private val onUnit: (UnitState) -> Unit,
) : View(context) {
    init {
        NativeRouting.initialize(context)
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    var filter = "ALLE"
    var showRoutes = true
    var showHeatmap = false
    var showCoverage = false
    var showMinimap = true
    var zoom = 1f
    var nightMode: Boolean? = null
    // Compatibility with the previous settings screen; all geography stays offline.
    var useOnlineStreets = false
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var topInset = 64f
    private var bottomInset = 138f
    private var overviewWholeState = false
    private var lastScope = ""
    private var lastRegion = ""
    private var screenHits = listOf<MapClustering.Cluster<Any>>()
    private var pausedBefore = true
    private var closed = false

    private data class Motion(
        var from: GeoPoint,
        var target: GeoPoint,
        var current: GeoPoint,
        var started: Long,
    )

    private val motions = HashMap<String, Motion>()

    private data class Road(val name: String, val points: List<GeoPoint>)

    private val roads: List<Road> by lazy {
        try {
            val json =
                JSONArray(
                    context.assets.open("hamburg-roads.json").bufferedReader().use { it.readText() }
                )
            (0 until json.length()).map { n ->
                val row = json.getJSONObject(n)
                val a = row.getJSONArray("points")
                Road(
                    row.optString("name"),
                    (0 until a.length()).map { k ->
                        val v = a.getJSONArray(k)
                        GeoPoint(v.getDouble(0), v.getDouble(1))
                    },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
    private var roadCacheKey = ""
    private var roadPath = Path()
    private val detector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    zoom = (zoom * detector.scaleFactor).coerceIn(.8f, 36f)
                    moved = true
                    invalidate()
                    return true
                }
            },
        )

    data class Transform(
        val region: GeoRegion,
        val w: Float,
        val h: Float,
        val centerX: Double,
        val centerY: Double,
        val fitted: Double,
        val zoom: Float,
        val panX: Float,
        val panY: Float,
        val top: Float = 0f,
        val bottom: Float = 0f,
    ) {
        companion object {
            fun worldX(lon: Double) = (lon + 180.0) / 360.0

            fun worldY(lat: Double): Double {
                val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
                return (1.0 - ln(tan(r) + 1 / cos(r)) / PI) / 2
            }
        }

        fun point(lon: Double, lat: Double) =
            PointF(
                (w / 2 + (worldX(lon) - centerX) * fitted * zoom + panX).toFloat(),
                (top + (h - top - bottom) / 2 + (worldY(lat) - centerY) * fitted * zoom + panY)
                    .toFloat(),
            )
    }

    fun setContentInsets(topDp: Float, bottomDp: Float) {
        topInset = topDp
        bottomInset = bottomDp
        invalidate()
    }

    fun resetCamera() {
        overviewWholeState = false
        zoom = 1f
        panX = 0f
        panY = 0f
        roadCacheKey = ""
        invalidate()
    }

    fun fitWholeState() {
        resetCamera()
        overviewWholeState = true
        invalidate()
    }

    fun focus(lon: Double, lat: Double) {
        zoom = max(zoom, 4f)
        val t = makeTransform(zoom, 0f, 0f)
        val a = t.point(lon, lat)
        panX = width / 2f - a.x
        panY = (topInset * density + (height - bottomInset * density)) / 2 - a.y
        invalidate()
    }

    fun close() {
        closed = true
        motions.clear()
    }

    private fun makeTransform(z: Float, px: Float, py: Float): Transform {
        val r = game.region()
        val bounds =
            if (overviewWholeState) r.bounds
            else OperationsAreas.bounds(r.code, game.state.scope) ?: r.bounds
        val minX = Transform.worldX(bounds[0])
        val maxX = Transform.worldX(bounds[2])
        val minY = Transform.worldY(bounds[3])
        val maxY = Transform.worldY(bounds[1])
        val w = max(1f, width.toFloat())
        val h = max(1f, height.toFloat())
        val top = min(topInset * density, h * .3f)
        val bottom = min(bottomInset * density, h * .35f)
        val scale =
            min(
                (w - 46 * density).coerceAtLeast(40f) / (maxX - minX).coerceAtLeast(1e-7),
                (h - top - bottom - 40 * density).coerceAtLeast(40f) /
                    (maxY - minY).coerceAtLeast(1e-7),
            )
        return Transform(
            r,
            w,
            h,
            (minX + maxX) / 2,
            (minY + maxY) / 2,
            scale,
            z,
            px,
            py,
            top,
            bottom,
        )
    }

    private fun paint(color: Int, width: Float = 1f, style: Paint.Style = Paint.Style.FILL) {
        p.reset()
        p.isAntiAlias = true
        p.color = color
        p.strokeWidth = width * density
        p.style = style
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
    }

    private fun text(
        c: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) {
        paint(color)
        p.textSize = size * density
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(s, x, y, p)
    }

    private fun path(t: Transform, rings: List<List<GeoPoint>>): Path =
        Path().apply {
            fillType = Path.FillType.EVEN_ODD
            for (ring in rings) {
                ring.forEachIndexed { i, v ->
                    val a = t.point(v.lon, v.lat)
                    if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
                }
                close()
            }
        }

    private fun point(u: UnitState, now: Long): GeoPoint {
        val target = GeoPoint(u.lon, u.lat)
        val motion = motions.getOrPut(u.id) { Motion(target, target, target, now) }
        if (motion.target != target) {
            motion.from = motion.current
            motion.target = target
            motion.started = now
        }
        if (!game.state.paused && game.state.phase == "RUNNING") {
            val f = ((now - motion.started) / 950.0).coerceIn(0.0, 1.0)
            motion.current =
                GeoPoint(
                    motion.from.lon + (motion.target.lon - motion.from.lon) * f,
                    motion.from.lat + (motion.target.lat - motion.from.lat) * f,
                )
        } else if (u.status != "DRIVING" && u.status != "RETURNING") motion.current = target
        return motion.current
    }

    private fun visible(u: UnitState): Boolean =
        when (filter) {
            "ALLE" -> true
            "FREI" -> game.available(u)
            "GEBUNDEN" -> u.status in listOf("DRIVING", "WORKING", "RETURNING")
            else -> filter == u.type
        }

    private fun inScope(p: GeoPoint) =
        OperationsAreas.contains(game.state.region, game.state.scope, p)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (closed) return
        if (lastRegion != game.state.region || lastScope != game.state.scope) {
            lastRegion = game.state.region
            lastScope = game.state.scope
            resetCamera()
        }
        val now = SystemClock.uptimeMillis()
        val paused = game.state.paused || game.state.phase != "RUNNING"
        if (pausedBefore && !paused)
            motions.values.forEach {
                it.from = it.current
                it.started = now
            }
        pausedBefore = paused
        val hour = (game.state.startMinute + game.state.elapsed) % 1440 / 60
        val night = nightMode ?: (hour < 7 || hour >= 19)
        val background =
            Color.rgb(if (night) 8 else 218, if (night) 19 else 229, if (night) 29 else 232)
        val land = if (night) Color.rgb(20, 39, 52) else Color.rgb(238, 243, 236)
        val ink = if (night) Color.rgb(204, 222, 231) else Color.rgb(42, 67, 76)
        c.drawColor(background)
        val t = makeTransform(zoom, panX, panY)
        paint(if (night) 0xff18313e.toInt() else 0xffc6d5d8.toInt(), .5f, Paint.Style.STROKE)
        var x = panX % (52 * density)
        while (x < width) {
            c.drawLine(x, 0f, x, height.toFloat(), p)
            x += 52 * density
        }
        var y = panY % (52 * density)
        while (y < height) {
            c.drawLine(0f, y, width.toFloat(), y, p)
            y += 52 * density
        }
        val boundary = path(t, t.region.rings)
        paint(land)
        c.drawPath(boundary, p)
        c.save()
        c.clipPath(boundary)
        if (t.region.code == "DE-HH") drawRoads(c, t, night)
        else {
            // These are simulated intercity corridors, never painted as invented local streets.
            paint(if (night) 0xff375563.toInt() else 0xffa4b9bc.toInt(), 1f, Paint.Style.STROKE)
            p.pathEffect = DashPathEffect(floatArrayOf(5 * density, 5 * density), 0f)
            for ((i, a) in t.region.cities.withIndex()) for (b in
                t.region.cities
                    .drop(i + 1)
                    .sortedBy { GeoData.km(GeoPoint(a.lon, a.lat), GeoPoint(it.lon, it.lat)) }
                    .take(2)) {
                val aa = t.point(a.lon, a.lat)
                val bb = t.point(b.lon, b.lat)
                c.drawLine(aa.x, aa.y, bb.x, bb.y, p)
            }
        }
        c.restore()
        paint(if (night) 0xff709bac.toInt() else 0xff699098.toInt(), 1.5f, Paint.Style.STROKE)
        c.drawPath(boundary, p)
        val scopeOutline = OperationsAreas.outline(game.state.region, game.state.scope)
        if (scopeOutline.isNotEmpty()) {
            paint(0xff6bdfcb.toInt(), 2f, Paint.Style.STROKE)
            c.drawPath(path(t, scopeOutline), p)
        }
        val labels = mutableListOf<RectF>()
        for (city in t.region.cities) {
            val q = t.point(city.lon, city.lat)
            if (q.x < 10 || q.x > width - 30 || q.y < t.top || q.y > height - t.bottom) continue
            val rect =
                RectF(
                    q.x,
                    q.y - 14 * density,
                    q.x + city.name.length * 5.5f * density,
                    q.y + 3 * density,
                )
            if (labels.any { RectF.intersects(it, rect) }) continue
            labels.add(rect)
            text(c, city.name, q.x, q.y, 10f, ink, true)
        }
        if (showHeatmap)
            for (i in
                game.state.incidents.filter {
                    it.region == game.state.region &&
                        it.status in listOf("OPEN", "DRIVING", "WORKING")
                }) {
                val q = t.point(i.lon, i.lat)
                paint(0x44ff986b)
                p.shader =
                    RadialGradient(
                        q.x,
                        q.y,
                        55 * density,
                        intArrayOf(0x66ff586e, 0x00ff586e),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                c.drawCircle(q.x, q.y, 55 * density, p)
            }
        if (showCoverage)
            for (city in if (game.state.region == "DE-HH")
                game.stations().map { GeoCity(it.name, it.lon, it.lat) }
            else t.region.cities) {
                val q = t.point(city.lon, city.lat)
                paint(0x1468d9cc)
                c.drawCircle(q.x, q.y, 58 * density, p)
                paint(0x4468d9cc, 1f, Paint.Style.STROKE)
                c.drawCircle(q.x, q.y, 58 * density, p)
            }
        val all = mutableListOf<MapClustering.Marker<Any>>()
        val units = game.state.units.filter { it.region == game.state.region && visible(it) }
        for (u in units) {
            val geo = point(u, now)
            if (
                !inScope(geo) &&
                    game.state.scope != "STATE" &&
                    !overviewWholeState &&
                    u.incidentId.isBlank()
            )
                continue
            val q = t.point(geo.lon, geo.lat)
            if (showRoutes && u.status in listOf("DRIVING", "RETURNING")) {
                paint(0xff60c8ee.toInt(), 2f, Paint.Style.STROKE)
                p.pathEffect = DashPathEffect(floatArrayOf(8 * density, 5 * density), 0f)
                val route = Path()
                val points =
                    u.routePoints.ifEmpty {
                        listOf(GeoPoint(u.lon, u.lat), GeoPoint(u.destLon, u.destLat))
                    }
                for ((n, v) in points.withIndex()) {
                    val a = t.point(v.lon, v.lat)
                    if (n == 0) route.moveTo(a.x, a.y) else route.lineTo(a.x, a.y)
                }
                c.drawPath(route, p)
            }
            all.add(MapClustering.Marker(q.x, q.y, u))
        }
        if (filter != "WACHEN")
            for (i in game.state.incidents) {
                if (
                    i.region != game.state.region ||
                        i.status !in listOf("OPEN", "DRIVING", "WORKING", "HANDED") ||
                        (!overviewWholeState && !inScope(GeoPoint(i.lon, i.lat)))
                )
                    continue
                if (filter.startsWith("P") && filter != "P" + i.priority) continue
                val q = t.point(i.lon, i.lat)
                all.add(MapClustering.Marker(q.x, q.y, i))
            }
        if (filter == "ALLE" || filter == "WACHEN") {
            if (game.state.region == "DE-HH") {
                for (station in game.stations().filter {
                    overviewWholeState || inScope(GeoPoint(it.lon, it.lat))
                }) {
                    val q = t.point(station.lon, station.lat)
                    all.add(MapClustering.Marker(q.x, q.y, station))
                }
            } else {
                for (city in t.region.cities.filter {
                    overviewWholeState || inScope(GeoPoint(it.lon, it.lat))
                }) {
                    val q = t.point(city.lon, city.lat)
                    all.add(MapClustering.Marker(q.x, q.y, city))
                }
            }
        }
        val inside =
            all.filter {
                it.x >= -30 * density &&
                    it.x <= width + 30 * density &&
                    it.y >= t.top - 20 * density &&
                    it.y <= height - t.bottom + 20 * density
            }
        screenHits = MapClustering.cluster(inside, 38 * density)
        for (cluster in screenHits) {
            if (cluster.members.size > 1) drawCluster(c, cluster)
            else
                when (val value = cluster.members[0].value) {
                    is UnitState -> drawVehicle(c, t, cluster.x, cluster.y, value)
                    is IncidentState -> drawCall(c, cluster.x, cluster.y, value, now, paused)
                    is GeoCity -> drawFacility(c, cluster.x, cluster.y, "SPIEL")
                    is DispatchStation -> drawFacility(c, cluster.x, cluster.y, value.type)
                }
        }
        val countUnits = all.count { it.value is UnitState }
        val countCalls = all.count { it.value is IncidentState }
        val hudY = (t.top + 19 * density).coerceAtMost(height - 20 * density)
        paint(0xe70a1a26.toInt())
        c.drawRoundRect(
            RectF(8 * density, t.top + 4 * density, width - 8 * density, t.top + 30 * density),
            7 * density,
            7 * density,
            p,
        )
        text(
            c,
            "$countUnits Fahrzeuge  ·  $countCalls Lagen  ·  ${screenHits.size} Pins/Cluster",
            17 * density,
            hudY,
            10f,
            0xffd6ebf2.toInt(),
        )
        if (showMinimap) drawMinimap(c, t)
        text(
            c,
            "© GeoBasis-DE/BKG · Hamburg: LGV/BWVI · Fahrwege simuliert",
            12 * density,
            height - t.bottom + 14 * density,
            7.5f,
            ink,
        )
        // The native Activity supplies the main toolbar and shift actions; no obscured duplicate
        // HUD.
        if (
            !paused && (units.any { it.status in listOf("DRIVING", "RETURNING") } || countCalls > 0)
        )
            postInvalidateDelayed(33)
    }

    private fun drawMinimap(c: Canvas, t: Transform) {
        val w = 88 * density
        val h = 68 * density
        val x = width - w - 12 * density
        val y = height - t.bottom - h - 8 * density
        if (y < t.top + 40 * density) return
        c.save()
        c.translate(x, y)
        paint(0xe60a1d2b.toInt())
        c.drawRoundRect(RectF(0f, 0f, w, h), 6 * density, 6 * density, p)
        val b = t.region.bounds
        val loX = Transform.worldX(b[0])
        val hiX = Transform.worldX(b[2])
        val loY = Transform.worldY(b[3])
        val hiY = Transform.worldY(b[1])
        val scale = min((w - 12 * density) / (hiX - loX), (h - 12 * density) / (hiY - loY))
        val mini = Transform(t.region, w, h, (loX + hiX) / 2, (loY + hiY) / 2, scale, 1f, 0f, 0f)
        val boundary = path(mini, t.region.rings)
        paint(0xff34556a.toInt())
        c.drawPath(boundary, p)
        paint(0xff7c9ead.toInt(), .7f, Paint.Style.STROKE)
        c.drawPath(boundary, p)
        val outline = OperationsAreas.outline(game.state.region, game.state.scope)
        if (outline.isNotEmpty()) {
            paint(0xff79e3ce.toInt(), 1f, Paint.Style.STROKE)
            c.drawPath(path(mini, outline), p)
        }
        fun screenWorld(px: Double, py: Double) =
            PointF(
                (w / 2 + (px - mini.centerX) * scale).toFloat(),
                (h / 2 + (py - mini.centerY) * scale).toFloat(),
            )
        val a =
            screenWorld(
                t.centerX + (-t.w / 2 - t.panX) / (t.fitted * t.zoom),
                t.centerY + (-(t.h - t.top - t.bottom) / 2 - t.panY) / (t.fitted * t.zoom),
            )
        val z =
            screenWorld(
                t.centerX + (t.w / 2 - t.panX) / (t.fitted * t.zoom),
                t.centerY + ((t.h - t.top - t.bottom) / 2 - t.panY) / (t.fitted * t.zoom),
            )
        c.clipRect(3 * density, 3 * density, w - 3 * density, h - 3 * density)
        paint(0xffd1f7ed.toInt(), 1f, Paint.Style.STROKE)
        c.drawRect(RectF(a.x, a.y, z.x, z.y), p)
        c.restore()
    }

    private fun drawRoads(c: Canvas, t: Transform, night: Boolean) {
        val key = "${width}:${height}:$zoom:$panX:$panY:${game.state.scope}:$overviewWholeState"
        if (roadCacheKey != key) {
            val path = Path()
            for (r in roads) for ((index, v) in r.points.withIndex()) {
                val q = t.point(v.lon, v.lat)
                if (index == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
            }
            roadPath = path
            roadCacheKey = key
        }
        paint(if (night) 0xff385563.toInt() else 0xffbcc9bb.toInt(), 3.7f, Paint.Style.STROKE)
        c.drawPath(roadPath, p)
        paint(if (night) 0xff7e9399.toInt() else 0xfffefcf0.toInt(), 1.1f, Paint.Style.STROKE)
        c.drawPath(roadPath, p)
    }

    private fun drawFacility(c: Canvas, x: Float, y: Float, kind: String) {
        paint(when (kind) {
            "POLIZEI" -> 0xff75adf4.toInt()
            "BERUFSFEUERWEHR", "FREIWILLIGE_FEUERWEHR" -> 0xfff18d7b.toInt()
            "RETTUNG" -> 0xff94e4b4.toInt()
            else -> 0xffb7b4e8.toInt()
        })
        c.drawRoundRect(
            RectF(x - 9 * density, y - 9 * density, x + 9 * density, y + 9 * density),
            3 * density,
            3 * density,
            p,
        )
        val label = when (kind) {
            "POLIZEI" -> "P"
            "BERUFSFEUERWEHR", "FREIWILLIGE_FEUERWEHR" -> "F"
            else -> "W"
        }
        text(c, label, x - 4 * density, y + 4 * density, 10f, 0xff10232e.toInt(), true)
    }

    private fun drawCall(
        c: Canvas,
        x: Float,
        y: Float,
        i: IncidentState,
        now: Long,
        paused: Boolean,
    ) {
        val tint =
            when (i.priority) {
                1 -> 0xffff5d77.toInt()
                2 -> 0xffffc66d.toInt()
                else -> 0xff7dbbff.toInt()
            }
        val pulse = if (paused) 0f else ((now % 1500) / 1500f) * 8 * density
        paint((tint and 0x00ffffff) or 0x33000000)
        c.drawCircle(x, y, 18 * density + pulse, p)
        val shape =
            Path().apply {
                moveTo(x, y - 13 * density)
                lineTo(x + 12 * density, y)
                lineTo(x, y + 13 * density)
                lineTo(x - 12 * density, y)
                close()
            }
        paint(0xff112531.toInt())
        c.drawPath(shape, p)
        paint(tint, 2f, Paint.Style.STROKE)
        c.drawPath(shape, p)
        text(c, i.priority.toString(), x - 3.7f * density, y + 4 * density, 11f, tint, true)
    }

    private fun drawCluster(c: Canvas, cluster: MapClustering.Cluster<Any>) {
        val calls = cluster.members.mapNotNull { it.value as? IncidentState }
        val tint =
            when (calls.minOfOrNull { it.priority }) {
                1 -> 0xffff687d.toInt()
                2 -> 0xffffc66d.toInt()
                else -> 0xff68c7e9.toInt()
            }
        paint(0xff102b3b.toInt())
        c.drawCircle(cluster.x, cluster.y, 22 * density, p)
        paint(tint, 2f, Paint.Style.STROKE)
        c.drawCircle(cluster.x, cluster.y, 21 * density, p)
        text(
            c,
            cluster.members.size.toString(),
            cluster.x - (if (cluster.members.size > 9) 7f else 3.7f) * density,
            cluster.y + 2 * density,
            13f,
            tint,
            true,
        )
        text(
            c,
            if (calls.isEmpty()) "Kräfte" else "P${calls.minOf{it.priority}} · Kräfte",
            cluster.x - 18 * density,
            cluster.y + 13 * density,
            7f,
            0xffc4dce5.toInt(),
        )
    }

    private fun drawVehicle(c: Canvas, t: Transform, x: Float, y: Float, u: UnitState) {
        if (u.vehicleClass == "HELI" || (u.vehicleClass == "AUTO" && u.type == "HELI")) {
            drawHelicopter(c, x, y, u)
            return
        }
        val dest = t.point(u.destLon, u.destLat)
        val moving = u.status in listOf("DRIVING", "RETURNING")
        val angle =
            if (moving)
                Math.toDegrees(atan2((dest.y - y).toDouble(), (dest.x - x).toDouble())).toFloat() +
                    90
            else 0f
        val tint =
            when (u.type) {
                "SEK" -> 0xffbd9cff.toInt()
                "MEK" -> 0xff78e0bc.toInt()
                "KRIPO" -> 0xff80cbd7.toInt()
                "RETTUNG" -> 0xffffca75.toInt()
                "FEUERWEHR" -> 0xfffa816e.toInt()
                else -> 0xff68c9ff.toInt()
            }
        val armored = u.vehicleClass == "GESCHUETZT" ||
            (u.vehicleClass == "AUTO" && u.type in listOf("SEK", "TASKFORCE"))
        val van = u.vehicleClass in listOf("VAN", "LKW", "GESCHUETZT", "SUV") ||
            (u.vehicleClass == "AUTO" && (armored || u.type in listOf("BEREITSCHAFT", "FEUERWEHR", "RETTUNG")))
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        paint(0x99020a10.toInt())
        c.drawOval(RectF(-13 * density, -22 * density, 14 * density, 23 * density), p)
        val halfW = if (u.vehicleClass == "LKW") 12f else if (van) 10f else 8f
        val halfH = if (u.vehicleClass == "LKW") 21f else if (van) 18f else 16f
        paint(0xff071018.toInt())
        for (side in listOf(-1, 1)) for (end in listOf(-1, 1)) c.drawRoundRect(
            RectF(
                (side * halfW - 2) * density,
                (end * 10 - 3) * density,
                (side * halfW + 2) * density,
                (end * 10 + 3) * density,
            ),
            1 * density,
            1 * density,
            p,
        )
        paint(
            if (armored) 0xff333a49.toInt()
            else if (u.vehicleClass == "ZIVIL" || u.type in listOf("MEK", "KRIPO")) 0xff8a949c.toInt()
            else 0xffd7e2e7.toInt()
        )
        c.drawRoundRect(
            RectF(-halfW * density, -halfH * density, halfW * density, halfH * density),
            4 * density,
            4 * density,
            p,
        )
        paint(if (armored) 0xff788391.toInt() else 0xff17354a.toInt())
        c.drawRoundRect(
            RectF((-halfW + 2) * density, -9 * density, (halfW - 2) * density, 8 * density),
            2 * density,
            2 * density,
            p,
        )
        if (u.vehicleClass != "ZIVIL" && u.type !in listOf("MEK", "KRIPO")) {
            paint(tint)
            c.drawRect(-halfW * density, -3 * density, halfW * density, 4 * density, p)
            paint(0xffe3f6ff.toInt())
            c.drawRect(-6 * density, -1 * density, 6 * density, 1 * density, p)
        }
        paint(if (armored) 0xffa5afbc.toInt() else 0xfff8f5ce.toInt())
        c.drawRect(
            (-halfW + 1) * density,
            (-halfH + 1) * density,
            (-halfW + 4) * density,
            (-halfH + 3) * density,
            p,
        )
        c.drawRect(
            (halfW - 4) * density,
            (-halfH + 1) * density,
            (halfW - 1) * density,
            (-halfH + 3) * density,
            p,
        )
        if (u.type != "MEK") {
            paint(if (moving) 0xff60ccff.toInt() else 0xff426c8d.toInt())
            c.drawRoundRect(
                RectF(-6 * density, -5 * density, 6 * density, -2 * density),
                1 * density,
                1 * density,
                p,
            )
        }
        c.restore()
        paint(
            if (game.available(u)) 0xff6ee7b7.toInt()
            else if (u.status == "TRAINING") 0xffbca2f5.toInt() else 0xffffc76b.toInt()
        )
        c.drawCircle(x + 13 * density, y + 14 * density, 4 * density, p)
        paint(0xe50b1a26.toInt())
        c.drawRoundRect(
            RectF(x - 21 * density, y + 22 * density, x + 21 * density, y + 35 * density),
            3 * density,
            3 * density,
            p,
        )
        if (moving)
            text(
                c,
                "${u.travelLeft+u.readinessLeft} min",
                x - 15 * density,
                y - 25 * density,
                8f,
                0xffffd58b.toInt(),
                true,
            )
        text(
            c,
            when (u.type) {
                "STREIFE" -> "STREIFE"
                "BEREITSCHAFT" -> "BEPO"
                else -> u.type.take(7)
            },
            x - 18 * density,
            y + 31 * density,
            7f,
            tint,
            true,
        )
    }

    private fun drawHelicopter(c: Canvas, x: Float, y: Float, u: UnitState) {
        paint(0xff152c3b.toInt())
        c.drawCircle(x, y, 22 * density, p)
        paint(0xff76cde7.toInt(), 3f, Paint.Style.STROKE)
        c.drawLine(x - 21 * density, y, x + 21 * density, y, p)
        c.drawLine(x, y - 19 * density, x, y + 19 * density, p)
        paint(0xffdce8ec.toInt())
        c.drawOval(RectF(x - 6 * density, y - 13 * density, x + 6 * density, y + 11 * density), p)
        paint(0xff224759.toInt())
        c.drawOval(RectF(x - 4 * density, y - 10 * density, x + 4 * density, y - 2 * density), p)
        paint(0xff9ebdce.toInt(), 3f, Paint.Style.STROKE)
        c.drawLine(x, y + 10 * density, x, y + 24 * density, p)
        c.drawLine(x - 5 * density, y + 22 * density, x + 5 * density, y + 22 * density, p)
        text(c, "HELI", x - 12 * density, y + 37 * density, 8f, 0xff8adcf2.toInt(), true)
    }

    private fun choose(cluster: MapClustering.Cluster<Any>) {
        if (cluster.members.size == 1) {
            select(cluster.members[0].value)
            return
        }
        val members =
            cluster.members
                .map { it.value }
                .sortedBy { if (it is IncidentState) it.priority else 10 }
        AlertDialog.Builder(context)
            .setTitle("${members.size} Objekte an diesem Ort")
            .setItems(
                members
                    .map {
                        when (it) {
                            is UnitState ->
                                "${it.callsign} · ${it.type} · ${it.status}" +
                                    (if (it.status in listOf("DRIVING", "RETURNING"))
                                        " · ${it.travelLeft+it.readinessLeft} Min"
                                    else "")
                            is IncidentState -> "P${it.priority} · ${it.title}"
                            is GeoCity -> "Spielwache ${it.name} · fiktiv"
                            is DispatchStation -> "${it.name} · ${if (it.custom) "eigene Wache" else "amtlicher Standort"}"
                            else -> "Objekt"
                        }
                    }
                    .toTypedArray()
            ) { _, index ->
                select(members[index])
            }
            .setNeutralButton("Vergrößern") { _, _ ->
                val geo =
                    when (val first = members.first()) {
                        is UnitState -> GeoPoint(first.lon, first.lat)
                        is IncidentState -> GeoPoint(first.lon, first.lat)
                        is GeoCity -> GeoPoint(first.lon, first.lat)
                        is DispatchStation -> GeoPoint(first.lon, first.lat)
                        else -> null
                    }
                geo?.let { focus(it.lon, it.lat) }
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun select(value: Any) {
        when (value) {
            is UnitState -> onUnit(value)
            is IncidentState -> onCall(value)
            is DispatchStation ->
                AlertDialog.Builder(context)
                    .setTitle(value.name)
                    .setMessage(
                        "${value.address}\n${if (value.custom) "Eigene Spielwache" else "Amtlicher Standort · Hamburger Geodaten"}"
                    )
                    .setPositiveButton("Auf Karte") { _, _ -> focus(value.lon, value.lat) }
                    .setNegativeButton("Schließen", null)
                    .show()
            is GeoCity ->
                AlertDialog.Builder(context)
                    .setTitle("Spielwache ${value.name}")
                    .setMessage(
                        "Fiktiver Spielstützpunkt. Keine reale Polizeidienststelle.\n${value.lat}, ${value.lon}"
                    )
                    .setPositiveButton("Auf Karte") { _, _ -> focus(value.lon, value.lat) }
                    .setNegativeButton("Schließen", null)
                    .show()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                downX = e.x
                downY = e.y
                moved = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1 && !detector.isInProgress) {
                    if (hypot(e.x - downX, e.y - downY) > 8 * density) moved = true
                    if (moved) {
                        panX += e.x - lastX
                        panY += e.y - lastY
                        invalidate()
                    }
                    lastX = e.x
                    lastY = e.y
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && !detector.isInProgress) {
                    val hit = screenHits.minByOrNull { hypot(it.x - e.x, it.y - e.y) }
                    if (hit != null && hypot(hit.x - e.x, hit.y - e.y) <= 28 * density) choose(hit)
                    else performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
