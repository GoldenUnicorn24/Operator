package de.einsatzleitstelle110

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

/**
 * Native live operations map. A background-rendered topographic base map (OSM main roads, Natural
 * Earth water and settlements, official borders) plus a live overlay: stations, calls with
 * deadline rings, vehicles moving along their routed roads, dispatch previews. No WebView, no
 * network tiles, no borrowed game art.
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
    var showLegend = false
    var zoom = 1f
    var nightMode: Boolean? = null
    // Compatibility with the previous settings screen; all geography stays offline.
    var useOnlineStreets = false

    /** Call currently opened in the dispatch panel (highlighted, candidates previewed). */
    var selectedCall: IncidentState? = null
    /** Units ticked in the dispatch panel: their prospective routes are drawn. */
    var previewUnits: Set<String> = emptySet()
    var onEmptyTap: (() -> Unit)? = null

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
    private var closed = false
    private val renderer = MapRenderer(context, density) { postInvalidate() }
    private val previewCache = HashMap<String, NativeRoute>()

    /** Smoothly animated route progress per unit, so vehicles glide along the roads between ticks. */
    private class Anim(
        val key: Int,
        val points: List<GeoPoint>,
        val cum: DoubleArray,
        var from: Double,
        var to: Double,
        var started: Long,
        var shown: Double,
    )

    private val anims = HashMap<String, Anim>()

    private val detector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    zoomAround(d.scaleFactor, d.focusX, d.focusY)
                    moved = true
                    return true
                }
            },
        )

    private val taps =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    zoomAround(2f, e.x, e.y)
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

        fun camera(): Camera {
            val a = fitted * zoom
            return Camera(
                a,
                w / 2 - centerX * a + panX,
                top + (h - top - bottom) / 2 - centerY * a + panY,
                w.toInt(),
                h.toInt(),
            )
        }
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
        invalidate()
    }

    fun fitWholeState() {
        resetCamera()
        overviewWholeState = true
        invalidate()
    }

    fun zoomBy(factor: Float) = zoomAround(factor, width / 2f, (topInset * density + height - bottomInset * density) / 2)

    private fun maxZoom(): Float {
        val t = makeTransform(1f, 0f, 0f)
        val lat = (t.region.bounds[1] + t.region.bounds[3]) / 2
        // Street level: about 450 px per km.
        return max(36.0, 450 * density * 40075 * cos(Math.toRadians(lat)) / t.fitted).toFloat()
    }

    private fun zoomAround(factor: Float, fx: Float, fy: Float) {
        val old = zoom
        val next = (zoom * factor).coerceIn(.8f, maxZoom())
        if (next == old) return
        val t = makeTransform(old, panX, panY)
        val cy = t.top + (t.h - t.top - t.bottom) / 2
        val dx = (fx - t.w / 2 - panX) / old
        val dy = (fy - cy - panY) / old
        zoom = next
        panX = fx - t.w / 2 - dx * next
        panY = fy - cy - dy * next
        invalidate()
    }

    fun focus(lon: Double, lat: Double) {
        zoom = max(zoom, min(maxZoom(), 6f))
        val t = makeTransform(zoom, 0f, 0f)
        val a = t.point(lon, lat)
        panX = width / 2f - a.x
        panY = (topInset * density + (height - bottomInset * density)) / 2 - a.y
        invalidate()
    }

    /** Keep a point visible without changing the zoom (used when a call is selected). */
    fun reveal(lon: Double, lat: Double, visibleBottom: Float = height - bottomInset * density) {
        val t = makeTransform(zoom, panX, panY)
        val a = t.point(lon, lat)
        val top = t.top + 30 * density
        val bottom = (visibleBottom - 30 * density).coerceAtLeast(top + 40 * density)
        if (a.x in 30 * density..width - 30 * density && a.y in top..bottom) return
        panX += width / 2f - a.x
        panY += (top + bottom) / 2 - a.y
        invalidate()
    }

    fun close() {
        closed = true
        anims.clear()
        renderer.close()
    }

    fun prepareRegion() = renderer.prepare(game.region(), game.regions)

    /** True once the topographic base map of the current state has been rendered. */
    fun baseMapReady() = renderer.frame?.key?.startsWith(game.state.region + "|") == true

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
        return Transform(r, w, h, (minX + maxX) / 2, (minY + maxY) / 2, scale, z, px, py, top, bottom)
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

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, center: Boolean = false) {
        paint(color)
        p.textSize = size * density
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val dx = if (center) p.measureText(s) / 2 else 0f
        c.drawText(s, x - dx, y, p)
    }

    private fun pill(c: Canvas, s: String, x: Float, y: Float, size: Float, fg: Int, bg: Int) {
        p.reset()
        p.isAntiAlias = true
        p.textSize = size * density
        p.typeface = Typeface.DEFAULT_BOLD
        val tw = p.measureText(s)
        paint(bg)
        c.drawRoundRect(RectF(x - tw / 2 - 4 * density, y - size * density, x + tw / 2 + 4 * density, y + 3.5f * density), 4 * density, 4 * density, p)
        text(c, s, x, y, size, fg, true, true)
    }

    private fun night(): Boolean {
        val hour = if (game.state.phase == "PLANNING") game.state.startMinute / 60 else game.hour()
        return nightMode ?: (hour < 7 || hour >= 19)
    }

    private fun visible(u: UnitState): Boolean =
        when (filter) {
            "ALLE" -> true
            "FREI" -> game.available(u)
            "GEBUNDEN" -> u.status in listOf("DRIVING", "WORKING", "ON_SCENE", "RETURNING")
            "EINSÄTZE", "P1", "P2", "P3" -> u.incidentId.isNotBlank()
            "WACHEN" -> false
            else -> filter == u.type
        }

    private fun inScope(p: GeoPoint) = OperationsAreas.contains(game.state.region, game.state.scope, p)

    // --- Unit animation along the route -----------------------------------------------------

    private fun routeOf(u: UnitState): List<GeoPoint> =
        u.routePoints.ifEmpty { listOf(GeoPoint(u.startLon, u.startLat), GeoPoint(u.destLon, u.destLat)) }

    private fun anim(u: UnitState, now: Long, paused: Boolean): Pair<GeoPoint, Float>? {
        if (u.status !in listOf("DRIVING", "RETURNING")) {
            anims.remove(u.id)
            return null
        }
        val pts = routeOf(u)
        if (pts.size < 2) return null
        val key = pts.size * 31 + pts.first().hashCode() * 17 + pts.last().hashCode() + u.status.hashCode()
        val target =
            if (u.readinessLeft > 0 || u.travelTotal <= 0) 0.0 else 1.0 - u.travelLeft.toDouble() / u.travelTotal
        var a = anims[u.id]
        if (a == null || a.key != key) {
            val cum = DoubleArray(pts.size)
            for (i in 1 until pts.size) cum[i] = cum[i - 1] + GeoData.km(pts[i - 1], pts[i])
            a = Anim(key, pts, cum, target, target, now, target)
            anims[u.id] = a
        }
        if (target != a.to) {
            a.from = a.shown
            a.to = target
            a.started = now
        }
        a.shown =
            if (paused) a.to
            else a.from + (a.to - a.from) * ((now - a.started) / 1000.0).coerceIn(0.0, 1.0)
        return along(a, a.shown)
    }

    private fun along(a: Anim, progress: Double): Pair<GeoPoint, Float> {
        val total = a.cum.last()
        if (total <= 0) return a.points.last() to 0f
        val d = total * progress.coerceIn(0.0, 1.0)
        var i = a.cum.indexOfFirst { it >= d }.let { if (it <= 0) 1 else it }
        if (i >= a.points.size) i = a.points.size - 1
        val seg = (a.cum[i] - a.cum[i - 1]).takeIf { it > 0 } ?: 1.0
        val f = ((d - a.cum[i - 1]) / seg).coerceIn(0.0, 1.0)
        val p0 = a.points[i - 1]
        val p1 = a.points[i]
        val pos = GeoPoint(p0.lon + (p1.lon - p0.lon) * f, p0.lat + (p1.lat - p0.lat) * f)
        val heading = Math.toDegrees(atan2(-(p1.lat - p0.lat), (p1.lon - p0.lon) * cos(Math.toRadians(p0.lat)))).toFloat() + 90f
        return pos to heading
    }

    // --- Drawing ------------------------------------------------------------------------------

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (closed) return
        if (lastRegion != game.state.region || lastScope != game.state.scope) {
            lastRegion = game.state.region
            lastScope = game.state.scope
            resetCamera()
            anims.clear()
            previewCache.clear()
        }
        val now = SystemClock.uptimeMillis()
        val paused = game.state.paused || game.state.phase != "RUNNING"
        val isNight = night()
        val pal = MapPalette(isNight)
        val t = makeTransform(zoom, panX, panY)
        val cam = t.camera()
        // Base map: exact frame when ready, otherwise the last frame re-projected (pan/zoom).
        val key = "${game.state.region}|${game.state.scope}|$isNight|${cam.w}x${cam.h}|${"%.6g".format(cam.a)}|${cam.bx.roundToInt()}|${cam.by.roundToInt()}"
        val frame = renderer.frame
        c.drawColor(pal.sea)
        if (frame != null && frame.key.substringBefore('|') == game.state.region && frame.key.split('|')[2] == isNight.toString()) {
            val s = (cam.a / frame.camera.a).toFloat()
            val m = Matrix().apply {
                setScale(s, s)
                postTranslate((cam.bx - s * frame.camera.bx).toFloat(), (cam.by - s * frame.camera.by).toFloat())
            }
            paint(Color.WHITE)
            p.isFilterBitmap = true
            c.drawBitmap(frame.bitmap, m, p)
        }
        if (frame?.key != key && width > 0 && height > 0)
            renderer.request(MapRenderer.Request(cam, t.region, game.regions, game.state.scope, isNight, key))
        val pxKm = cam.pxPerKm((t.region.bounds[1] + t.region.bounds[3]) / 2) / density
        val detailed = pxKm > 2.5

        if (showHeatmap) drawHeatmap(c, t)
        if (showCoverage) drawCoverage(c, t, pxKm)

        val units = game.state.units.filter { it.region == game.state.region }
        val positions = HashMap<String, Pair<GeoPoint, Float>>()
        for (u in units) positions[u.id] = anim(u, now, paused) ?: (GeoPoint(u.lon, u.lat) to 0f)

        // Routes of moving units.
        if (showRoutes)
            for (u in units) {
                if (u.status !in listOf("DRIVING", "RETURNING") || !visible(u)) continue
                val a = anims[u.id] ?: continue
                drawRoute(c, t, a.points, a.cum, a.shown, if (u.status == "RETURNING") 0xff6f8fa3.toInt() else routeColor(u), u.routeBasis == "DISTANCE_APPROXIMATION")
            }
        // Dispatch preview: prospective routes of ticked candidates.
        val sel = selectedCall
        if (sel != null) {
            for (id in previewUnits) {
                val u = units.find { it.id == id } ?: continue
                val r = previewCache.getOrPut("${u.id}:${sel.id}:${u.lat}:${u.lon}") { NativeRouting.routeFor(u, GeoPoint(sel.lon, sel.lat)) }
                val path = Path()
                r.points.forEachIndexed { i, g -> val q = t.point(g.lon, g.lat); if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }
                paint(0x55ffffff, 7f, Paint.Style.STROKE)
                c.drawPath(path, p)
                paint(0xfff5e663.toInt(), 3f, Paint.Style.STROKE)
                p.pathEffect = DashPathEffect(floatArrayOf(10 * density, 6 * density), ((now / 25) % 1000).toFloat() * -density)
                c.drawPath(path, p)
                p.pathEffect = null
            }
            if (previewCache.size > 80) previewCache.clear()
        }

        val all = mutableListOf<MapClustering.Marker<Any>>()
        // Stations: where units are based (fictional game stations at real towns).
        val stations = units.groupBy { "%.3f:%.3f".format(it.homeLat, it.homeLon) }
        if (filter == "ALLE" || filter == "WACHEN")
            for ((_, group) in stations) {
                val h = group.first()
                if (game.state.scope != "STATE" && !overviewWholeState && !inScope(GeoPoint(h.homeLon, h.homeLat))) continue
                val q = t.point(h.homeLon, h.homeLat)
                all.add(MapClustering.Marker(q.x, q.y, Station(h.homeLon, h.homeLat, group)))
            }
        for (u in units) {
            if (!visible(u)) continue
            val (geo, _) = positions.getValue(u.id)
            if (u.status == "FREE" && u.lat == u.homeLat && u.lon == u.homeLon && filter != "FREI" && !detailed) continue
            if (!inScope(geo) && game.state.scope != "STATE" && !overviewWholeState && u.incidentId.isBlank()) continue
            val q = t.point(geo.lon, geo.lat)
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
                if (filter in GameController.TYPES) continue
                val q = t.point(i.lon, i.lat)
                all.add(MapClustering.Marker(q.x, q.y, i))
            }
        val inside =
            all.filter {
                it.x >= -30 * density && it.x <= width + 30 * density &&
                    it.y >= t.top - 20 * density && it.y <= height - t.bottom + 20 * density
            }
        // Calls never disappear into a cluster with vehicles: cluster them separately.
        val callHits = MapClustering.cluster(inside.filter { it.value is IncidentState }, (if (detailed) 26 else 30) * density)
        val otherHits = MapClustering.cluster(inside.filter { it.value !is IncidentState }, (if (detailed) 30 else 34) * density)
        screenHits = callHits + otherHits
        for (cluster in otherHits) {
            if (cluster.members.size > 1) drawCluster(c, cluster)
            else
                when (val v = cluster.members[0].value) {
                    is UnitState -> drawUnit(c, cluster.x, cluster.y, v, positions.getValue(v.id).second, detailed, pal)
                    is Station -> drawStation(c, cluster.x, cluster.y, v)
                }
        }
        for (cluster in callHits) {
            if (cluster.members.size > 1) drawCallCluster(c, cluster)
            else drawCall(c, cluster.x, cluster.y, cluster.members[0].value as IncidentState, now, paused)
        }
        if (sel != null) {
            val q = t.point(sel.lon, sel.lat)
            paint(0xffffffff.toInt(), 2f, Paint.Style.STROKE)
            val r = 25 * density + ((now % 1200) / 1200f) * 6 * density
            c.drawCircle(q.x, q.y, r, p)
        }
        if (showMinimap) drawMinimap(c, t)
        if (showLegend) drawLegend(c, t)
        val basis =
            when (NativeRouting.loadedGraph(game.state.region)?.basis) {
                "OSM_MAIN_ROADS_APPROXIMATION" -> "Hauptstraßennetz (Spielnäherung)"
                "HAMBURG_MAIN_ROADS_APPROXIMATION" -> "Hamburger Hauptstraßen (Spielnäherung)"
                else -> "Distanznäherung"
            }
        text(c, "© OpenStreetMap-Mitwirkende (ODbL) · Natural Earth · GeoBasis-DE/BKG · Routen: $basis", 10 * density, height - t.bottom + 13 * density, 7f, pal.labelMinor)
        // Keep animating while anything moves or pulses.
        if (!paused || sel != null) postInvalidateDelayed(if (paused) 60 else 33)
    }

    private fun routeColor(u: UnitState) =
        when (u.type) {
            "SEK" -> 0xffbd9cff.toInt()
            "MEK" -> 0xff78e0bc.toInt()
            "HELI" -> 0xff8adcf2.toInt()
            else -> 0xff60c8ee.toInt()
        }

    private fun drawRoute(c: Canvas, t: Transform, pts: List<GeoPoint>, cum: DoubleArray, progress: Double, color: Int, approx: Boolean) {
        if (pts.size < 2) return
        val total = cum.last()
        val d = total * progress
        val done = Path()
        val todo = Path()
        var started = false
        for (i in pts.indices) {
            val q = t.point(pts[i].lon, pts[i].lat)
            if (cum[i] <= d) {
                if (i == 0) done.moveTo(q.x, q.y) else done.lineTo(q.x, q.y)
            } else {
                if (!started) {
                    // Start the remaining path exactly at the vehicle.
                    val prev = pts[i - 1]
                    val seg = (cum[i] - cum[i - 1]).takeIf { it > 0 } ?: 1.0
                    val f = ((d - cum[i - 1]) / seg).coerceIn(0.0, 1.0)
                    val v = t.point(prev.lon + (pts[i].lon - prev.lon) * f, prev.lat + (pts[i].lat - prev.lat) * f)
                    todo.moveTo(v.x, v.y)
                    started = true
                }
                todo.lineTo(q.x, q.y)
            }
        }
        paint(0x66000000, 6f, Paint.Style.STROKE)
        c.drawPath(todo, p)
        paint(color, 3f, Paint.Style.STROKE)
        if (approx) p.pathEffect = DashPathEffect(floatArrayOf(6 * density, 5 * density), 0f)
        c.drawPath(todo, p)
        p.pathEffect = null
        paint((color and 0x00ffffff) or 0x40000000, 2f, Paint.Style.STROKE)
        c.drawPath(done, p)
        // Destination flag.
        val end = t.point(pts.last().lon, pts.last().lat)
        paint(color)
        c.drawCircle(end.x, end.y, 3 * density, p)
    }

    private fun drawHeatmap(c: Canvas, t: Transform) {
        for (i in game.state.incidents.filter { it.region == game.state.region && it.status in listOf("OPEN", "DRIVING", "WORKING") }) {
            val q = t.point(i.lon, i.lat)
            paint(0x44ff986b)
            p.shader = RadialGradient(q.x, q.y, 55 * density, intArrayOf(0x66ff586e, 0x00ff586e), null, Shader.TileMode.CLAMP)
            c.drawCircle(q.x, q.y, 55 * density, p)
        }
    }

    /** Simulated 10-minute reach of every free unit (straight-line, clearly an estimate). */
    private fun drawCoverage(c: Canvas, t: Transform, pxKm: Double) {
        for (u in game.state.units.filter { it.region == game.state.region && game.available(it) }) {
            val q = t.point(u.lon, u.lat)
            val r = (u.vehicleSpeed / 6 * pxKm * density / 1.3).toFloat()
            paint(0x1468d9cc)
            c.drawCircle(q.x, q.y, r, p)
            paint(0x3368d9cc, 1f, Paint.Style.STROKE)
            c.drawCircle(q.x, q.y, r, p)
        }
    }

    private class Station(val lon: Double, val lat: Double, val units: List<UnitState>)

    private fun drawStation(c: Canvas, x: Float, y: Float, s: Station) {
        val free = s.units.count { game.available(it) }
        val r = 10 * density
        val shield = Path().apply {
            moveTo(x, y - r)
            lineTo(x + r * .9f, y - r * .55f)
            lineTo(x + r * .75f, y + r * .45f)
            lineTo(x, y + r)
            lineTo(x - r * .75f, y + r * .45f)
            lineTo(x - r * .9f, y - r * .55f)
            close()
        }
        paint(0xff0f2433.toInt())
        c.drawPath(shield, p)
        paint(if (free > 0) 0xff6ee7b7.toInt() else 0xff8aa0ad.toInt(), 1.6f, Paint.Style.STROKE)
        c.drawPath(shield, p)
        text(c, "$free/${s.units.size}", x, y + 3.5f * density, 8f, 0xffe4f2f7.toInt(), true, true)
    }

    private fun typeGlyph(type: String) =
        when (type) {
            "STREIFE" -> "FuStW"
            "BEREITSCHAFT" -> "BePo"
            "VERKEHR" -> "VPI"
            "HUND" -> "DHF"
            "KRIPO" -> "K"
            "RETTUNG" -> "RTW"
            "FEUERWEHR" -> "HLF"
            else -> type
        }

    private fun statusColor(u: UnitState) =
        when {
            game.available(u) -> 0xff6ee7b7.toInt()
            u.status == "DRIVING" -> 0xffffc66d.toInt()
            u.status in listOf("WORKING", "ON_SCENE") -> 0xffff7a8c.toInt()
            u.status == "RETURNING" -> 0xff7dbbff.toInt()
            u.status == "TRAINING" -> 0xffbca2f5.toInt()
            else -> 0xff8aa0ad.toInt()
        }

    private fun drawUnit(c: Canvas, x: Float, y: Float, u: UnitState, heading: Float, detailed: Boolean, pal: MapPalette) {
        val moving = u.status in listOf("DRIVING", "RETURNING")
        val highlighted = u.id in previewUnits
        if (highlighted) {
            paint(0x66f5e663, 0f)
            c.drawCircle(x, y, 22 * density, p)
        }
        if (!detailed && !moving && !highlighted) {
            // Compact status chip for the state overview.
            val w = 15 * density
            paint(0xff0d1d29.toInt())
            c.drawRoundRect(RectF(x - w, y - 7 * density, x + w, y + 7 * density), 4 * density, 4 * density, p)
            paint(statusColor(u), 1.4f, Paint.Style.STROKE)
            c.drawRoundRect(RectF(x - w, y - 7 * density, x + w, y + 7 * density), 4 * density, 4 * density, p)
            text(c, typeGlyph(u.type).take(5), x, y + 3 * density, 7.5f, routeColor(u), true, true)
            return
        }
        if (u.type == "HELI") drawHelicopter(c, x, y, heading, moving) else drawVehicle(c, x, y, u, heading, moving)
        paint(statusColor(u))
        c.drawCircle(x + 11 * density, y - 11 * density, 3.6f * density, p)
        paint(0xff081520.toInt(), 1f, Paint.Style.STROKE)
        c.drawCircle(x + 11 * density, y - 11 * density, 3.6f * density, p)
        if (moving) pill(c, "${u.travelLeft + u.readinessLeft}′", x, y - 21 * density, 8f, 0xffffd58b.toInt(), 0xe00b1a26.toInt())
        if (detailed || highlighted) pill(c, u.callsign.take(14), x, y + 27 * density, 7.5f, routeColor(u), 0xe00b1a26.toInt())
    }

    private fun drawVehicle(c: Canvas, x: Float, y: Float, u: UnitState, heading: Float, moving: Boolean) {
        val tint = routeColor(u)
        val armored = u.type == "SEK"
        val van = armored || u.type in listOf("BEREITSCHAFT", "FEUERWEHR", "RETTUNG", "HUND")
        val civil = u.type in listOf("MEK", "KRIPO")
        val s = .62f * density
        c.save()
        c.translate(x, y)
        c.rotate(if (moving) heading else 0f)
        c.scale(s, s)
        val halfW = if (van) 10f else 8f
        val halfH = if (van) 18f else 16f
        paint(0x88020a10.toInt(), 0f)
        p.strokeWidth = 0f
        c.drawOval(RectF(-halfW - 3, -halfH - 3, halfW + 3, halfH + 4), p)
        p.color =
            when {
                armored -> 0xff333a49.toInt()
                civil -> 0xff8a949c.toInt()
                u.type == "RETTUNG" -> 0xfff7f3e6.toInt()
                u.type == "FEUERWEHR" -> 0xffd8463c.toInt()
                else -> 0xffe6edf0.toInt()
            }
        c.drawRoundRect(RectF(-halfW, -halfH, halfW, halfH), 4f, 4f, p)
        p.color = if (armored) 0xff788391.toInt() else 0xff17354a.toInt()
        c.drawRoundRect(RectF(-halfW + 2, -9f, halfW - 2, 8f), 2f, 2f, p)
        if (!civil) {
            p.color = if (u.type == "FEUERWEHR") 0xfff4f4f4.toInt() else tint
            c.drawRect(-halfW, -3f, halfW, 4f, p)
        }
        // Blue lights flash while on an emergency drive.
        if (!civil && u.status == "DRIVING") {
            val on = (SystemClock.uptimeMillis() / 220) % 2 == 0L
            p.color = if (on) 0xff4fc3ff.toInt() else 0xff1d4f7a.toInt()
            c.drawRoundRect(RectF(-6f, -6f, 0f, -2f), 1f, 1f, p)
            p.color = if (!on) 0xff4fc3ff.toInt() else 0xff1d4f7a.toInt()
            c.drawRoundRect(RectF(0f, -6f, 6f, -2f), 1f, 1f, p)
        }
        p.color = 0xfff8f5ce.toInt()
        c.drawRect(-halfW + 1, -halfH + 1, -halfW + 4, -halfH + 3, p)
        c.drawRect(halfW - 4, -halfH + 1, halfW - 1, -halfH + 3, p)
        c.restore()
    }

    private fun drawHelicopter(c: Canvas, x: Float, y: Float, heading: Float, moving: Boolean) {
        c.save()
        c.translate(x, y)
        c.rotate(if (moving) heading else 0f)
        val d = density * .8f
        paint(0x88020a10.toInt())
        c.drawCircle(0f, 2 * d, 15 * d, p)
        paint(0xffdce8ec.toInt())
        c.drawOval(RectF(-6 * d, -12 * d, 6 * d, 9 * d), p)
        paint(0xff224759.toInt())
        c.drawOval(RectF(-4 * d, -10 * d, 4 * d, -3 * d), p)
        paint(0xff9ebdce.toInt(), 2.4f, Paint.Style.STROKE)
        c.drawLine(0f, 8 * d, 0f, 22 * d, p)
        c.drawLine(-5 * d, 21 * d, 5 * d, 21 * d, p)
        val spin = if (moving) (SystemClock.uptimeMillis() % 360).toFloat() * 2 else 20f
        c.rotate(spin)
        paint(0xcc76cde7.toInt(), 2f, Paint.Style.STROKE)
        c.drawLine(-19 * d, 0f, 19 * d, 0f, p)
        c.drawLine(0f, -19 * d, 0f, 19 * d, p)
        c.restore()
    }

    private fun callColor(i: IncidentState) =
        when (i.priority) {
            1 -> 0xffff4d63.toInt()
            2 -> 0xffffa940.toInt()
            else -> 0xfff5e05a.toInt()
        }

    private fun callGlyph(i: IncidentState): String {
        val t = i.title.lowercase()
        return when {
            i.requiredTypes.contains("SEK") -> "!"
            i.requiredTypes.contains("FEUERWEHR") || t.contains("brand") -> "F"
            i.requiredTypes.contains("RETTUNG") -> "+"
            t.contains("unfall") || t.contains("verkehr") -> "V"
            t.contains("einbruch") || t.contains("diebstahl") || t.contains("raub") -> "E"
            t.contains("vermisst") || t.contains("such") -> "?"
            i.requiredTypes.contains("MEK") -> "O"
            i.requiredTypes.contains("KRIPO") -> "K"
            else -> "P"
        }
    }

    private fun drawCall(c: Canvas, x: Float, y: Float, i: IncidentState, now: Long, paused: Boolean) {
        val tint = if (i.status == "HANDED") 0xff8aa0ad.toInt() else callColor(i)
        val open = i.status == "OPEN"
        if (open && !paused) {
            val f = (now % 1400) / 1400f
            paint((tint and 0x00ffffff) or (((1 - f) * 120).toInt() shl 24))
            c.drawCircle(x, y, (16 + 14 * f) * density, p)
        }
        val r = 13 * density
        paint(0xff0d1a24.toInt())
        c.drawCircle(x, y, r + 3 * density, p)
        // Deadline ring: remaining time until the call fails.
        if (i.status in listOf("OPEN", "DRIVING")) {
            val left = (1f - i.age.toFloat() / i.deadline.coerceAtLeast(1)).coerceIn(0f, 1f)
            paint(0x44ffffff, 3f, Paint.Style.STROKE)
            c.drawCircle(x, y, r + 1 * density, p)
            paint(if (left < .25f) 0xffff4d63.toInt() else 0xffe9f4f8.toInt(), 3f, Paint.Style.STROKE)
            c.drawArc(RectF(x - r - density, y - r - density, x + r + density, y + r + density), -90f, 360f * left, false, p)
        }
        paint(tint)
        c.drawCircle(x, y, r - 1.5f * density, p)
        text(c, callGlyph(i), x, y + 4.5f * density, 13f, 0xff10161c.toInt(), true, true)
        val tag =
            when (i.status) {
                "OPEN" -> "P${i.priority}"
                "DRIVING" -> "Anfahrt"
                "WORKING" -> "vor Ort"
                else -> "Übergabe"
            }
        pill(c, tag, x, y + 24 * density, 7.5f, tint, 0xe00b1a26.toInt())
    }

    private fun drawCallCluster(c: Canvas, cluster: MapClustering.Cluster<Any>) {
        val calls = cluster.members.mapNotNull { it.value as? IncidentState }
        val top = calls.minByOrNull { it.priority } ?: return
        val tint = callColor(top)
        paint(0xff0d1a24.toInt())
        c.drawCircle(cluster.x, cluster.y, 18 * density, p)
        paint(tint, 3f, Paint.Style.STROKE)
        c.drawCircle(cluster.x, cluster.y, 16 * density, p)
        text(c, calls.size.toString(), cluster.x, cluster.y + 5 * density, 14f, tint, true, true)
        pill(c, "P${top.priority} · Lagen", cluster.x, cluster.y + 29 * density, 7.5f, tint, 0xe00b1a26.toInt())
    }

    private fun drawCluster(c: Canvas, cluster: MapClustering.Cluster<Any>) {
        val units = cluster.members.flatMap {
            when (val v = it.value) {
                is UnitState -> listOf(v)
                is Station -> v.units
                else -> emptyList()
            }
        }.distinctBy { it.id }
        val free = units.count { game.available(it) }
        paint(0xff102b3b.toInt())
        c.drawRoundRect(RectF(cluster.x - 19 * density, cluster.y - 13 * density, cluster.x + 19 * density, cluster.y + 13 * density), 7 * density, 7 * density, p)
        paint(if (free > 0) 0xff6ee7b7.toInt() else 0xffffc66d.toInt(), 1.6f, Paint.Style.STROKE)
        c.drawRoundRect(RectF(cluster.x - 19 * density, cluster.y - 13 * density, cluster.x + 19 * density, cluster.y + 13 * density), 7 * density, 7 * density, p)
        text(c, "$free/${units.size}", cluster.x, cluster.y + 4 * density, 11f, 0xffe4f2f7.toInt(), true, true)
    }

    private fun drawMinimap(c: Canvas, t: Transform) {
        val w = 84 * density
        val h = 64 * density
        val x = width - w - 10 * density
        val y = t.top + 8 * density
        if (t.zoom < 1.6f) return
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
        val boundary = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            for (ring in t.region.rings) {
                ring.forEachIndexed { i, v -> val a = mini.point(v.lon, v.lat); if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y) }
                close()
            }
        }
        paint(0xff34556a.toInt())
        c.drawPath(boundary, p)
        for (i in game.state.incidents) if (i.region == game.state.region && i.status in listOf("OPEN", "DRIVING", "WORKING")) {
            val q = mini.point(i.lon, i.lat)
            paint(callColor(i))
            c.drawCircle(q.x, q.y, 1.6f * density, p)
        }
        fun screenWorld(px: Double, py: Double) = PointF((w / 2 + (px - mini.centerX) * scale).toFloat(), (h / 2 + (py - mini.centerY) * scale).toFloat())
        val a = screenWorld(t.centerX + (-t.w / 2 - t.panX) / (t.fitted * t.zoom), t.centerY + (-(t.h - t.top - t.bottom) / 2 - t.panY) / (t.fitted * t.zoom))
        val z = screenWorld(t.centerX + (t.w / 2 - t.panX) / (t.fitted * t.zoom), t.centerY + ((t.h - t.top - t.bottom) / 2 - t.panY) / (t.fitted * t.zoom))
        c.clipRect(3 * density, 3 * density, w - 3 * density, h - 3 * density)
        paint(0xffd1f7ed.toInt(), 1f, Paint.Style.STROKE)
        c.drawRect(RectF(a.x, a.y, z.x, z.y), p)
        c.restore()
    }

    private fun drawLegend(c: Canvas, t: Transform) {
        val rows =
            listOf(
                Triple(0xffff4d63.toInt(), "●", "P1 · akute Gefahr"),
                Triple(0xffffa940.toInt(), "●", "P2 · dringend"),
                Triple(0xfff5e05a.toInt(), "●", "P3 · normal"),
                Triple(0xffe9f4f8.toInt(), "◔", "Ring: Restzeit bis Fristablauf"),
                Triple(0xff6ee7b7.toInt(), "■", "Einheit frei"),
                Triple(0xffffc66d.toInt(), "■", "Anfahrt (Blaulicht)"),
                Triple(0xffff7a8c.toInt(), "■", "am Einsatzort"),
                Triple(0xff7dbbff.toInt(), "■", "Rückfahrt · erneut alarmierbar"),
                Triple(0xffbca2f5.toInt(), "■", "Lehrgang / Ruhe"),
                Triple(0xff6ee7b7.toInt(), "⬟", "Spielwache · frei/gesamt"),
                Triple(0xffd99a3c.toInt(), "━", "Autobahn"),
                Triple(0xff7d93a6.toInt(), "━", "Bundes-/Hauptstraße"),
                Triple(0xfff5e663.toInt(), "┅", "Routenvorschau Disposition"),
            )
        val w = 214 * density
        val h = (rows.size * 17 + 26) * density
        val x = 10 * density
        val y = t.top + 8 * density
        paint(0xee0a1a26.toInt())
        c.drawRoundRect(RectF(x, y, x + w, y + h), 8 * density, 8 * density, p)
        text(c, "LEGENDE", x + 10 * density, y + 16 * density, 9f, 0xff9fd4e6.toInt(), true)
        rows.forEachIndexed { i, (color, sym, label) ->
            val ry = y + (34 + i * 17) * density
            text(c, sym, x + 12 * density, ry, 12f, color, true)
            text(c, label, x + 32 * density, ry, 10f, 0xffd6ebf2.toInt())
        }
    }

    // --- Interaction ----------------------------------------------------------------------------

    private fun describe(it: Any): String =
        when (it) {
            is UnitState ->
                "${it.callsign} · ${it.type} · ${it.status}" +
                    (if (it.status in listOf("DRIVING", "RETURNING")) " · ${it.travelLeft + it.readinessLeft} Min" else "")
            is IncidentState -> "P${it.priority} · ${it.title} · ${it.location}"
            is Station -> "Spielwache · ${it.units.size} Einheiten"
            else -> "Objekt"
        }

    private fun choose(cluster: MapClustering.Cluster<Any>) {
        val members =
            cluster.members
                .flatMap {
                    when (val v = it.value) {
                        is Station -> if (cluster.members.size == 1) listOf<Any>(v) else v.units
                        else -> listOf(v)
                    }
                }
                .distinct()
                .sortedBy { if (it is IncidentState) it.priority else 10 }
        if (members.size == 1) {
            select(members[0])
            return
        }
        AlertDialog.Builder(context)
            .setTitle("${members.size} Objekte an diesem Ort")
            .setItems(members.map { describe(it) }.toTypedArray()) { _, index -> select(members[index]) }
            .setNeutralButton("Heranzoomen") { _, _ -> zoomAround(3f, cluster.x, cluster.y) }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun select(value: Any) {
        when (value) {
            is UnitState -> onUnit(value)
            is IncidentState -> onCall(value)
            is Station ->
                AlertDialog.Builder(context)
                    .setTitle("Spielwache · fiktiv")
                    .setItems(value.units.map { describe(it) }.toTypedArray()) { _, i -> onUnit(value.units[i]) }
                    .setPositiveButton("Heranzoomen") { _, _ -> focus(value.lon, value.lat) }
                    .setNegativeButton("Schließen", null)
                    .show()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        taps.onTouchEvent(e)
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
            MotionEvent.ACTION_POINTER_DOWN -> moved = true
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1 && !detector.isInProgress) {
                    if (hypot(e.x - downX, e.y - downY) > 8 * density) moved = true
                    if (moved) {
                        panX += e.x - lastX
                        panY += e.y - lastY
                        invalidate()
                    }
                }
                lastX = e.x
                lastY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && !detector.isInProgress) {
                    val hit = screenHits.minByOrNull { hypot(it.x - e.x, it.y - e.y) }
                    if (hit != null && hypot(hit.x - e.x, hit.y - e.y) <= 30 * density) choose(hit)
                    else {
                        onEmptyTap?.invoke()
                        performClick()
                    }
                }
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
