package de.einsatzleitstelle110

/** Deterministic game multipliers sampled at departure, without live traffic or weather APIs. */
object SimulationConditions {
    val weatherOptions = linkedMapOf("CLEAR" to "Trocken", "RAIN" to "Regen", "ICE" to "Glätte")
    fun trafficFactor(hour: Int): Double = when (hour.mod(24)) {
        in 7..9, in 16..18 -> 1.25
        in 0..5, 22, 23 -> .85
        else -> 1.0
    }
    fun weatherFactor(weather: String): Double = when (weather) {
        "RAIN" -> 1.15
        "ICE" -> 1.45
        else -> 1.0
    }
    fun travelFactor(hour: Int, weather: String) = trafficFactor(hour) * weatherFactor(weather)
    fun accidentWeight(weather: String): Double = when (weather) { "RAIN" -> 1.5; "ICE" -> 2.2; else -> 1.0 }
}
