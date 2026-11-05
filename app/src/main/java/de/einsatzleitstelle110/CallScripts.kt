package de.einsatzleitstelle110

/**
 * Written emergency calls: the caller only reveals what the player asks for. What is hidden in a
 * call decides which forces are really needed. Abstract and non-graphic; all people are fictional.
 * Placeholders: {strasse}, {ort}.
 */
data class Question(
    val id: String,
    val label: String,
    val answer: String,
    /** Essential for a complete call. Counts towards the call-taking rating. */
    val key: Boolean = true,
)

data class Advice(val id: String, val label: String, val good: Boolean, val reply: String)

data class CallScript(
    val id: String,
    /** REAL, or PRANK / MISDIAL / POCKET / INFO: no emergency, nobody should be sent. */
    val kind: String = "REAL",
    /** Matched against shift events; NIGHT and DAY limit the hours. */
    val tags: Set<String> = emptySet(),
    val caller: String,
    /** RUHIG, AUFGEREGT, PANISCH (needs calming before useful answers) or FLUESTERND. */
    val mood: String = "RUHIG",
    val opening: String,
    val correct: String = "",
    val wrong: List<String> = emptyList(),
    val questions: List<Question>,
    val advice: List<Advice> = emptyList(),
    /** Caller cannot name the place; only the mobile location (AML) pins it down. */
    val vague: Boolean = false,
    /** Asked: extra forces go out at once. Not asked: unit finds out on arrival, at a cost. */
    val twistQuestion: String = "",
    val twistAdd: List<String> = emptyList(),
    val twistRadio: String = "",
    /** Sudden line after n answers, so the call moves on by itself. */
    val beats: Map<Int, String> = emptyMap(),
    /** The situation worsens when nobody is there by half of the deadline. */
    val worse: String = "",
    val worseAdd: List<String> = emptyList(),
    val cpr: Boolean = false,
    /** Fixed place of the call; such a call only rings when that place is in the area. */
    val place: GeoPoint? = null,
)

object CallScripts {
    const val NO_ACTION = "Kein Einsatz erforderlich"

    private fun wo(answer: String) = Question("wo", "Wo genau ist das?", answer)
    private fun was(answer: String) = Question("was", "Was ist passiert?", answer)
    private fun wer(answer: String) = Question("wer", "Wie viele Personen, wer ist verletzt?", answer)
    private fun gefahr(answer: String) = Question("gefahr", "Besteht Gefahr, Waffen, Feuer?", answer)
    private fun extra(id: String, label: String, answer: String, key: Boolean = false) = Question(id, label, answer, key)
    private fun tip(id: String, label: String, good: Boolean, reply: String) = Advice(id, label, good, reply)
    private fun set(vararg v: String) = v.toSet()

    /** Calls that need a template the original catalog does not have. */
    val templates =
        CallScriptsHamburg.templates + listOf(
            IncidentTemplate("Reanimation", 1, "RETTUNG", 22, "Leblose Person, Telefonreanimation läuft.", requiredTypes = listOf("RETTUNG", "RETTUNG"), deadline = 20, reward = 1900),
            IncidentTemplate("Medizinischer Notfall", 2, "RETTUNG", 18, "Akute Erkrankung, Rettungsdienst erforderlich.", requiredTypes = listOf("RETTUNG"), deadline = 30, reward = 900),
            IncidentTemplate("Wohnungsbrand", 1, "FEUERWEHR", 35, "Feuer in einem Wohnhaus, Absperrung und Rettungsdienst erforderlich.", requiredTypes = listOf("FEUERWEHR", "RETTUNG", "STREIFE"), deadline = 25, reward = 2100),
            IncidentTemplate("Häusliche Gewalt", 1, "STREIFE", 28, "Bedrohung in einer Wohnung, Täter noch vor Ort.", requiredTypes = listOf("STREIFE", "STREIFE"), deadline = 25, reward = 1700),
            IncidentTemplate("Einbruch in Ausführung", 1, "STREIFE", 24, "Täter noch im Objekt, Festnahmechance.", requiredTypes = listOf("STREIFE", "STREIFE"), deadline = 25, reward = 1500),
            IncidentTemplate("Unfall mit Verletzten", 1, "STREIFE", 30, "Zusammenstoß mit Verletzten, Unfallaufnahme und Versorgung.", requiredTypes = listOf("STREIFE", "RETTUNG"), deadline = 25, reward = 1600),
            IncidentTemplate("Eingeklemmte Person", 1, "FEUERWEHR", 38, "Person im Fahrzeug eingeschlossen, technische Rettung.", requiredTypes = listOf("FEUERWEHR", "RETTUNG", "STREIFE"), deadline = 25, reward = 2200),
            IncidentTemplate("Raubüberfall", 1, "STREIFE", 30, "Täter flüchtig, Fahndung und Spurensicherung.", requiredTypes = listOf("STREIFE", "STREIFE", "KRIPO"), deadline = 25, reward = 1800),
            IncidentTemplate("Person im Wasser", 1, "FEUERWEHR", 30, "Person im Gewässer gesichtet, Wasserrettung.", requiredTypes = listOf("FEUERWEHR", "RETTUNG"), deadline = 20, reward = 2000),
            IncidentTemplate("Gasgeruch", 2, "FEUERWEHR", 24, "Gasgeruch im Gebäude, Absperrung erforderlich.", requiredTypes = listOf("FEUERWEHR", "STREIFE"), deadline = 30, reward = 1200),
            IncidentTemplate("Schlägerei", 1, "STREIFE", 22, "Körperliche Auseinandersetzung mehrerer Personen.", requiredTypes = listOf("STREIFE", "STREIFE"), deadline = 25, reward = 1200),
            IncidentTemplate("Taschendiebstahl", 3, "STREIFE", 12, "Geldbörse entwendet, Täter unbekannt.", requiredTypes = listOf("STREIFE"), deadline = 80, reward = 450),
            IncidentTemplate("Blinder Alarm", 3, "STREIFE", 4, "Vor Ort kein Notfall feststellbar.", requiredTypes = listOf("STREIFE"), deadline = 120, reward = 0),
        )

    val all: List<CallScript> by lazy { base + CallScriptsHamburg.list }

    private val base: List<CallScript> =
        listOf(
            CallScript(
                "herzstillstand", tags = set("MED"), caller = "Herr Sander", mood = "PANISCH", cpr = true,
                opening = "Mein Vater! Er ist einfach umgefallen, er rührt sich nicht mehr!",
                correct = "Reanimation", wrong = listOf("Medizinischer Notfall", "Hilflose Person am Bahnhof"),
                questions = listOf(
                    wo("{strasse} in {ort}, erster Stock, Sander steht am Klingelschild."),
                    was("Er saß am Tisch, hat sich an die Brust gefasst und ist vom Stuhl gerutscht."),
                    extra("atmung", "Atmet er normal? Kopf überstrecken, drei Sekunden hinsehen.", "Nein… nur so ein seltsames Schnappen, ganz selten. Das ist doch nicht normal, oder?", true),
                ),
                advice = listOf(
                    tip("cpr", "Telefonreanimation anleiten", true, "Gut, ich mache mit. Sagen Sie mir den Takt!"),
                    tip("wait", "Warten Sie, bis der Rettungsdienst da ist", false, "Warten?! Ich soll einfach nur zusehen?"),
                ),
                beats = mapOf(2 to "Er wird ganz blau im Gesicht! Bitte, was soll ich tun?!"),
                worse = "Sohn meldet: Person zeigt weiter kein Lebenszeichen.",
            ),
            CallScript(
                "pizza", tags = set("NIGHT", "HOME"), caller = "Unbekannte Frau", mood = "FLUESTERND",
                opening = "Hallo… ich möchte eine große Pizza bestellen.",
                correct = "Häusliche Gewalt", wrong = listOf("Ruhestörung", "Nachbarschaftskonflikt"),
                questions = listOf(
                    extra("hinweis", "Hier ist der Notruf. Sind Sie in Gefahr? Antworten Sie nur mit Ja oder Nein.", "Ja. Genau. Eine große, bitte.", true),
                    wo("Liefern Sie bitte in die {strasse}, Hausnummer zwölf, {ort}."),
                    extra("taeter", "Ist die Person noch bei Ihnen im Raum?", "Ja. Er steht gleich neben mir.", true),
                    gefahr("Mit… mit Messer. Also ohne Pilze, meine ich. Bitte schnell."),
                ),
                advice = listOf(
                    tip("leise", "Bleiben Sie ruhig in der Leitung. Sie müssen nichts sagen.", true, "Ja, danke. Das wäre sehr nett."),
                    tip("laut", "Sprechen Sie bitte lauter, ich verstehe Sie nicht.", false, "Ich… das kann ich gerade nicht…"),
                ),
                twistQuestion = "gefahr", twistAdd = listOf("STREIFE"),
                twistRadio = "Streife: Mann mit Messer in der Wohnung. Wir brauchen dringend Verstärkung.",
                beats = mapOf(3 to "(Im Hintergrund eine laute Männerstimme.) Ja, Pizza, ich sag doch!"),
                worse = "Stille Leitung. Im Hintergrund war zuletzt lautes Poltern zu hören.",
            ),
            CallScript(
                "einbrecher_daheim", tags = set("NIGHT", "HOME"), caller = "Frau Lindner", mood = "FLUESTERND",
                opening = "Da ist jemand in meinem Haus. Ich habe Glas splittern hören. Ich bin oben im Bad.",
                correct = "Einbruch in Ausführung", wrong = listOf("Spurensicherung nach Einbruch", "Ruhestörung"),
                questions = listOf(
                    wo("{strasse} in {ort}, das Reihenendhaus mit dem weißen Zaun."),
                    extra("wieviel", "Wie viele Personen hören Sie?", "Zwei Stimmen, glaube ich. Unten im Wohnzimmer.", true),
                    gefahr("Ich habe nichts gesehen. Einer hat was von „Stemmeisen“ gesagt."),
                    extra("allein", "Sind Sie allein im Haus?", "Ja. Nur mein Hund, und der ist bei mir."),
                ),
                advice = listOf(
                    tip("zu", "Schließen Sie sich ein, bleiben Sie leise am Telefon.", true, "Die Tür ist abgeschlossen."),
                    tip("nachsehen", "Gehen Sie nachsehen, ob wirklich jemand da ist.", false, "Das… das traue ich mich nicht. Muss ich das?"),
                ),
                beats = mapOf(2 to "Sie kommen die Treppe hoch… ich höre Schritte!"),
                twistQuestion = "wieviel", twistAdd = listOf("HUND"),
                twistRadio = "Streife: Zwei Täter flüchten durch die Gärten. Diensthund wäre hilfreich.",
                worse = "Anruferin meldet: Täter sind jetzt im Obergeschoss.",
            ),
            CallScript(
                "wohnungsbrand", tags = set("HOME"), caller = "Herr Polat", mood = "AUFGEREGT",
                opening = "Es brennt! Aus dem Fenster gegenüber kommen Flammen!",
                correct = "Wohnungsbrand", wrong = listOf("Gasgeruch", "Ruhestörung"),
                questions = listOf(
                    wo("{strasse} in {ort}, das Mehrfamilienhaus an der Ecke, dritter Stock."),
                    extra("personen", "Sind noch Menschen im Haus?", "Ja! Am Fenster daneben steht eine alte Frau und winkt!", true),
                    gefahr("Dicker schwarzer Rauch, er zieht schon ins Treppenhaus."),
                ),
                advice = listOf(
                    tip("raus", "Halten Sie Abstand und weisen Sie die Einsatzkräfte ein.", true, "Mache ich, ich stelle mich an die Straße."),
                    tip("rein", "Laufen Sie hinein und holen Sie die Frau.", false, "Da rein? In den Rauch?!"),
                ),
                twistQuestion = "personen", twistAdd = listOf("RETTUNG"),
                twistRadio = "Feuerwehr: Mehrere Bewohner mit Rauchgasverdacht. Weitere Rettungsmittel erforderlich.",
                beats = mapOf(1 to "Jetzt ist eine Scheibe geplatzt! Das wird immer mehr!"),
                worse = "Feuer greift auf die Nachbarwohnung über.", worseAdd = listOf("FEUERWEHR"),
            ),
            CallScript(
                "unfall_einfach", tags = set("TRAFFIC"), caller = "Frau Bergmann", mood = "AUFGEREGT",
                opening = "Ich hatte gerade einen Unfall. Mir ist jemand hinten draufgefahren.",
                correct = "Verkehrsunfall", wrong = listOf("Unfall mit Verletzten", "Fahrerflucht nach Parkschaden"),
                questions = listOf(
                    wo("{strasse}, Höhe {ort}, direkt an der Ampel."),
                    wer("Zwei Autos. Keiner verletzt, nur ein Schreck."),
                    extra("verkehr", "Stehen die Fahrzeuge auf der Fahrbahn?", "Ja, die rechte Spur ist blockiert.", false),
                ),
                advice = listOf(
                    tip("sichern", "Warnblinker an, Warnweste, Warndreieck, hinter die Leitplanke.", true, "Alles klar, mache ich sofort."),
                    tip("wegfahren", "Fahren Sie einfach weiter nach Hause.", false, "Darf ich das denn?"),
                ),
            ),
            CallScript(
                "unfall_verletzt", tags = set("TRAFFIC"), caller = "Herr Kaya", mood = "AUFGEREGT",
                opening = "Hier hat es richtig gekracht! Ein Auto steht quer auf der Straße!",
                correct = "Unfall mit Verletzten", wrong = listOf("Verkehrsunfall", "Stau nach Ampelausfall"),
                questions = listOf(
                    wo("{strasse} bei {ort}, kurz hinter der Kreuzung."),
                    wer("Drei, glaube ich. Die Fahrerin blutet am Kopf und ist ganz benommen."),
                    extra("eingeklemmt", "Kommen alle aus dem Fahrzeug heraus?", "Warten Sie… nein! Die Tür geht nicht auf, sie sitzt fest!", true),
                    gefahr("Es riecht nach Benzin, unter dem Auto läuft etwas aus."),
                ),
                advice = listOf(
                    tip("sichern", "Sichern Sie die Stelle ab, nicht rauchen, Abstand zum Fahrzeug.", true, "Ich sage es den anderen, wir winken den Verkehr vorbei."),
                    tip("ziehen", "Ziehen Sie die Fahrerin sofort aus dem Wagen.", false, "Mit Gewalt? Ich weiß nicht, die Tür klemmt doch…"),
                ),
                twistQuestion = "eingeklemmt", twistAdd = listOf("FEUERWEHR"),
                twistRadio = "Streife: Fahrerin ist im Wrack eingeschlossen. Feuerwehr zur technischen Rettung nachalarmieren!",
                worse = "Zeuge meldet: Die Fahrerin reagiert kaum noch.",
            ),
            CallScript(
                "autobahn_falschfahrer", tags = set("TRAFFIC"), caller = "Herr Vogt", mood = "AUFGEREGT", vague = true,
                opening = "Mir kommt ein Auto entgegen! Auf der Autobahn, auf meiner Spur!",
                correct = "Gefahrenstelle auf der Autobahn", wrong = listOf("Verdächtiges Fahrzeug", "Verkehrsunfall"),
                questions = listOf(
                    wo("Keine Ahnung, ich fahre hier schon ewig. Irgendwo vor der nächsten Ausfahrt!"),
                    extra("auto", "Was für ein Fahrzeug?", "Dunkler Kombi, sehr schnell, ohne Licht!", true),
                ),
                advice = listOf(
                    tip("rechts", "Fahren Sie ganz rechts und überholen Sie nicht.", true, "Bin rechts, Warnblinker ist an."),
                    tip("folgen", "Wenden Sie und fahren Sie dem Wagen nach.", false, "Wenden? Auf der Autobahn?!"),
                ),
                worse = "Weitere Anrufer melden den Falschfahrer, Ausfahrten nicht gesperrt.", worseAdd = listOf("STREIFE"),
            ),
            CallScript(
                "schlaegerei", tags = set("NIGHT", "CROWD", "DRUNK"), caller = "Frau Weiß", mood = "AUFGEREGT",
                opening = "Vor der Kneipe prügeln sie sich! Das sind ganz viele!",
                correct = "Schlägerei", wrong = listOf("Ruhestörung", "Auseinandersetzung vor Gaststätte"),
                questions = listOf(
                    wo("{strasse} in {ort}, vor der Eckkneipe."),
                    wer("Bestimmt zehn Leute. Einer liegt am Boden und steht nicht auf."),
                    gefahr("Einer hat eine Flasche in der Hand. Kaputt, glaube ich."),
                ),
                advice = listOf(
                    tip("abstand", "Bleiben Sie auf Abstand und beschreiben Sie, was Sie sehen.", true, "Ja. Ich stehe auf der anderen Straßenseite."),
                    tip("dazwischen", "Gehen Sie dazwischen und trennen Sie die Leute.", false, "Ich? Das sind zehn Männer!"),
                ),
                twistQuestion = "wer", twistAdd = listOf("RETTUNG"),
                twistRadio = "Streife: Eine Person bewusstlos am Boden. RTW wird benötigt.",
                worse = "Die Gruppe wächst, es wird weiter geschlagen.", worseAdd = listOf("STREIFE"),
            ),
            CallScript(
                "ruhestoerung", tags = set("NIGHT", "DRUNK"), caller = "Herr Thiele",
                opening = "Guten Abend. Mein Nachbar feiert schon wieder. Ich kann so nicht schlafen.",
                correct = "Ruhestörung", wrong = listOf("Nachbarschaftskonflikt", "Schlägerei"),
                questions = listOf(
                    wo("{strasse}, {ort}. Zweites Obergeschoss, das hört man schon von der Straße."),
                    was("Bass ohne Ende, seit Stunden, und dann gegrölt."),
                    gefahr("Nein, nein. Nur laut. Ich will bloß meine Ruhe."),
                ),
            ),
            CallScript(
                "kind_vermisst", tags = set("DAY", "CROWD", "SEARCH"), caller = "Frau Akin", mood = "PANISCH",
                opening = "Mein Sohn ist weg! Eben war er noch da! Er ist doch erst sechs!",
                correct = "Kind von Begleitperson getrennt", wrong = listOf("Vermisste Person im Park", "Ladendiebstahl"),
                questions = listOf(
                    wo("Auf dem Spielplatz an der {strasse} in {ort}."),
                    extra("aussehen", "Wie sieht er aus, was trägt er?", "Rote Jacke, blaue Mütze, Dinosaurier-Rucksack.", true),
                    extra("seit", "Seit wann ist er verschwunden?", "Zehn Minuten vielleicht. Er wollte zum Eiswagen."),
                ),
                advice = listOf(
                    tip("bleiben", "Bleiben Sie am letzten Ort, jemand sucht die Umgebung ab.", true, "Mein Mann läuft zum Eiswagen, ich bleibe hier."),
                    tip("fahren", "Fahren Sie erst einmal nach Hause und warten Sie.", false, "Nach Hause?! Mein Kind ist weg!"),
                ),
            ),
            CallScript(
                "fake_unfall_prank", kind = "PRANK", tags = set("DRUNK"), caller = "Junger Anrufer", mood = "AUFGEREGT",
                opening = "Hilfe, Polizei! Ein Alien ist in unseren Kühlschrank geflogen! (Im Hintergrund Kichern.)",
                wrong = listOf("Unbekannter Gegenstand", "Einbruch in Ausführung"),
                questions = listOf(
                    wo("Äh… in der Hauptstraße. Oder so. Hihi."),
                    was("Er hat den ganzen Käse gegessen!"),
                    extra("name", "Wie heißen Sie? Missbrauch von Notrufen ist strafbar.", "Ähm… Tschüss!", true),
                ),
            ),
            CallScript(
                "taschenanruf", kind = "POCKET", caller = "Unbekannt", mood = "RUHIG",
                opening = "(Rascheln. Schritte. Weit entfernt läuft Radiomusik.)",
                wrong = listOf("Unklare Notrufverbindung", "Häusliche Gewalt"),
                questions = listOf(
                    extra("hallo", "Hallo, hier ist der Notruf. Können Sie mich hören?", "(Rascheln. Jemand summt mit dem Radio.)", true),
                    extra("zeichen", "Wenn Sie nicht sprechen können, drücken Sie eine Taste.", "(Keine Reaktion. Schritte, eine Ladentür klingelt.) „…und zwei Brötchen, bitte.“", true),
                ),
            ),
            CallScript(
                "info_muell", kind = "INFO", caller = "Frau Dreyer",
                opening = "Guten Tag. Die Müllabfuhr war heute nicht da. An wen wende ich mich da?",
                wrong = listOf("Sachbeschädigung an Schule", "Nachbarschaftskonflikt"),
                questions = listOf(
                    was("Die Tonnen stehen immer noch draußen. Das geht doch nicht."),
                    gefahr("Gefahr? Nein, aber es riecht schon etwas."),
                ),
                advice = listOf(tip("verweis", "Das ist die Notrufnummer. Bitte wenden Sie sich an die Stadtverwaltung.", true, "Oh, entschuldigen Sie. Danke!")),
            ),
            CallScript(
                "info_wetter", kind = "INFO", tags = set("STORM"), caller = "Herr Gerlach",
                opening = "Sagen Sie mal, wird das heute noch mit dem Sturm? Ich will grillen.",
                wrong = listOf("Unwetter: umgestürzter Baum", "Ruhestörung"),
                questions = listOf(gefahr("Nein, nein, alles gut bei mir. Reine Neugier.")),
                advice = listOf(tip("verweis", "Der Notruf ist nur für Notfälle. Bitte legen Sie auf.", true, "Na gut, nichts für ungut.")),
            ),
            CallScript(
                "verwaehlt", kind = "MISDIAL", caller = "Ältere Dame",
                opening = "Hallo Helga, ich bin's! Wegen Sonntag…",
                wrong = listOf("Hilflose Person am Bahnhof", "Unklare Notrufverbindung"),
                questions = listOf(
                    extra("klaerung", "Hier ist der Notruf 110. Brauchen Sie Hilfe?", "Der Notruf? Oh je, ach du liebe Zeit! Nein, ich wollte die 1101 wählen. Entschuldigung!", true),
                ),
            ),
            CallScript(
                "raub_tankstelle", tags = set("NIGHT"), caller = "Herr Wolter", mood = "PANISCH",
                opening = "Ich bin überfallen worden! Ich arbeite an der Tankstelle, er ist gerade raus!",
                correct = "Raubüberfall", wrong = listOf("Ladendiebstahl", "Bewaffnete Bedrohung"),
                questions = listOf(
                    wo("Die Tankstelle an der {strasse}, {ort}."),
                    extra("flucht", "Wohin ist der Täter geflüchtet, womit?", "Motorroller, schwarz, Richtung Stadt. Eben gerade!", true),
                    gefahr("Er hatte eine Pistole. Ich weiß nicht, ob die echt war."),
                    wer("Ich bin allein. Verletzt bin ich nicht, nur meine Hände zittern."),
                ),
                advice = listOf(
                    tip("spuren", "Schließen Sie ab und fassen Sie nichts an, was der Täter berührt hat.", true, "Ich schließe zu. Die Kasse rühre ich nicht an."),
                    tip("hinterher", "Fahren Sie dem Täter hinterher.", false, "Dem mit der Pistole?!"),
                ),
                twistQuestion = "gefahr", twistAdd = listOf("HELI"),
                twistRadio = "Streife: Täter bewaffnet und mit Roller flüchtig. Fahndung aus der Luft wäre sinnvoll.",
            ),
            CallScript(
                "bewaffnet", tags = set("NIGHT"), caller = "Herr Ritter", mood = "FLUESTERND",
                opening = "Bei uns im Haus läuft ein Mann mit einer Waffe durchs Treppenhaus. Er schreit herum.",
                correct = "Bedrohung in Wohnhaus", wrong = listOf("Ruhestörung", "Schlägerei"),
                questions = listOf(
                    wo("{strasse} in {ort}, Eingang B."),
                    gefahr("Sieht aus wie ein Gewehr. Ich habe es durch den Türspion gesehen."),
                    extra("wer", "Kennen Sie den Mann?", "Das ist der Nachbar aus dem vierten Stock. Er hatte heute Ärger mit seiner Frau.", true),
                ),
                advice = listOf(
                    tip("drinnen", "Bleiben Sie in der Wohnung, Türen zu, weg von Tür und Fenstern.", true, "Wir sind im Schlafzimmer."),
                    tip("reden", "Öffnen Sie die Tür und reden Sie ihm gut zu.", false, "Das mache ich auf keinen Fall."),
                ),
                worse = "Weitere Anrufe aus dem Haus: Der Mann ist inzwischen auf der Straße.", worseAdd = listOf("STREIFE"),
            ),
            CallScript(
                "schlaganfall", tags = set("MED"), caller = "Frau Hoffmann", mood = "AUFGEREGT",
                opening = "Meine Mutter redet plötzlich so komisch. Ich verstehe sie gar nicht mehr.",
                correct = "Medizinischer Notfall", wrong = listOf("Hilflose Person am Bahnhof", "Reanimation"),
                questions = listOf(
                    wo("{strasse}, {ort}. Sie wohnt im Erdgeschoss."),
                    extra("fast", "Bitten Sie sie zu lächeln und beide Arme zu heben.", "Der Mund hängt rechts, und den rechten Arm bekommt sie nicht hoch!", true),
                    extra("seit", "Seit wann ist das so?", "Vor zwanzig Minuten war noch alles normal.", true),
                ),
                advice = listOf(
                    tip("nichts", "Nichts essen und trinken lassen, Oberkörper leicht hoch lagern.", true, "Gut, sie sitzt jetzt im Sessel."),
                    tip("hinlegen", "Lassen Sie sie erst mal ausschlafen.", false, "Ausschlafen? Das sieht aber gar nicht gut aus."),
                ),
            ),
            CallScript(
                "gas", tags = set("HOME"), caller = "Herr Busch", mood = "RUHIG",
                opening = "Im Treppenhaus riecht es stark nach Gas. Schon den ganzen Vormittag.",
                correct = "Gasgeruch", wrong = listOf("Wohnungsbrand", "Unbekannter Gegenstand"),
                questions = listOf(
                    wo("{strasse} in {ort}, Haus mit acht Parteien."),
                    extra("wo_geruch", "Wo ist es am stärksten?", "Unten am Keller. Beim Hausmeister macht keiner auf.", true),
                    gefahr("Mir ist schon etwas schwindelig."),
                ),
                advice = listOf(
                    tip("raus", "Kein Licht, keine Klingel, Fenster auf, alle ruhig nach draußen.", true, "Ich klopfe an die Türen und gehe raus."),
                    tip("licht", "Schalten Sie im Keller das Licht ein und suchen Sie die Stelle.", false, "Licht einschalten? Bei Gas? Sind Sie sicher?"),
                ),
                worse = "Hausbewohner melden Übelkeit, Geruch nimmt zu.", worseAdd = listOf("RETTUNG"),
            ),
            CallScript(
                "person_wasser", tags = set("SEARCH"), caller = "Frau Nowak", mood = "PANISCH", vague = true,
                opening = "Da treibt jemand im Wasser! Da ruft jemand um Hilfe!",
                correct = "Person im Wasser", wrong = listOf("Vermisste Person im Park", "Hilflose Person am Bahnhof"),
                questions = listOf(
                    wo("Am Ufer, beim Weg… ich bin hier nur spazieren, ich kenne mich nicht aus!"),
                    extra("sicht", "Können Sie die Person noch sehen?", "Ja, sie treibt ab, mit der Strömung nach links!", true),
                ),
                advice = listOf(
                    tip("sicht", "Behalten Sie die Stelle im Blick und werfen Sie etwas Schwimmfähiges zu.", true, "Hier ist ein Rettungsring am Pfosten!"),
                    tip("springen", "Springen Sie sofort hinterher.", false, "Ich kann doch gar nicht gut schwimmen!"),
                ),
                worse = "Zeugin sieht die Person nur noch selten an der Oberfläche.", worseAdd = listOf("HELI"),
            ),
            CallScript(
                "koffer", tags = set("CROWD"), caller = "Frau Sommer",
                opening = "Am Bahnhof steht seit einer Stunde ein Koffer ganz allein am Gleis.",
                correct = "Unbekannter Gegenstand", wrong = listOf("Verdächtiges Fahrzeug", "Hilflose Person am Bahnhof"),
                questions = listOf(
                    wo("Am Bahnhof {ort}, Zugang {strasse}, hinten am Fahrstuhl."),
                    extra("merkmal", "Fällt etwas Besonderes daran auf?", "Da hängen Kabel aus der Seitentasche. Vielleicht vom Ladegerät.", true),
                    wer("Es warten bestimmt fünfzig Leute am Gleis."),
                ),
                advice = listOf(
                    tip("abstand", "Nicht berühren, Abstand halten, Leute wegbitten.", true, "Ich sage dem Personal Bescheid und gehe auf Abstand."),
                    tip("oeffnen", "Schauen Sie vorsichtig hinein.", false, "Ich soll den aufmachen?"),
                ),
            ),
            CallScript(
                "taschendieb", tags = set("DAY", "CROWD"), caller = "Herr Lorenz",
                opening = "Mir wurde gerade die Geldbörse geklaut, in der Einkaufsstraße.",
                correct = "Taschendiebstahl", wrong = listOf("Raubüberfall", "Ladendiebstahl"),
                questions = listOf(
                    wo("{strasse} in {ort}, vor dem Drogeriemarkt."),
                    gefahr("Nein, ich habe gar nichts bemerkt. Sie war einfach weg."),
                    extra("karte", "Waren Karten im Portemonnaie?", "Ja, die EC-Karte. Ach herrje.", false),
                ),
                advice = listOf(tip("sperren", "Lassen Sie die Karte über den Sperr-Notruf 116 116 sperren.", true, "Das mache ich sofort, danke.")),
            ),
            CallScript(
                "baum_strasse", tags = set("STORM", "TRAFFIC"), caller = "Herr Schulte",
                opening = "Der Sturm hat einen Baum auf die Straße geworfen. Da kommt keiner mehr durch.",
                correct = "Unwetter: umgestürzter Baum", wrong = listOf("Stau nach Ampelausfall", "Tiere auf der Fahrbahn"),
                questions = listOf(
                    wo("{strasse} bei {ort}, gleich hinter der Kurve."),
                    wer("Niemand verletzt. Aber ein Auto hat's knapp erwischt."),
                    gefahr("Da hängt auch eine Stromleitung mit drin, glaube ich."),
                ),
                advice = listOf(tip("abstand", "Halten Sie Abstand zur Leitung und warnen Sie andere Fahrer.", true, "Ich stelle mein Warndreieck auf.")),
                twistQuestion = "gefahr", twistAdd = listOf("FEUERWEHR"),
                twistRadio = "Verkehrsdienst: Leitung liegt im Geäst. Feuerwehr nachfordern, Bereich sperren.",
            ),
            CallScript(
                "wasser_keller", kind = "INFO", tags = set("STORM"), caller = "Frau Peters",
                opening = "Bei mir steht ein bisschen Wasser im Keller, zwei Zentimeter. Kommt da jemand zum Aufwischen?",
                wrong = listOf("Evakuierungsunterstützung", "Unwetter: umgestürzter Baum"),
                questions = listOf(
                    gefahr("Nein, Strom ist aus dem Keller raus, da ist nur Gerümpel. Es ist nur ärgerlich."),
                    wer("Nein, es ist niemand in Gefahr."),
                ),
                advice = listOf(tip("verweis", "Bei Unwetter gehen Notlagen vor. Wir können hier leider nicht helfen.", true, "Verstehe. Ich versuche es mit dem Nachbarn.")),
            ),
            CallScript(
                "fahrerflucht", tags = set("TRAFFIC", "DAY"), caller = "Herr Merten",
                opening = "Mein geparktes Auto wurde angefahren, und der ist einfach weg.",
                correct = "Fahrerflucht nach Parkschaden", wrong = listOf("Verkehrsunfall", "Sachbeschädigung an Schule"),
                questions = listOf(
                    wo("Parkplatz an der {strasse}, {ort}."),
                    extra("kennz", "Haben Sie das Kennzeichen gesehen?", "Ein Zeuge hat es aufgeschrieben. Der wartet noch hier.", true),
                    wer("Nein, verletzt ist keiner."),
                ),
            ),
            CallScript(
                "verwirrter_senior", tags = set("NIGHT", "SEARCH"), caller = "Busfahrer Otte",
                opening = "Bei mir im Bus sitzt ein älterer Herr im Schlafanzug. Er weiß nicht, wo er hin will.",
                correct = "Hilflose Person am Bahnhof", wrong = listOf("Ruhestörung", "Reanimation"),
                questions = listOf(
                    wo("Endhaltestelle in {ort}, an der {strasse}."),
                    extra("zustand", "Wie geht es ihm? Ist er ansprechbar?", "Er ist freundlich, aber völlig verwirrt und friert.", true),
                ),
                advice = listOf(tip("warm", "Lassen Sie ihn im warmen Bus und bleiben Sie bei ihm.", true, "Klar, ich warte mit ihm hier.")),
            ),
            CallScript(
                "drohung_paket", tags = set("CROWD"), caller = "Frau Keller",
                opening = "Hier sind zwei Männer auf dem Parkplatz, die seit Stunden Autos abklappern.",
                correct = "Verdächtiges Fahrzeug", wrong = listOf("Einbruch in Ausführung", "Observation"),
                questions = listOf(
                    wo("Parkplatz am Einkaufszentrum, {strasse}, {ort}."),
                    extra("auto", "Beschreiben Sie Personen und Fahrzeug.", "Weißer Transporter, ein Kennzeichen von außerhalb. Beide in dunklen Jacken.", true),
                    gefahr("Nein, die haben mich nicht bemerkt."),
                ),
            ),
        )

    private val byId by lazy { all.associateBy { it.id } }

    fun find(id: String): CallScript? = byId[id]

    /** Any template, written or custom, still becomes a real phone call. */
    fun generic(t: IncidentTemplate): CallScript =
        CallScript(
            "gen:${t.title}",
            caller = "Anrufer",
            mood = if (t.priority == 1) "AUFGEREGT" else "RUHIG",
            opening = Scenarios.find(t.title)?.caller?.firstOrNull()?.trim('„', '“') ?: t.description,
            correct = t.title,
            wrong = emptyList(),
            questions = listOf(
                wo("{strasse} in {ort}."),
                was(Scenarios.find(t.title)?.caller?.getOrNull(1)?.trim('„', '“') ?: t.description),
                gefahr(if (t.priority == 1) "Ja, das sieht wirklich gefährlich aus. Bitte beeilen Sie sich." else "Nein, akut nicht, soweit ich das sehe."),
            ),
        )
}
