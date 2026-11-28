package de.einsatzleitstelle110

internal enum class QuestionTopic(val label: String) {
    LOCATION("Standort"),
    PEOPLE("Betroffene Personen"),
    INJURIES("Verletzungen"),
    EVENT("Art des Ereignisses"),
    DANGER("Gefahren"),
    DEVELOPMENT("Aktuelle Entwicklung"),
    CALLBACK("Rückruf"),
    DETAILS("Zusätzliche Angaben"),
}

/** A common interview guide. Authored answers retain their original IDs, assessment and radio effects. */
internal object CallQuestions {
    private val prompts = mapOf(
        QuestionTopic.LOCATION to Question("general.location", "Gibt es einen erkennbaren Orientierungspunkt?",
            "Von hier kann ich kein eindeutiges Schild erkennen.", key = false),
        QuestionTopic.PEOPLE to Question("general.people", "Sind noch andere Personen in der Nähe?",
            "Das kann ich von hier aus nicht sicher sagen.", key = false),
        QuestionTopic.INJURIES to Question("general.injuries", "Sind noch weitere Personen verletzt?",
            "Ich weiß es nicht. Ich kann nicht alle sehen.", key = false),
        QuestionTopic.EVENT to Question("general.event", "Haben Sie gesehen, wie es angefangen hat?",
            "Den Anfang habe ich nicht mitbekommen.", key = false),
        QuestionTopic.DANGER to Question("general.danger", "Sehen Sie eine weitere Gefahr?",
            "Das kann ich von hier nicht sicher einschätzen.", key = false),
        QuestionTopic.DEVELOPMENT to Question("general.development", "Hat sich die Lage gerade verändert?",
            "Von meinem Standort aus kann ich das nicht beurteilen.", key = false),
        QuestionTopic.CALLBACK to Question("general.callback", "Sind Sie für einen Rückruf erreichbar?",
            "Ja, ich behalte das Telefon bei mir.", key = false),
        QuestionTopic.DETAILS to Question("general.details", "Gibt es noch etwas, das die Kräfte wissen sollten?",
            "Mehr kann ich im Moment nicht sagen.", key = false),
    )

    fun forTopic(script: CallScript, topic: QuestionTopic): List<Question> =
        script.questions.filter { topicOf(it.id) == topic } + prompts.getValue(topic)

    fun answer(script: CallScript, id: String): Question? =
        script.questions.firstOrNull { it.id == id } ?: prompts.values.firstOrNull { it.id == id }?.let { question ->
            if (id == "general.callback" && (script.mood == "FLUESTERND" || script.kind != "REAL"))
                question.copy(answer = "Ich weiß noch nicht, ob ich wieder rangehen kann.")
            else question
        }

    fun isUnproductive(id: String) = id != "general.callback" && prompts.values.any { it.id == id }

    private fun topicOf(id: String) = when (id) {
        "wo", "wo_geruch", "haus", "karte" -> QuestionTopic.LOCATION
        "wer", "personen", "allein", "eltern", "taeter", "wieviel" -> QuestionTopic.PEOPLE
        "atmung", "fast", "zustand", "eingeklemmt", "zeichen" -> QuestionTopic.INJURIES
        "was", "klaerung", "hallo" -> QuestionTopic.EVENT
        "gefahr", "verkehr", "tempo", "wetter", "zug" -> QuestionTopic.DANGER
        "seit", "wann", "flucht", "sicht" -> QuestionTopic.DEVELOPMENT
        "name", "telefon" -> QuestionTopic.CALLBACK
        else -> QuestionTopic.DETAILS
    }
}
