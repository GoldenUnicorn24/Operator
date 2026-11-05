package de.einsatzleitstelle110

/** Calls set at fixed Hamburg places. The location is the point of the call, so it is not random. */
object CallScriptsHamburg {
    private val REEPERBAHN = GeoPoint(9.9605, 53.5497)
    private val LANDUNGSBRUECKEN = GeoPoint(9.9680, 53.5456)
    private val JUNGFERNSTIEG = GeoPoint(9.9930, 53.5527)
    private val HAUPTBAHNHOF = GeoPoint(10.0069, 53.5528)
    private val AUSSENALSTER = GeoPoint(10.0040, 53.5700)
    private val FISCHMARKT = GeoPoint(9.9525, 53.5449)
    private val OEVELGOENNE = GeoPoint(9.9050, 53.5450)
    private val STADION = GeoPoint(9.8986, 53.5872)
    private val STADTPARK = GeoPoint(10.0240, 53.5970)
    private val FLUGHAFEN = GeoPoint(10.0060, 53.6320)
    private val HARBURG = GeoPoint(9.9900, 53.4560)
    private val WANDSBEK = GeoPoint(10.0670, 53.5730)
    private val BLANKENESE = GeoPoint(9.8130, 53.5590)
    private val BERGEDORF = GeoPoint(10.2050, 53.4890)
    private val SCHANZE = GeoPoint(9.9620, 53.5630)
    private val SPEICHERSTADT = GeoPoint(9.9950, 53.5430)
    private val ELBBRUECKEN = GeoPoint(10.0260, 53.5370)

    private fun wo(a: String) = Question("wo", "Wo genau ist das?", a)
    private fun was(a: String) = Question("was", "Was ist passiert?", a)
    private fun wer(a: String) = Question("wer", "Wie viele Personen, wer ist verletzt?", a)
    private fun gefahr(a: String) = Question("gefahr", "Besteht Gefahr, Waffen, Feuer?", a)
    private fun extra(id: String, label: String, a: String, key: Boolean = true) = Question(id, label, a, key)
    private fun tip(id: String, label: String, good: Boolean, reply: String) = Advice(id, label, good, reply)
    private fun set(vararg v: String) = v.toSet()

    val templates =
        listOf(
            IncidentTemplate("Betrugsanruf", 3, "KRIPO", 18, "Telefonbetrug, Geldübergabe verhindern.", requiredTypes = listOf("STREIFE"), deadline = 60, reward = 700),
            IncidentTemplate("Trunkenheitsfahrt", 2, "VERKEHR", 20, "Fahrzeug fährt in Schlangenlinien.", requiredTypes = listOf("STREIFE"), deadline = 30, reward = 800),
            IncidentTemplate("Bootsunfall", 1, "FEUERWEHR", 30, "Havarie auf dem Wasser, Personen in Not.", requiredTypes = listOf("FEUERWEHR", "RETTUNG"), deadline = 20, reward = 1900),
            IncidentTemplate("Fahrraddiebstahl", 3, "STREIFE", 10, "Täter beim Aufbrechen beobachtet.", requiredTypes = listOf("STREIFE"), deadline = 40, reward = 400),
            IncidentTemplate("Brand im Freien", 2, "FEUERWEHR", 20, "Container oder Böschung in Brand.", requiredTypes = listOf("FEUERWEHR"), deadline = 30, reward = 800),
            IncidentTemplate("Person auf Gleis", 1, "STREIFE", 20, "Person im Gleisbereich, Bahnverkehr sofort stoppen.", requiredTypes = listOf("STREIFE", "RETTUNG"), deadline = 20, reward = 1600),
        )

    val list: List<CallScript> =
        listOf(
            CallScript(
                "hh_kiez_flasche", tags = set("NIGHT", "DRUNK", "CROWD"), place = REEPERBAHN, caller = "Türsteher Malte", mood = "AUFGEREGT",
                opening = "Moin, hier geht's gleich rund! Vor unserem Laden auf dem Kiez kloppen sich zwei Gruppen.",
                correct = "Schlägerei", wrong = listOf("Ruhestörung", "Auseinandersetzung vor Gaststätte"),
                questions = listOf(
                    wo("Reeperbahn, Ecke Große Freiheit. Direkt vor der Tür."),
                    wer("Acht, neun Leute. Einer blutet am Kopf."),
                    gefahr("Einer hat ein Messer gezeigt und ist in die Seitengasse!"),
                ),
                advice = listOf(
                    tip("abstand", "Halten Sie Gäste drinnen und Abstand zu dem Mann mit dem Messer.", true, "Tür ist zu, wir bleiben drinnen."),
                    tip("packen", "Halten Sie den Mann mit dem Messer selbst fest.", false, "Nee, das lass ich schön bleiben."),
                ),
                twistQuestion = "gefahr", twistAdd = listOf("STREIFE"),
                twistRadio = "Streife: Bewaffneter Täter flüchtet Richtung Große Freiheit, wir brauchen Verstärkung.",
                worse = "Weitere Gäste mischen sich ein, es fliegen Flaschen.", worseAdd = listOf("STREIFE"),
            ),
            CallScript(
                "hh_kiez_kollaps", tags = set("NIGHT", "DRUNK", "MED"), place = REEPERBAHN, caller = "Junge Frau", mood = "AUFGEREGT",
                opening = "Meine Freundin ist in der Toilette umgekippt. Wir sind auf dem Kiez, die wacht nicht richtig auf!",
                correct = "Medizinischer Notfall", wrong = listOf("Reanimation", "Ruhestörung"),
                questions = listOf(
                    wo("Im Club am Spielbudenplatz, Damentoilette, erste Etage."),
                    extra("atmung", "Atmet sie normal?", "Ja, ganz ruhig. Sie stöhnt, wenn ich sie anfasse."),
                    extra("getraenk", "Was hat sie zu sich genommen?", "Zwei Cocktails nur. Ich glaube, da war was im Glas."),
                ),
                advice = listOf(
                    tip("seite", "Bringen Sie sie in die stabile Seitenlage und bleiben Sie bei ihr.", true, "Hab ich. Der Sicherheitsdienst ist jetzt da."),
                    tip("wasser", "Schütten Sie ihr kaltes Wasser ins Gesicht.", false, "Das bringt doch nichts, oder?"),
                ),
            ),
            CallScript(
                "hh_alster_segler", tags = set("DAY", "STORM"), place = AUSSENALSTER, caller = "Herr Claasen", mood = "AUFGEREGT",
                opening = "Auf der Außenalster ist ein Segelboot gekentert! Zwei Leute hängen am Rumpf.",
                correct = "Bootsunfall", wrong = listOf("Person im Wasser", "Vermisste Person im Park"),
                questions = listOf(
                    wo("Mitten auf der Außenalster, ich stehe an der Schönen Aussicht."),
                    wer("Zwei Erwachsene. Eine Person hat wohl keine Schwimmweste."),
                    extra("wetter", "Wie sind Wellen und Wind?", "Böig, das Wasser ist richtig kalt."),
                ),
                advice = listOf(
                    tip("blick", "Behalten Sie die Personen im Blick und weisen Sie die Retter ein.", true, "Ich bleibe am Steg und winke."),
                    tip("schwimmen", "Schwimmen Sie zu den beiden hinüber.", false, "Das sind doch vierhundert Meter!"),
                ),
                worse = "Eine Person hat den Rumpf losgelassen.", worseAdd = listOf("HELI"),
            ),
            CallScript(
                "hh_elbstrand", tags = set("DAY", "SEARCH"), place = OEVELGOENNE, caller = "Frau Hansen", mood = "PANISCH",
                opening = "Am Elbstrand! Ein Kind ist ins Wasser, die Welle vom Containerschiff hat es mitgenommen!",
                correct = "Person im Wasser", wrong = listOf("Kind von Begleitperson getrennt", "Vermisste Person im Park"),
                questions = listOf(
                    wo("Elbstrand Övelgönne, Höhe Strandperle."),
                    extra("sicht", "Können Sie das Kind noch sehen?", "Ja, ein Mann schwimmt hin, die Strömung zieht aber stark!"),
                    wer("Ein Junge, vielleicht acht."),
                ),
                advice = listOf(
                    tip("werfen", "Werfen Sie etwas Schwimmfähiges und rufen Sie weitere Helfer.", true, "Wir haben eine Luftmatratze!"),
                    tip("alle", "Alle sofort hinterher ins Wasser!", false, "Das ist doch viel zu gefährlich, die Strömung!"),
                ),
                worse = "Die Strömung treibt das Kind Richtung Fahrrinne.", worseAdd = listOf("HELI"),
            ),
            CallScript(
                "hh_hbf_gleis", tags = set("CROWD"), place = HAUPTBAHNHOF, caller = "Herr Dierks", mood = "AUFGEREGT",
                opening = "Am Hauptbahnhof läuft ein Mann unten im Gleis herum!",
                correct = "Person auf Gleis", wrong = listOf("Hilflose Person am Bahnhof", "Bahnhof: Diebstahlserie"),
                questions = listOf(
                    wo("Hauptbahnhof, Gleis 13, Südende."),
                    extra("zug", "Kommt gerade ein Zug?", "Die Anzeige sagt: in zwei Minuten!"),
                    extra("zustand", "Wie wirkt der Mann?", "Völlig verwirrt. Er redet mit sich selbst."),
                ),
                advice = listOf(
                    tip("personal", "Holen Sie sofort Bahnpersonal, nicht selbst ins Gleis.", true, "Da vorne ist die Aufsicht, ich laufe hin!"),
                    tip("runter", "Springen Sie runter und ziehen Sie ihn heraus.", false, "Ins Gleis? Da kommt doch gleich ein Zug!"),
                ),
                worse = "Zug steht vor der Einfahrt, Mann weiterhin im Gleis.", worseAdd = listOf("STREIFE"),
            ),
            CallScript(
                "hh_hbf_taschen", tags = set("DAY", "CROWD"), place = HAUPTBAHNHOF, caller = "Frau Meier",
                opening = "Mir wurde in der Wandelhalle die Handtasche weggerissen, er ist die Treppe hoch!",
                correct = "Raubüberfall", wrong = listOf("Taschendiebstahl", "Ladendiebstahl"),
                questions = listOf(
                    wo("Hauptbahnhof, Wandelhalle, Ausgang Kirchenallee."),
                    extra("flucht", "Wie sieht er aus, wohin läuft er?", "Graue Kapuzenjacke, Richtung Steintorwall."),
                    wer("Ich bin gestürzt, mein Arm tut weh."),
                ),
                advice = listOf(tip("bleiben", "Bleiben Sie sitzen, ich schicke Hilfe. Zeugen sollen warten.", true, "Eine Dame hält schon meinen Arm.")),
            ),
            CallScript(
                "hh_dom_kind", tags = set("CROWD", "SEARCH"), place = REEPERBAHN, caller = "Herr Yilmaz", mood = "PANISCH",
                opening = "Auf dem Dom! Meine Tochter ist weg, zwischen all den Leuten!",
                correct = "Kind von Begleitperson getrennt", wrong = listOf("Vermisste Person im Park", "Taschendiebstahl"),
                questions = listOf(
                    wo("Heiligengeistfeld, beim Riesenrad."),
                    extra("aussehen", "Wie alt, was trägt sie?", "Fünf Jahre, gelbes Kleid, Zöpfe."),
                    extra("seit", "Seit wann vermissen Sie sie?", "Seit fünf Minuten etwa."),
                ),
                advice = listOf(
                    tip("punkt", "Bleiben Sie am Riesenrad, Ihre Frau geht zum Dom-Sicherheitsdienst.", true, "Ja, sie läuft los!"),
                    tip("nachhause", "Gehen Sie heim, Kinder tauchen meistens wieder auf.", false, "Wie bitte? Das meinen Sie nicht ernst!"),
                ),
            ),
            CallScript(
                "hh_fischmarkt_gabelstapler", tags = set("DAY"), place = FISCHMARKT, caller = "Händler Rolf",
                opening = "Moin. Hier am Fischmarkt ist ein Lieferwagen in einen Stand gefahren.",
                correct = "Unfall mit Verletzten", wrong = listOf("Verkehrsunfall", "Sachbeschädigung an Schule"),
                questions = listOf(
                    wo("Große Elbstraße, direkt an der Fischauktionshalle."),
                    wer("Zwei Kunden sind verletzt. Einer hält sich das Bein."),
                    gefahr("Nee, aber die Gasflasche vom Stand liegt am Boden."),
                ),
                advice = listOf(tip("gas", "Halten Sie Leute von der Gasflasche fern, nicht bewegen.", true, "Mach ich. Wir sperren hier ab.")),
                twistQuestion = "gefahr", twistAdd = listOf("FEUERWEHR"),
                twistRadio = "Streife: Beschädigte Gasflasche am Stand. Feuerwehr zur Absicherung nötig.",
            ),
            CallScript(
                "hh_stadion_fans", tags = set("CROWD", "DRUNK"), place = STADION, caller = "Ordner Peters", mood = "AUFGEREGT",
                opening = "Hier am Stadion stehen sich zwei Fangruppen gegenüber. Das kippt gleich!",
                correct = "Schlägerei", wrong = listOf("Ruhestörung", "Verdächtiges Fahrzeug"),
                questions = listOf(
                    wo("Am Volksparkstadion, Vorplatz Nord, Parkplatz Blau."),
                    wer("Rund fünfzig Leute. Verletzte sehe ich noch keine."),
                    gefahr("Einige haben Pyrotechnik dabei."),
                ),
                advice = listOf(tip("abstand", "Ziehen Sie die Ordner zurück und halten Sie Rettungswege frei.", true, "Machen wir, wir gehen an den Zaun zurück.")),
                worse = "Erste Würfe, die Gruppen laufen aufeinander zu.", worseAdd = listOf("STREIFE", "RETTUNG"),
            ),
            CallScript(
                "hh_flughafen_koffer", tags = set("CROWD"), place = FLUGHAFEN, caller = "Herr Reimers",
                opening = "Terminal 1 am Flughafen: Da liegt seit einer halben Stunde ein Rucksack ohne Besitzer.",
                correct = "Unbekannter Gegenstand", wrong = listOf("Verdächtiges Fahrzeug", "Hilflose Person am Bahnhof"),
                questions = listOf(
                    wo("Flughafen, Terminal 1, Ankunft, an den Sitzbänken."),
                    extra("merkmal", "Fällt etwas auf daran?", "Nein, ein ganz normaler blauer Rucksack."),
                    wer("Hier warten viele Reisende."),
                ),
                advice = listOf(
                    tip("abstand", "Nicht anfassen, Flughafenpersonal informieren, Abstand halten.", true, "Ich sage dem Mitarbeiter am Schalter Bescheid."),
                    tip("oeffnen", "Schauen Sie nach, wem er gehört.", false, "Soll ich da wirklich reingucken?"),
                ),
            ),
            CallScript(
                "hh_schanze_brand", tags = set("NIGHT"), place = SCHANZE, caller = "Frau Brandt",
                opening = "In der Schanze brennen Müllcontainer direkt an einer Hauswand.",
                correct = "Brand im Freien", wrong = listOf("Wohnungsbrand", "Sachbeschädigung an Schule"),
                questions = listOf(
                    wo("Schulterblatt, Ecke Susannenstraße, im Hinterhof."),
                    extra("haus", "Greift das Feuer aufs Haus über?", "Noch nicht. Die Fassade wird aber schon schwarz."),
                    extra("taeter", "Haben Sie jemanden gesehen?", "Zwei Personen sind weggerannt.", false),
                ),
                advice = listOf(tip("fenster", "Fenster schließen, Bewohner warnen, nicht selbst löschen.", true, "Ich klingle bei den Nachbarn.")),
                worse = "Das Feuer greift auf den Hausflur über.", worseAdd = listOf("FEUERWEHR", "RETTUNG"),
            ),
            CallScript(
                "hh_speicherstadt_einbruch", tags = set("NIGHT"), place = SPEICHERSTADT, caller = "Wachmann Otte", mood = "FLUESTERND",
                opening = "Sicherheitsdienst Speicherstadt. In einem Kontor ist Licht, das darf nicht sein.",
                correct = "Einbruch in Ausführung", wrong = listOf("Ruhestörung", "Spurensicherung nach Einbruch"),
                questions = listOf(
                    wo("Am Sandtorkai, Block E, drittes Obergeschoss."),
                    extra("wieviel", "Wie viele Personen sehen Sie?", "Ich sehe zwei Taschenlampen."),
                    gefahr("Unten steht ein Transporter mit laufendem Motor."),
                ),
                advice = listOf(
                    tip("warten", "Bleiben Sie außer Sicht und merken Sie sich das Kennzeichen.", true, "Ich stehe hinter der Brücke."),
                    tip("rein", "Gehen Sie rein und stellen Sie die Täter.", false, "Allein? Das ist nicht mein Auftrag."),
                ),
                twistQuestion = "gefahr", twistAdd = listOf("STREIFE"),
                twistRadio = "Streife: Fluchtfahrzeug vor dem Objekt. Zweiten Wagen zur Absperrung schicken.",
            ),
            CallScript(
                "hh_elbbruecken_fahrer", tags = set("TRAFFIC", "NIGHT", "DRUNK"), place = ELBBRUECKEN, caller = "Herr Lübbe",
                opening = "Vor mir fährt einer in Schlangenlinien über die Elbbrücken.",
                correct = "Trunkenheitsfahrt", wrong = listOf("Verkehrsunfall", "Verdächtiges Fahrzeug"),
                questions = listOf(
                    wo("Elbbrücken stadtauswärts, gerade auf der Mitte."),
                    extra("auto", "Fahrzeug und Kennzeichen?", "Silberner Golf, Hamburger Kennzeichen, HH-K irgendwas."),
                    extra("tempo", "Wie schnell fährt er?", "Mal zwanzig, mal achtzig.", false),
                ),
                advice = listOf(
                    tip("abstand", "Abstand halten, nicht überholen, bleiben Sie am Telefon.", true, "Mache ich. Ich habe Freisprechen."),
                    tip("stoppen", "Überholen und das Auto ausbremsen.", false, "Ich soll den ausbremsen?"),
                ),
            ),
            CallScript(
                "hh_enkeltrick", tags = set("DAY"), place = BLANKENESE, caller = "Frau Thomsen",
                opening = "Junger Mann, die Polizei hat mich gerade angerufen, ich soll mein Geld vor die Tür legen.",
                correct = "Betrugsanruf", wrong = listOf("Hilflose Person am Bahnhof", "Einbruch in Ausführung"),
                questions = listOf(
                    wo("Blankeneser Hauptstraße, das weiße Haus am Treppenviertel."),
                    was("Ein Kommissar sagt, Einbrecher wären in der Gegend. Mein Schmuck wäre nicht sicher."),
                    extra("wann", "Wann soll das Geld abgeholt werden?", "In einer Stunde kommt ein Kollege von ihm."),
                ),
                advice = listOf(
                    tip("nicht", "Die echte Polizei fragt nie nach Geld. Bitte nichts herausgeben.", true, "Ach du meine Güte. Dann lege ich alles wieder hin."),
                    tip("legen", "Machen Sie, was der Kommissar sagt.", false, "Wirklich? Dann hole ich mal die Schatulle."),
                ),
            ),
            CallScript(
                "hh_stadtpark_rad", tags = set("DAY"), place = STADTPARK, caller = "Herr Jensen",
                opening = "Im Stadtpark ist gerade ein Radfahrer böse gestürzt, er steht nicht auf.",
                correct = "Medizinischer Notfall", wrong = listOf("Unfall mit Verletzten", "Vermisste Person im Park"),
                questions = listOf(
                    wo("Am Stadtparksee, Weg zur Freilichtbühne."),
                    extra("zustand", "Ist er ansprechbar?", "Ja, aber er weiß nicht, welcher Tag ist. Kein Helm."),
                ),
                advice = listOf(
                    tip("ruhig", "Lassen Sie ihn liegen, nicht bewegen, sprechen Sie mit ihm.", true, "Ist gut. Ich halte seine Hand."),
                    tip("hoch", "Helfen Sie ihm auf und setzen Sie ihn aufs Rad.", false, "Aufs Rad? Der kann kaum die Augen offenhalten."),
                ),
            ),
            CallScript(
                "hh_jungfernstieg_rad", tags = set("DAY"), place = JUNGFERNSTIEG, caller = "Frau Paulsen",
                opening = "Hier am Jungfernstieg knackt gerade jemand ganz dreist ein Fahrradschloss.",
                correct = "Fahrraddiebstahl", wrong = listOf("Taschendiebstahl", "Einbruch in Ausführung"),
                questions = listOf(
                    wo("Jungfernstieg, an den Fahrradbügeln vor dem Alsterhaus."),
                    extra("aussehen", "Beschreiben Sie die Person.", "Rote Jacke, Bolzenschneider im Rucksack."),
                    gefahr("Nein, der hat mich nicht bemerkt."),
                ),
                advice = listOf(tip("abstand", "Bleiben Sie auf Abstand und sagen Sie mir, wohin er geht.", true, "Er schiebt das Rad Richtung Rathaus.")),
            ),
            CallScript(
                "hh_harburg_bedrohung", tags = set("NIGHT"), place = HARBURG, caller = "Herr Celik", mood = "AUFGEREGT",
                opening = "Ich sitze im Kiosk und da steht ein Betrunkener, der Geld will und mir droht.",
                correct = "Bewaffnete Bedrohung", wrong = listOf("Ruhestörung", "Ladendiebstahl"),
                questions = listOf(
                    wo("Kiosk am Bahnhof Harburg, Hannoversche Straße."),
                    gefahr("Er hat die Hand in der Jacke. Er sagt, er hat ein Messer."),
                    extra("wer", "Kennen Sie den Mann?", "Nein, noch nie gesehen."),
                ),
                advice = listOf(
                    tip("ruhe", "Bleiben Sie ruhig. Geben Sie ihm im Zweifel das Geld.", true, "Ja. Er ist schon an der Kasse."),
                    tip("wehren", "Wehren Sie sich mit dem, was Sie finden.", false, "Bitte was? Er hat ein Messer!"),
                ),
            ),
            CallScript(
                "hh_wandsbek_oma", tags = set("MED", "HOME"), place = WANDSBEK, caller = "Pflegedienst Frau Lange",
                opening = "Ich stehe vor der Tür einer Patientin, sie macht nicht auf. Sonst ist sie immer da.",
                correct = "Hilflose Person am Bahnhof", wrong = listOf("Einbruch in Ausführung", "Ruhestörung"),
                questions = listOf(
                    wo("Wandsbeker Marktstraße, drittes Obergeschoss, Name Gerdes."),
                    extra("zeichen", "Hören Sie etwas aus der Wohnung?", "Ganz leise ein Rufen, glaube ich."),
                    extra("schluessel", "Haben Sie einen Schlüssel?", "Nein, der ist diese Woche bei der Tochter.", false),
                ),
                advice = listOf(tip("sprechen", "Rufen Sie durch die Tür, dass Hilfe kommt.", true, "Frau Gerdes, wir kommen zu Ihnen!")),
                twistQuestion = "zeichen", twistAdd = listOf("FEUERWEHR"),
                twistRadio = "Streife: Person hinter verschlossener Tür. Feuerwehr zur Türöffnung nötig.",
            ),
            CallScript(
                "hh_bergedorf_hund", tags = set("DAY"), place = BERGEDORF, caller = "Herr Schlüter",
                opening = "An der Bahnstrecke irrt ein großer Hund über die Straße. Die Autos müssen bremsen.",
                correct = "Tiere auf der Fahrbahn", wrong = listOf("Verkehrsunfall", "Person auf Gleis"),
                questions = listOf(
                    wo("Bergedorfer Straße, Höhe Bahnhof."),
                    gefahr("Er knurrt nicht, er hat nur Angst."),
                ),
                advice = listOf(tip("warnen", "Warnen Sie den Verkehr, aber laufen Sie nicht auf die Fahrbahn.", true, "Mache ich vom Gehweg aus.")),
            ),
            CallScript(
                "hh_michel_touristen", kind = "INFO", tags = set("DAY"), place = LANDUNGSBRUECKEN, caller = "Touristin",
                opening = "Hello? Excuse me, ich suche die Elbphilharmonie, ist das weit?",
                wrong = listOf("Hilflose Person am Bahnhof", "Vermisste Person im Park"),
                questions = listOf(extra("klaerung", "Haben Sie einen Notfall?", "Notfall? Nein, ich dachte, das ist die Touristen-Info!")),
                advice = listOf(tip("verweis", "Dies ist der Notruf. Fragen Sie bitte in der Touristeninformation.", true, "Oh, sorry! Thank you!")),
            ),
            CallScript(
                "hh_nachbar_paket", kind = "INFO", tags = set("DAY"), caller = "Herr Krüger",
                opening = "Der Paketbote hat mein Paket beim falschen Nachbarn abgegeben. Kann die Polizei das holen?",
                wrong = listOf("Nachbarschaftskonflikt", "Fahrerflucht nach Parkschaden"),
                questions = listOf(gefahr("Gefahr? Nee. Aber da sind neue Schuhe drin!")),
                advice = listOf(tip("verweis", "Das ist kein Notfall. Klären Sie das bitte mit dem Paketdienst.", true, "Na gut. Dann eben so.")),
            ),
            CallScript(
                "hh_kinder_kichern", kind = "PRANK", caller = "Kinderstimme",
                opening = "Bei mir brennt der Mond! (Lautes Kichern im Hintergrund.)",
                wrong = listOf("Wohnungsbrand", "Brand im Freien"),
                questions = listOf(
                    extra("eltern", "Sind deine Eltern da? Gib sie mir bitte mal.", "Mamaaa! Der Polizist will dich… (Es knackt, die Leitung ist tot.)"),
                ),
            ),
            CallScript(
                "hh_betrunken_taxi", kind = "MISDIAL", tags = set("NIGHT", "DRUNK"), caller = "Lallende Stimme",
                opening = "Tachchen. Einmal Taxi nach Barmbek, bitte. Aber fix.",
                wrong = listOf("Hilflose Person am Bahnhof", "Ruhestörung"),
                questions = listOf(
                    extra("klaerung", "Hier ist der Notruf. Brauchen Sie Hilfe?", "Hilfe? Ich brauch ein Taxi! Ach, ist das nicht die Taxizentrale? Na, denn nicht."),
                ),
            ),
            CallScript(
                "hh_verkehr_ampel", tags = set("TRAFFIC"), place = WANDSBEK, caller = "Frau Rickert",
                opening = "Die große Ampelkreuzung ist komplett ausgefallen. Es wird gefährlich.",
                correct = "Stau nach Ampelausfall", wrong = listOf("Verkehrsunfall", "Unwetter: umgestürzter Baum"),
                questions = listOf(
                    wo("Wandsbek Markt, an der Kreuzung Wandsbeker Chaussee."),
                    gefahr("Einige fahren einfach. Es hätte eben fast gekracht."),
                ),
            ),
            CallScript(
                "hh_laerm_hafen", tags = set("NIGHT"), place = LANDUNGSBRUECKEN, caller = "Anwohner Kroll",
                opening = "An den Landungsbrücken grölt eine Gruppe seit zwei Stunden mit voller Musikbox.",
                correct = "Ruhestörung", wrong = listOf("Schlägerei", "Nachbarschaftskonflikt"),
                questions = listOf(
                    wo("Landungsbrücken, Brücke 4, auf den Bänken."),
                    wer("Rund fünf junge Leute."),
                    gefahr("Nein. Nur unfassbar laut."),
                ),
            ),
            CallScript(
                "hh_ladendieb_mönckeberg", tags = set("DAY"), place = JUNGFERNSTIEG, caller = "Detektiv Holm",
                opening = "Kaufhausdetektiv hier. Ich habe einen Ladendieb im Büro, er wird aber langsam laut.",
                correct = "Ladendiebstahl", wrong = listOf("Raubüberfall", "Taschendiebstahl"),
                questions = listOf(
                    wo("Mönckebergstraße, Personaleingang Rückseite, zweiter Stock."),
                    gefahr("Er hat vorhin nach mir geschlagen, aber wir halten ihn fest."),
                ),
                advice = listOf(tip("ruhe", "Halten Sie Abstand und lassen Sie die Tür offen.", true, "Machen wir.")),
            ),
        )
}
