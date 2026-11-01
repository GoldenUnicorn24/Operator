package de.einsatzleitstelle110

import android.content.Context
import kotlin.math.*
import org.json.JSONArray
import org.json.JSONObject

data class GeoPoint(val lon: Double, val lat: Double)

data class GeoCity(val name: String, val lon: Double, val lat: Double)

data class GeoRegion(
    val code: String,
    val name: String,
    val rings: List<List<GeoPoint>>,
    val cities: List<GeoCity>,
) {
    val bounds: DoubleArray by lazy {
        val p = rings.flatten()
        doubleArrayOf(
            p.minOf { it.lon },
            p.minOf { it.lat },
            p.maxOf { it.lon },
            p.maxOf { it.lat },
        )
    }

    fun contains(p: GeoPoint): Boolean {
        // Multipolygons and holes: even-odd winding.
        var inside = false
        for (ring in rings) {
            var hit = false
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + ring.size - 1) % ring.size]
                if (
                    (a.lat > p.lat) != (b.lat > p.lat) &&
                        p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
                )
                    hit = !hit
            }
            if (hit) inside = !inside
        }
        return inside
    }
}

object GeoData {
    private val locations =
        mapOf(
            "DE-BW" to
                "Stuttgart,9.1829,48.7758;Mannheim,8.4660,49.4875;Karlsruhe,8.4037,49.0069;Freiburg,7.8522,47.9990;Heilbronn,9.2194,49.1427;Ulm,9.9876,48.4011;Konstanz,9.1751,47.6633",
            "DE-BY" to
                "München,11.5820,48.1351;Nürnberg,11.0775,49.4521;Augsburg,10.8978,48.3705;Regensburg,12.1016,49.0134;Würzburg,9.9366,49.7913;Passau,13.4319,48.5667;Ingolstadt,11.4268,48.7665;Bayreuth,11.5789,49.9456;Rosenheim,12.1219,47.8564;Kempten,10.3139,47.7267",
            "DE-BE" to
                "Mitte,13.4050,52.5200;Spandau,13.2005,52.5355;Köpenick,13.5750,52.4460;Neukölln,13.4311,52.4810;Pankow,13.4074,52.5690",
            "DE-BB" to
                "Potsdam,13.0645,52.3906;Cottbus,14.3340,51.7563;Frankfurt (Oder),14.5506,52.3471;Brandenburg (Havel),12.5580,52.4125;Neuruppin,12.8070,52.9290;Prenzlau,13.8620,53.3170;Senftenberg,14.0040,51.5240",
            "DE-HB" to "Bremen,8.8017,53.0793;Bremerhaven,8.5809,53.5396;Vegesack,8.6233,53.1670",
            "DE-HH" to
                "Hamburg Mitte,10.0000,53.5500;Altona,9.9357,53.5493;Wandsbek,10.0660,53.5731;Harburg,9.9867,53.4606;Bergedorf,10.2072,53.4884;Barmbek,10.0410,53.5800;Billstedt,10.1050,53.5440;Langenhorn,10.0120,53.6460",
            "DE-HE" to
                "Frankfurt,8.6821,50.1109;Wiesbaden,8.2398,50.0782;Darmstadt,8.6512,49.8728;Kassel,9.4797,51.3127;Gießen,8.6756,50.5841;Fulda,9.6750,50.5510;Marburg,8.7708,50.8022",
            "DE-MV" to
                "Rostock,12.0991,54.0924;Schwerin,11.4148,53.6355;Stralsund,13.0864,54.3091;Neubrandenburg,13.2618,53.5560;Greifswald,13.3923,54.0958;Wismar,11.4600,53.8931;Waren,12.6850,53.5170",
            "DE-NI" to
                "Hannover,9.7320,52.3759;Braunschweig,10.5268,52.2689;Osnabrück,8.0472,52.2799;Oldenburg,8.2146,53.1435;Lüneburg,10.4143,53.2460;Göttingen,9.9158,51.5413;Celle,10.0805,52.6226;Wolfsburg,10.7865,52.4227;Hildesheim,9.9511,52.1548;Wilhelmshaven,8.1406,53.5290;Emden,7.2060,53.3670;Cuxhaven,8.6940,53.8590",
            "DE-NW" to
                "Köln,6.9603,50.9375;Düsseldorf,6.7735,51.2277;Dortmund,7.4653,51.5136;Essen,7.0123,51.4556;Duisburg,6.7623,51.4344;Bonn,7.0996,50.7374;Münster,7.6257,51.9607;Bielefeld,8.5325,52.0302;Aachen,6.0839,50.7753;Paderborn,8.7521,51.7189;Siegen,8.0200,50.8740",
            "DE-RP" to
                "Mainz,8.2473,49.9929;Koblenz,7.5889,50.3569;Trier,6.6371,49.7499;Kaiserslautern,7.7689,49.4401;Ludwigshafen,8.4353,49.4811;Worms,8.3597,49.6341",
            "DE-SL" to
                "Saarbrücken,7.0000,49.2402;Neunkirchen,7.1771,49.3461;Saarlouis,6.7500,49.3150;Homburg,7.3339,49.3264",
            "DE-SN" to
                "Dresden,13.7373,51.0504;Leipzig,12.3731,51.3397;Chemnitz,12.9253,50.8278;Zwickau,12.4961,50.7189;Görlitz,14.9885,51.1526;Bautzen,14.4330,51.1810",
            "DE-ST" to
                "Magdeburg,11.6276,52.1205;Halle,11.9705,51.4828;Dessau,12.2456,51.8318;Wittenberg,12.6484,51.8661;Stendal,11.8550,52.6050;Halberstadt,11.0540,51.8960",
            "DE-SH" to
                "Kiel,10.1228,54.3233;Lübeck,10.6866,53.8655;Flensburg,9.4469,54.7931;Neumünster,9.9814,54.0710;Husum,9.0522,54.4854;Itzehoe,9.5177,53.9230;Rendsburg,9.6620,54.3040",
            "DE-TH" to
                "Erfurt,11.0299,50.9848;Jena,11.5892,50.9271;Gera,12.0833,50.8803;Weimar,11.3290,50.9795;Gotha,10.7080,50.9490;Eisenach,10.3150,50.9780;Nordhausen,10.7910,51.5050",
        )
    val all: List<GeoRegion> by lazy { regions ?: emptyList() }
    private var regions: List<GeoRegion>? = null

    fun load(context: Context): List<GeoRegion> {
        OperationsAreas.load(context)
        if (regions != null) return regions!!
        val root =
            JSONObject(context.assets.open("states.geojson").bufferedReader().use { it.readText() })
        val features = root.getJSONArray("features")
        val parsed = mutableListOf<GeoRegion>()
        for (fi in 0 until features.length()) {
            val feature = features.getJSONObject(fi)
            val prop = feature.getJSONObject("properties")
            val code = prop.optString("code", prop.optString("id"))
            val name = prop.getString("name")
            val geom = feature.getJSONObject("geometry")
            val result = mutableListOf<List<GeoPoint>>()
            fun readRing(ring: JSONArray) {
                val pts = ArrayList<GeoPoint>(ring.length())
                for (i in 0 until ring.length()) {
                    val arr = ring.getJSONArray(i)
                    pts.add(GeoPoint(arr.getDouble(0), arr.getDouble(1)))
                }
                if (pts.size >= 3) result.add(pts)
            }
            fun readPolygon(poly: JSONArray) {
                for (ri in 0 until poly.length()) readRing(poly.getJSONArray(ri))
            }
            when (geom.getString("type")) {
                "Polygon" -> readPolygon(geom.getJSONArray("coordinates"))
                "MultiPolygon" -> {
                    val polys = geom.getJSONArray("coordinates")
                    for (i in 0 until polys.length()) readPolygon(polys.getJSONArray(i))
                }
            }
            val cities =
                locations[code]
                    .orEmpty()
                    .split(';')
                    .filter { it.isNotBlank() }
                    .mapNotNull { item ->
                        val t = item.split(',')
                        if (t.size != 3) null else GeoCity(t[0], t[1].toDouble(), t[2].toDouble())
                    }
            parsed.add(GeoRegion(code, name, result, cities))
        }
        regions = parsed.sortedBy { it.name }
        return regions!!
    }

    fun find(code: String) = all.first { it.code == code }

    fun km(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val s =
            sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2.0)
        return 12742.0 * asin(min(1.0, sqrt(s)))
    }

    fun fromCity(c: GeoCity, lon: Double = 0.0, lat: Double = 0.0) =
        GeoPoint(c.lon + lon, c.lat + lat)
}
