package de.einsatzleitstelle110

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.math.*

/** Colours of the tactical map. Night is the default operations-room look. */
class MapPalette(val night: Boolean) {
    val sea = if (night) 0xff0a1622.toInt() else 0xffc9d8e2.toInt()
    val neighbour = if (night) 0xff111f2b.toInt() else 0xffdfe5e3.toInt()
    val land = if (night) 0xff16283a.toInt() else 0xfff1f2ec.toInt()
    val urban = if (night) 0xff1d3247.toInt() else 0xffe6e1d6.toInt()
    val water = if (night) 0xff0d2f45.toInt() else 0xffa9cde3.toInt()
    val river = if (night) 0xff1d5372.toInt() else 0xff8fbfdc.toInt()
    val motorwayCase = if (night) 0xff3a2a12.toInt() else 0xffb57a2a.toInt()
    val motorway = if (night) 0xffd99a3c.toInt() else 0xfff3b75a.toInt()
    val primaryCase = if (night) 0xff1b2c3b.toInt() else 0xffb8b2a2.toInt()
    val primary = if (night) 0xff7d93a6.toInt() else 0xfffdf6dc.toInt()
    val minor = if (night) 0xff3c5367.toInt() else 0xffffffff.toInt()
    val farRoad = if (night) 0xff2a3a49.toInt() else 0xffcfcabd.toInt()
    val border = if (night) 0xff2c4357.toInt() else 0xffa5b2b8.toInt()
    val stateBorder = if (night) 0xff5fb6d6.toInt() else 0xff3b7f9a.toInt()
    val district = if (night) 0x5579c7dd else 0x553b7f9a
    val label = if (night) 0xffd9e7ef.toInt() else 0xff26353f.toInt()
    val labelMinor = if (night) 0xff9fb6c4.toInt() else 0xff52626c.toInt()
    val halo = if (night) 0xe00a1622.toInt() else 0xddf5f5f0.toInt()
}

/**
 * Camera as an affine map from Web-Mercator world units (0..1) to screen pixels:
 * screen = a * world + (bx, by). Two cameras differ by a scale and a translation only, so a
 * cached base-map bitmap can be re-used during gestures.
 */
data class Camera(val a: Double, val bx: Double, val by: Double, val w: Int, val h: Int) {
    fun x(lon: Double) = (a * TacticalMapView.Transform.worldX(lon) + bx).toFloat()

    fun y(lat: Double) = (a * TacticalMapView.Transform.worldY(lat) + by).toFloat()

    /** Screen pixels per kilometre at a latitude. */
    fun pxPerKm(lat: Double) = a / (40075.0 * cos(Math.toRadians(lat)))

    fun same(o: Camera?) =
        o != null && w == o.w && h == o.h && abs(a / o.a - 1) < 1e-6 && abs(bx - o.bx) < .5 && abs(by - o.by) < .5
}

/**
 * Renders the static tactical base map (water, settlements, road hierarchy, borders, labels) on a
 * background thread into a bitmap. Geometry is projected once per state into local float paths.
 */
class MapRenderer(private val context: Context, private val density: Float, private val onReady: () -> Unit) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "map-renderer").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())

    class Frame(val bitmap: Bitmap, val camera: Camera, val key: String)

    @Volatile var frame: Frame? = null
        private set

    @Volatile private var pending: Request? = null
    @Volatile private var busy = false
    @Volatile private var closed = false

    data class Request(
        val camera: Camera,
        val region: GeoRegion,
        val all: List<GeoRegion>,
        val scope: String,
        val night: Boolean,
        val key: String,
    )

    /** Pre-projected geometry of one state view (local coordinates relative to an origin). */
    private class Layers(
        val code: String,
        val ox: Double,
        val oy: Double,
        val k: Double,
        val states: List<Pair<String, Path>>,
        val urban: Path,
        val lakes: Path,
        val rivers: List<Pair<Float, Path>>,
        val farRoads: Path,
        val roads: Array<Path>,
        val detailTiles: List<DetailTile>,
        val areas: Array<List<Path>>,
        val namedRoads: List<RoadLine>,
        val districts: List<Path>,
        val places: List<MapPlace>,
        val hasRoads: Boolean,
    )

    private class DetailTile(val bounds: RectF, val roads: Array<Path>)

    @Volatile private var layers: Layers? = null

    fun request(r: Request) {
        if (closed) return
        if (frame?.key == r.key) return
        pending = r
        schedule()
    }

    private fun schedule() {
        if (busy || closed) return
        val r = pending ?: return
        pending = null
        busy = true
        worker.execute {
            try {
                if (!closed) render(r)
            } catch (_: Throwable) {
                // A failed base frame must never crash the operations room; overlay still draws.
            } finally {
                main.post {
                    busy = false
                    if (!closed) {
                        onReady()
                        schedule()
                    }
                }
            }
        }
    }

    fun close() {
        closed = true
        worker.shutdownNow()
    }

    /** Warm-up: parse and project the state's geometry before the first frame is needed. */
    fun prepare(region: GeoRegion, all: List<GeoRegion>) {
        worker.execute {
            try {
                layersFor(region, all)
                NativeRouting.graph(region.code)
            } catch (_: Throwable) {}
        }
    }

    private fun layersFor(region: GeoRegion, all: List<GeoRegion>): Layers {
        layers?.let { if (it.code == region.code) return it }
        val b = region.bounds
        val ox = TacticalMapView.Transform.worldX((b[0] + b[2]) / 2)
        val oy = TacticalMapView.Transform.worldY((b[1] + b[3]) / 2)
        val k = 1e6
        fun lx(lon: Double) = ((TacticalMapView.Transform.worldX(lon) - ox) * k).toFloat()
        fun ly(lat: Double) = ((TacticalMapView.Transform.worldY(lat) - oy) * k).toFloat()
        fun ring(path: Path, pts: List<GeoPoint>) {
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(lx(p.lon), ly(p.lat)) else path.lineTo(lx(p.lon), ly(p.lat)) }
            path.close()
        }
        fun flatLine(path: Path, f: DoubleArray, closed: Boolean = false) {
            var i = 0
            while (i + 1 < f.size) {
                if (i == 0) path.moveTo(lx(f[0]), ly(f[1])) else path.lineTo(lx(f[i]), ly(f[i + 1]))
                i += 2
            }
            if (closed) path.close()
        }
        val margin = 1.2
        fun near(f: DoubleArray): Boolean {
            var i = 0
            while (i + 1 < f.size) {
                if (f[i] in b[0] - margin..b[2] + margin && f[i + 1] in b[1] - margin..b[3] + margin) return true
                i += 2
            }
            return false
        }
        val states = all.map { s -> s.code to Path().apply { fillType = Path.FillType.EVEN_ODD; s.rings.forEach { ring(this, it) } } }
        val base = MapData.base(context)
        val urban = Path().apply { fillType = Path.FillType.EVEN_ODD }
        for (poly in base.urban) if (near(poly[0])) poly.forEach { flatLine(urban, it, true) }
        val lakes = Path().apply { fillType = Path.FillType.EVEN_ODD }
        for (poly in base.lakes) if (near(poly[0])) poly.forEach { flatLine(lakes, it, true) }
        val rivers = base.rivers.filter { near(it.second) }.map { (w, f) -> w to Path().apply { flatLine(this, f) } }
        val farRoads = Path()
        for (r in base.roads) if (r.cls == 0 && near(r.points)) flatLine(farRoads, r.points)
        val stateMap = MapData.state(context, region.code)
        val roads = Array(3) { Path() }
        // Kilometer-sized detail tiles; only intersecting tiles are stroked at street zoom.
        val buckets = stateMap?.roads.orEmpty().filter { it.cls in 3..5 }.groupBy {
            floor(it.lon(0) * 100).toInt() to floor(it.lat(0) * 100).toInt()
        }
        val detailTiles = buckets.values.map { lines ->
            val paths = Array(3) { Path() }
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (line in lines) {
                flatLine(paths[line.cls - 3], line.points)
                for (i in 0 until line.size) {
                    val x = lx(line.lon(i)); val y = ly(line.lat(i))
                    minX = min(minX, x); minY = min(minY, y)
                    maxX = max(maxX, x); maxY = max(maxY, y)
                }
            }
            DetailTile(RectF(minX - .01f, minY - .01f, maxX + .01f, maxY + .01f), paths)
        }
        val areas = Array(3) { kind -> stateMap?.areas.orEmpty().filter { it.kind == kind }.map { area ->
            Path().apply { fillType = Path.FillType.EVEN_ODD; for (ring in area.rings) flatLine(this, ring, true) }
        } }
        stateMap?.roads?.forEach { r -> if (r.cls in 0..2) flatLine(roads[r.cls], r.points) }
        val districts =
            OperationsAreas.districts(region.code).map { (_, rings) -> Path().apply { rings.forEach { ring(this, it) } } }
        val places =
            (stateMap?.places?.filter { region.contains(GeoPoint(it.lon, it.lat)) }.orEmpty().ifEmpty {
                region.cities.map { MapPlace(it.name, "city", 80000, it.lon, it.lat) }
            })
                .sortedByDescending { it.population }
        return Layers(region.code, ox, oy, k, states, urban, lakes, rivers, farRoads, roads, detailTiles, areas, stateMap?.roads.orEmpty().filter { it.name.isNotBlank() || it.oneway != 0 }, districts, places, stateMap != null)
            .also { layers = it }
    }

    private fun render(r: Request) {
        val cam = r.camera
        if (cam.w <= 0 || cam.h <= 0) return
        val l = layersFor(r.region, r.all)
        // Always a fresh bitmap: the previous one may still be referenced by a pending display list.
        val bmp = Bitmap.createBitmap(cam.w, cam.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pal = MapPalette(r.night)
        c.drawColor(pal.sea)
        val s = (cam.a / l.k).toFloat()
        val m = Matrix().apply {
            setScale(s, s)
            postTranslate((cam.a * l.ox + cam.bx).toFloat(), (cam.a * l.oy + cam.by).toFloat())
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        fun stroke(color: Int, px: Float) {
            p.style = Paint.Style.STROKE
            p.color = color
            p.strokeWidth = px * density / s
        }
        fun fill(color: Int) {
            p.style = Paint.Style.FILL
            p.color = color
        }
        val midLat = (r.region.bounds[1] + r.region.bounds[3]) / 2
        val pxKm = cam.pxPerKm(midLat) / density
        c.save()
        c.concat(m)
        // Neighbouring states as context, the selected state brighter.
        for ((code, path) in l.states) {
            fill(if (code == r.region.code) pal.land else pal.neighbour)
            c.drawPath(path, p)
        }
        fill(pal.urban)
        c.drawPath(l.urban, p)
        fill(pal.water)
        c.drawPath(l.lakes, p)
        for ((w, path) in l.rivers) {
            stroke(pal.river, (w * .9f * min(2.2, max(.7, pxKm / 2.5)).toFloat()).coerceIn(.6f, 4f))
            c.drawPath(path, p)
        }
        // Context Autobahn network across the border.
        stroke(pal.farRoad, 1.1f)
        c.drawPath(l.farRoads, p)
        // State fill again on top of the context roads, so they only show outside.
        val own = l.states.firstOrNull { it.first == r.region.code }?.second
        if (own != null && l.hasRoads) {
            c.save()
            c.clipPath(own)
            fill(pal.land)
            c.drawPath(own, p)
            fill(pal.urban)
            c.drawPath(l.urban, p)
            fill(pal.water)
            c.drawPath(l.lakes, p)
            for ((w, path) in l.rivers) {
                stroke(pal.river, (w * .9f * min(2.2, max(.7, pxKm / 2.5)).toFloat()).coerceIn(.6f, 4f))
                c.drawPath(path, p)
            }
            c.restore()
        }
        fill(if (pal.night) 0xff203e31.toInt() else 0xffd1e7be.toInt())
        for (path in l.areas[1]) c.drawPath(path, p)
        fill(if (pal.night) 0xff35404a.toInt() else 0xffd8d4d0.toInt())
        for (path in l.areas[2]) c.drawPath(path, p)
        fill(pal.water)
        for (path in l.areas[0]) c.drawPath(path, p)
        if (pxKm > 14) {
            val visible = RectF((-cam.bx / s - l.ox * l.k).toFloat(), (-cam.by / s - l.oy * l.k).toFloat(),
                ((cam.w - cam.bx) / s - l.ox * l.k).toFloat(), ((cam.h - cam.by) / s - l.oy * l.k).toFloat())
            visible.inset(-6 * density / s, -6 * density / s)
            for (tile in l.detailTiles) if (RectF.intersects(visible, tile.bounds)) {
                for (cls in 0..2) {
                    stroke(pal.primaryCase, if (pxKm > 80) 4f else 2.1f)
                    c.drawPath(tile.roads[cls], p)
                    stroke(if (cls == 2 && !pal.night) 0xfffaf3df.toInt() else pal.minor, if (pxKm > 80) 2.6f else 1.2f)
                    c.drawPath(tile.roads[cls], p)
                }
            }
        }
        // Road hierarchy with casings; minor roads appear only when zoomed in.
        val zoomed = pxKm > 1.6
        val street = pxKm > 6
        if (street) {
            stroke(pal.primaryCase, 2.6f)
            c.drawPath(l.roads[2], p)
            stroke(pal.minor, 1.3f)
            c.drawPath(l.roads[2], p)
        } else if (zoomed) {
            stroke(pal.minor and 0x99ffffff.toInt(), .8f)
            c.drawPath(l.roads[2], p)
        }
        val primaryW = if (street) 2.4f else if (zoomed) 1.6f else 1.0f
        stroke(pal.primaryCase, primaryW + 1.4f)
        c.drawPath(l.roads[1], p)
        stroke(pal.primary, primaryW)
        c.drawPath(l.roads[1], p)
        val mwW = if (street) 3.6f else if (zoomed) 2.4f else 1.6f
        stroke(pal.motorwayCase, mwW + 1.6f)
        c.drawPath(l.roads[0], p)
        stroke(pal.motorway, mwW)
        c.drawPath(l.roads[0], p)
        // Borders.
        for ((code, path) in l.states) if (code != r.region.code) {
            stroke(pal.border, .9f)
            c.drawPath(path, p)
        }
        p.pathEffect = DashPathEffect(floatArrayOf(5 * density / s, 4 * density / s), 0f)
        for (d in l.districts) {
            stroke(pal.district, 1.1f)
            c.drawPath(d, p)
        }
        p.pathEffect = null
        if (own != null) {
            stroke((pal.stateBorder and 0x00ffffff) or 0x44000000, 6f)
            c.drawPath(own, p)
            stroke(pal.stateBorder, 1.8f)
            c.drawPath(own, p)
        }
        val scopeOutline = OperationsAreas.outline(r.region.code, r.scope)
        if (scopeOutline.isNotEmpty()) {
            val sp = Path().apply {
                scopeOutline.forEach { ring ->
                    ring.forEachIndexed { i, g ->
                        val x = ((TacticalMapView.Transform.worldX(g.lon) - l.ox) * l.k).toFloat()
                        val y = ((TacticalMapView.Transform.worldY(g.lat) - l.oy) * l.k).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
            }
            stroke(0xff6bdfcb.toInt(), 2.4f)
            c.drawPath(sp, p)
        }
        c.restore()
        drawLabels(c, cam, l, pal, pxKm)
        // Mask last, including labels. Live vehicles/routes remain a separate overlay.
        if (scopeOutline.isNotEmpty()) {
            val scope = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                for (ring in scopeOutline) {
                    ring.forEachIndexed { index, g ->
                        if (index == 0) moveTo(cam.x(g.lon), cam.y(g.lat)) else lineTo(cam.x(g.lon), cam.y(g.lat))
                    }
                    close()
                }
            }
            val outside = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(0f, 0f, cam.w.toFloat(), cam.h.toFloat(), Path.Direction.CW)
                addPath(scope)
            }
            p.style = Paint.Style.FILL
            p.color = 0xff102330.toInt()
            c.drawPath(outside, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f * density
            p.color = 0xff218a84.toInt()
            c.drawPath(scope, p)
        }
        frame = Frame(bmp, cam, r.key)
    }

    private fun drawLabels(c: Canvas, cam: Camera, l: Layers, pal: MapPalette, pxKm: Double) {
        val taken = ArrayList<RectF>()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        val halo = Paint(text).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * density
            color = pal.halo
            strokeJoin = Paint.Join.ROUND
        }
        // Fewer, larger labels when zoomed out; more towns as the user zooms in.
        val minPop =
            when {
                pxKm > 12 -> 0
                pxKm > 5 -> 4000
                pxKm > 2.2 -> 12000
                pxKm > 1.1 -> 30000
                else -> 60000
            }
        var count = 0
        for (pl in l.places) {
            if (pl.population < minPop && count > 6) continue
            if (count > 70) break
            val x = cam.x(pl.lon)
            val y = cam.y(pl.lat)
            if (x < -40 || y < -20 || x > cam.w + 40 || y > cam.h + 20) continue
            val big = pl.population >= 200000
            val size = (if (big) 13f else if (pl.population >= 50000) 11.5f else 10f) * density
            text.textSize = size
            halo.textSize = size
            text.color = if (big) pal.label else pal.labelMinor
            text.isFakeBoldText = big
            val tw = text.measureText(pl.name)
            val rect = RectF(x - tw / 2 - 4, y - size - 6, x + tw / 2 + 4, y + 4)
            if (taken.any { RectF.intersects(it, rect) }) continue
            taken.add(rect)
            count++
            c.drawText(pl.name, x - tw / 2, y - 6 * density, halo)
            c.drawText(pl.name, x - tw / 2, y - 6 * density, text)
            text.style = Paint.Style.FILL
            c.drawCircle(x, y, (if (big) 2.6f else 1.8f) * density, text)
        }
        if (pxKm < 45) return
        val used = HashSet<String>()
        text.textSize = 10f * density
        text.isFakeBoldText = false
        text.color = pal.labelMinor
        halo.textSize = text.textSize
        for (road in l.namedRoads) {
            var best = -1
            var length = 0f
            for (i in 0 until road.size - 1) {
                val x = cam.x(road.lon(i)); val y = cam.y(road.lat(i))
                val x2 = cam.x(road.lon(i + 1)); val y2 = cam.y(road.lat(i + 1))
                if (max(x, x2) < 0 || min(x, x2) > cam.w || max(y, y2) < 0 || min(y, y2) > cam.h) continue
                val d = hypot(x2 - x, y2 - y)
                if (d > length) { length = d; best = i }
            }
            if (best < 0) continue
            val x = cam.x(road.lon(best)); val y = cam.y(road.lat(best))
            val x2 = cam.x(road.lon(best + 1)); val y2 = cam.y(road.lat(best + 1))
            val label = road.name + if (road.oneway != 0 && pxKm > 80) "  →" else ""
            val tw = text.measureText(label)
            if (road.name.isBlank() || road.name in used || length < 18 * density) continue
            val mx = (x + x2) / 2; val my = (y + y2) / 2
            if (mx < tw / 2 || mx > cam.w - tw / 2 || my < 12 * density || my > cam.h - 12 * density) continue
            var angle = Math.toDegrees(atan2((y2 - y).toDouble(), (x2 - x).toDouble())).toFloat()
            val directionFlipped = angle > 90 || angle < -90
            if (directionFlipped) angle += 180
            val rad = Math.toRadians(angle.toDouble())
            val rw = (abs(cos(rad)) * tw + abs(sin(rad)) * 14 * density).toFloat()
            val rh = (abs(sin(rad)) * tw + abs(cos(rad)) * 14 * density).toFloat()
            val rect = RectF(mx - rw / 2 - 3, my - rh / 2 - 3, mx + rw / 2 + 3, my + rh / 2 + 3)
            if (taken.any { RectF.intersects(it, rect) }) continue
            taken.add(rect); used.add(road.name)
            val shown = road.name + if (road.oneway != 0 && pxKm > 80) {
                if ((road.oneway == -1) xor directionFlipped) "  ←" else "  →"
            } else ""
            c.save(); c.rotate(angle, mx, my)
            c.drawText(shown, mx - tw / 2, my - 3 * density, halo)
            c.drawText(shown, mx - tw / 2, my - 3 * density, text)
            c.restore()
        }
    }
}
