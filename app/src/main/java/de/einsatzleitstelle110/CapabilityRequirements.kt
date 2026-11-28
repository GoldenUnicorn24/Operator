package de.einsatzleitstelle110

/** Game equipment for additional roles declared by custom units. */
internal object CapabilityRequirements {
    data class Role(val course: String, val equipment: String)

    val specialistCourses = mapOf("SEK" to "SEK-Fortbildung", "MEK" to "MEK-Observation")

    val extra = mapOf(
        "STREIFE" to Role("Deeskalation", "Funkgerät"),
        "SEK" to Role(specialistCourses.getValue("SEK"), "Schutzweste"),
        "MEK" to Role(specialistCourses.getValue("MEK"), "Fahrzeugkamera"),
        "KRIPO" to Role("Fahndung", "Funkgerät"),
        "BEREITSCHAFT" to Role("Einsatzführung", "Schutzhelm"),
        "VERKEHR" to Role("Verkehrsdienst", "Fahrzeugkamera"),
    )
}
