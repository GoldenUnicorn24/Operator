package de.einsatzleitstelle110

/**
 * Data-driven multi-phase incident flows (M2). A scenario adds a caller dialogue, decision stages
 * (call taking, situation on arrival, change during the work) and outcome-dependent follow-ups to
 * an incident template with the same title. Every option has its own visible consequences: risk,
 * work time, deadline, additional forces, priority, cost, reputation, flags for follow-ups.
 * Abstract control-room management only – no real tactical procedures.
 */
data class Effect(
    /** Positive = harder (lower success chance), negative = safer. */
    val risk: Double = 0.0,
    /** Extra (or saved, negative) minutes of work on scene. */
    val work: Int = 0,
    /** Deadline change in minutes. */
    val time: Int = 0,
    /** Additional forces that must be on scene. */
    val add: List<String> = emptyList(),
    /** Set priority (0 = unchanged). */
    val prio: Int = 0,
    val cost: Int = 0,
    val rep: Int = 0,
    /** Flag for follow-ups and later options. */
    val flag: String = "",
    val note: String,
)

data class Option(val id: String, val label: String, val effect: Effect, val needs: String = "")

/** trigger: CALL (on call taking), ARRIVAL (first unit on scene), WORK (half of the work done). */
data class Stage(
    val id: String,
    val trigger: String,
    val text: String,
    val options: List<Option>,
    val default: String,
)

/** on: SUCCESS, FAILURE or FLAG:<name>. */
data class FollowUp(
    val on: String,
    val title: String,
    val priority: Int,
    val types: List<String>,
    val duration: Int,
    val deadline: Int,
    val text: String,
    val chance: Double = 1.0,
)

data class Scenario(
    val id: String,
    val caller: List<String>,
    val stages: List<Stage>,
    val success: String,
    val failure: String,
    val follow: List<FollowUp> = emptyList(),
)

object Scenarios {
    private fun o(
        id: String,
        label: String,
        note: String,
        needs: String = "",
        risk: Double = 0.0,
        work: Int = 0,
        time: Int = 0,
        add: List<String> = emptyList(),
        prio: Int = 0,
        cost: Int = 0,
        rep: Int = 0,
        flag: String = "",
    ) = Option(id, label, Effect(risk, work, time, add, prio, cost, rep, flag, note), needs)

    private fun call(text: String, default: String, vararg options: Option) = Stage("call", "CALL", text, options.toList(), default)

    private fun arrival(text: String, default: String, vararg options: Option) = Stage("arrival", "ARRIVAL", text, options.toList(), default)

    private fun work(text: String, default: String, vararg options: Option) = Stage("work", "WORK", text, options.toList(), default)

    private fun f(on: String, title: String, priority: Int, types: List<String>, duration: Int, deadline: Int, text: String, chance: Double = 1.0) =
        FollowUp(on, title, priority, types, duration, deadline, text, chance)

    /** Generic call taking for templates without a written scenario: three different strategies. */
    val generic =
        call(
            "Wie gehst du mit dem Notruf um?",
            "now",
            o("ask", "Gezielt nachfragen", "Rückfragen kosten Zeit am Telefon, die Kräfte wissen aber genauer, was sie erwartet.", risk = -.05, time = -4),
            o("now", "Sofort alarmieren", "Ohne Rückfragen alarmiert – schnell, aber mit unvollständigem Lagebild.", risk = .02),
            o("care", "Anrufer am Telefon halten", "Die Leitstelle bleibt bis zum Eintreffen in der Leitung; beruhigt Betroffene.", risk = -.02, rep = 1, cost = 120),
        )

    val all: List<Scenario> =
        listOf(
            Scenario(
                "Ruhestörung",
                listOf("„Seit Stunden dröhnt Bass aus der Wohnung über mir. Ich muss um fünf zur Arbeit.“", "„Geklopft habe ich schon – da macht keiner auf.“"),
                listOf(
                    call("Die Anruferin klingt erschöpft, nicht bedroht.", "low",
                        o("low", "Als P3 einreihen", "Einsatz bleibt niedrig priorisiert; andere Lagen gehen vor.", time = 20),
                        o("ask", "Nach Gewalt/Streit fragen", "Keine Hinweise auf Streit – reine Feier.", risk = -.04, time = -3)),
                    arrival("Streife vor Ort: Wohnungsparty mit rund 20 Gästen, Gastgeber uneinsichtig.", "talk",
                        o("talk", "Ansprache, Musik leiser", "Gespräch an der Tür – dauert, wirkt aber meist.", work = 4),
                        o("end", "Feier beenden lassen", "Konsequent, aber Gäste reagieren gereizt.", risk = .06, work = -3, rep = -1),
                        o("calm", "Deeskalierende Ansprache", "Geschulte Ansprache: Gastgeber kooperiert sofort.", needs = "QUAL:Deeskalation", risk = -.08, work = -2)),
                ),
                "Musik leiser, Nachbarschaft beruhigt.",
                "Die Feier geht weiter, weitere Beschwerden folgen.",
                listOf(f("FAILURE", "Erneute Ruhestörung", 3, listOf("STREIFE"), 10, 70, "Dieselbe Adresse meldet sich erneut.", .7)),
            ),
            Scenario(
                "Verkehrsunfall",
                listOf("„Hier sind zwei Autos zusammengestoßen, mitten auf der Kreuzung!“", "„Einer hält sich den Nacken, sonst steht man nur rum.“"),
                listOf(
                    call("Mögliche Verletzung gemeldet.", "rtw",
                        o("rtw", "Rettungsdienst mitalarmieren", "Rettungsdienst prüft die Nackenverletzung parallel.", add = listOf("RETTUNG"), risk = -.08),
                        o("police", "Nur Polizei", "Spart Rettungsmittel – Verletzung bleibt ungeprüft.", risk = .07)),
                    arrival("Kreuzung blockiert, Rückstau in beide Richtungen.", "secure",
                        o("secure", "Absichern und aufnehmen", "Unfallstelle gesichert, Aufnahme nach Vorschrift.", work = 4),
                        o("clear", "Schnell räumen lassen", "Fotos, dann Fahrzeuge an den Rand – Verkehr fließt wieder.", work = -6, risk = .04, rep = 1),
                        o("vpi", "Verkehrspolizei nachfordern", "Spezialisierte Unfallaufnahme, Umleitung koordiniert.", add = listOf("VERKEHR"), risk = -.06, work = -4)),
                ),
                "Unfall aufgenommen, Kreuzung wieder frei.",
                "Aufnahme lückenhaft, Verkehrschaos über Stunden.",
                listOf(f("FAILURE", "Stau nach Unfall", 2, listOf("VERKEHR"), 18, 80, "Der Rückstau hat sich bis zur Hauptverkehrsstraße ausgedehnt.", .6)),
            ),
            Scenario(
                "Ladendiebstahl",
                listOf("„Wir haben hier jemanden im Büro, der hat Elektronik eingesteckt.“", "„Er will jetzt gehen und wird laut.“"),
                listOf(
                    call("Detektiv hält eine Person fest, Stimmung kippt.", "hurry",
                        o("hurry", "Zügig anfahren lassen", "Streife fährt mit Vorrang an.", prio = 2, time = -10),
                        o("wait", "Ruhig halten lassen", "Detektiv soll die Person gehen lassen, falls es eskaliert.", risk = .05, flag = "gone")),
                    arrival("Person ohne Ausweis, verweigert Angaben.", "id",
                        o("id", "Identität feststellen", "Personalien ermittelt, Anzeige aufgenommen.", work = 5),
                        o("kripo", "An Kripo übergeben (Serie?)", "Kripo prüft Zusammenhang mit einer Diebstahlserie.", needs = "TYPE:KRIPO", flag = "serie", work = 8)),
                ),
                "Anzeige aufgenommen, Ware zurück.",
                "Person entkommt mit der Ware.",
                listOf(f("FLAG:serie", "Hinweis auf Hehlerbande", 2, listOf("KRIPO"), 30, 200, "Die Kripo erkennt ein Muster in mehreren Filialen.", .8)),
            ),
            Scenario(
                "Bewaffnete Bedrohung",
                listOf("„Mein Nachbar steht mit etwas Langem im Treppenhaus und schreit, er macht alle fertig!“", "„Ich habe mich eingeschlossen.“"),
                listOf(
                    call("Akute Gefahr für Unbeteiligte.", "cordon",
                        o("cordon", "Streifen sperren weiträumig ab", "Erste Kräfte sichern den Bereich, bis Spezialkräfte bereit sind.", risk = -.04),
                        o("inside", "Anrufer im Raum bleiben lassen", "Anrufer bleibt verschlossen in der Wohnung und gibt Lageinfos.", risk = -.06, rep = 1),
                        o("heli", "Hubschrauber zur Übersicht", "Luftbild unterstützt die Lagebeurteilung.", add = listOf("HELI"), cost = 600, risk = -.05)),
                    arrival("Person zieht sich in die Wohnung zurück, ist nicht ansprechbar.", "negotiate",
                        o("negotiate", "Gespräch suchen, Zeit gewinnen", "Ansprache über Telefon – dauert lange, senkt das Risiko deutlich.", work = 25, risk = -.12),
                        o("sek", "Spezialkräfte lösen die Lage", "SEK übernimmt sofort – schnell, aber riskanter.", needs = "TYPE:SEK", work = -10, risk = .04),
                        o("lead", "Einsatzführung koordiniert", "Erfahrene Einsatzführung bündelt alle Kräfte.", needs = "QUAL:Einsatzführung", risk = -.1)),
                ),
                "Person unverletzt in Gewahrsam, niemand verletzt.",
                "Lage eskaliert; Verletzte und massive Kritik.",
                listOf(f("SUCCESS", "Spurensicherung nach Bedrohungslage", 3, listOf("KRIPO"), 25, 240, "Die Kripo dokumentiert die Wohnung.", .8)),
            ),
            Scenario(
                "Observation",
                listOf("„Hier Kripo-Dauerdienst: Wir brauchen Augen auf einen Lagerhof, heute Nacht soll dort Ware umgeschlagen werden.“"),
                listOf(
                    arrival("MEK in Position. Bewegung am Tor, mehrere Fahrzeuge.", "watch",
                        o("watch", "Verdeckt weiter beobachten", "Geduldige Beobachtung bringt Kennzeichen und Treffpunkte.", work = 15, flag = "intel", risk = -.04),
                        o("follow", "Fahrzeug unauffällig folgen", "Ein Wagen wird bis zur Wohnadresse begleitet.", work = 5, flag = "address", risk = .06),
                        o("drone", "Drohnenbild ergänzen", "Luftbild mit Drohne zeigt den gesamten Hof.", needs = "GEAR:Drohne", flag = "intel", risk = -.08)),
                ),
                "Beobachtung liefert verwertbare Hinweise.",
                "Observation enttarnt; Zielpersonen gewarnt.",
                listOf(
                    f("FLAG:intel", "Auswertung der Beobachtung", 2, listOf("KRIPO"), 25, 240, "Die Kripo verdichtet die Beobachtungsergebnisse."),
                    f("FLAG:address", "Durchsuchung vorbereiten", 1, listOf("SEK", "KRIPO"), 35, 180, "Richterlicher Beschluss liegt vor; mögliche Gefahrenlage an der Adresse.", .7),
                ),
            ),
            Scenario(
                "Einbruchserie",
                listOf("„Hier wurde schon wieder eingebrochen – das ist der dritte Keller in der Straße diese Woche!“"),
                listOf(
                    arrival("Kripo vor Ort: gleiche Werkzeugspuren wie bei den letzten Taten.", "pattern",
                        o("pattern", "Muster auswerten", "Tatzeiten und Wege werden verglichen.", work = 10, flag = "pattern"),
                        o("door", "Nachbarschaft befragen", "Zeugen beschreiben einen Transporter.", work = 6, flag = "van", rep = 1),
                        o("quick", "Nur Anzeige aufnehmen", "Schnell erledigt, die Serie läuft weiter.", work = -8, risk = .08)),
                ),
                "Täterprofil steht, Prävention in der Nachbarschaft.",
                "Keine Spur – die Serie geht weiter.",
                listOf(
                    f("FLAG:pattern", "Observation an der Tatortreihe", 2, listOf("MEK"), 40, 300, "Das Muster legt nahe, wo die Täter als Nächstes zuschlagen."),
                    f("FLAG:van", "Fahndung nach Transporter", 2, listOf("STREIFE", "STREIFE"), 20, 90, "Ein passender Transporter wurde in der Nähe gesehen.", .8),
                    f("FAILURE", "Erneuter Kellereinbruch", 3, listOf("STREIFE"), 15, 120, "Wieder ein Keller aufgebrochen.", .8),
                ),
            ),
            Scenario(
                "Großveranstaltung",
                listOf("„Veranstaltungsleitung hier: Der Einlass staut sich bis auf die Straße, es wird gedrängelt.“"),
                listOf(
                    call("Gefahr durch Gedränge am Einlass.", "more",
                        o("more", "Bereitschaftspolizei anfordern", "Zusätzliche Kräfte für die Besucherlenkung.", add = listOf("BEREITSCHAFT"), risk = -.08, cost = 400),
                        o("organizer", "Veranstalter öffnet Notausgänge", "Veranstalter entlastet selbst – günstig, aber unkontrollierter.", risk = .05)),
                    work("Ein Teil der Besucher drängt zur Bühne, Absperrgitter wackeln.", "split",
                        o("split", "Ströme umlenken", "Besucher werden über einen zweiten Weg geführt.", work = 8, risk = -.05),
                        o("stop", "Einlass kurz stoppen", "Pause entschärft, sorgt aber für Unmut.", work = 4, risk = -.08, rep = -1),
                        o("lead", "Lageführung übernimmt", "Geschulte Lageführung ordnet Bereiche zu.", needs = "QUAL:Lageführung", risk = -.12, work = -4)),
                ),
                "Veranstaltung verläuft geordnet.",
                "Gedränge mit Leichtverletzten – Ermittlungen folgen.",
                listOf(f("FAILURE", "Verletzte nach Gedränge", 1, listOf("RETTUNG", "STREIFE"), 25, 60, "Mehrere Personen benötigen Hilfe.")),
            ),
            Scenario(
                "Geisellage",
                listOf("„In der Bankfiliale hält ein Mann Kunden fest, er verlangt ein Fluchtfahrzeug!“"),
                listOf(
                    call("Schwerste Lage. Erste Kräfte dürfen nichts überstürzen.", "cordon",
                        o("cordon", "Absperren, Spezialkräfte anfordern", "Weiträumige Absperrung; SEK wird vorbereitet.", risk = -.05, add = listOf("BEREITSCHAFT")),
                        o("quiet", "Ohne Sondersignal heranführen", "Leiser Ansatz – verhindert Panik beim Täter.", risk = -.06, time = 15)),
                    arrival("Telefonkontakt zum Täter steht. Er wird ungeduldig.", "talk",
                        o("talk", "Verhandeln und Zeit gewinnen", "Lange Verhandlung, sehr geringe Gefährdung.", work = 40, risk = -.15),
                        o("access", "SEK-Zugriff bei günstiger Gelegenheit", "Schneller Abschluss, aber höheres Risiko.", needs = "TYPE:SEK", work = -15, risk = .05),
                        o("comm", "Geschulte Gesprächsführung", "Kommunikationsprofi baut Vertrauen auf.", needs = "QUAL:Kommunikation", risk = -.12, work = 10)),
                ),
                "Geiseln unverletzt frei, Täter festgenommen.",
                "Geiseln verletzt – schwere Vorwürfe gegen die Einsatzleitung.",
                listOf(f("SUCCESS", "Tatortarbeit Bankfiliale", 2, listOf("KRIPO"), 40, 300, "Umfangreiche Spurensicherung.")),
            ),
            Scenario(
                "Vermisste Person im Park",
                listOf("„Mein Vater ist dement und seit einer Stunde nicht vom Spaziergang zurück. Es wird bald dunkel.“"),
                listOf(
                    call("Zeitdruck: Dunkelheit und Kälte.", "area",
                        o("area", "Suchgebiet eingrenzen", "Lieblingswege erfragt – Suche gezielter.", risk = -.08, time = -5),
                        o("dog", "Hundestaffel sofort dazu", "Spürhund nimmt die Fährte auf.", add = listOf("HUND"), risk = -.1),
                        o("heli", "Hubschrauber mit Wärmebild", "Großflächig, teuer, sehr wirksam bei Dunkelheit.", add = listOf("HELI"), cost = 900, risk = -.14)),
                    work("Ein Hinweis: Ein Mann wurde am See gesehen.", "lake",
                        o("lake", "Suche zum See verlagern", "Kräfte konzentrieren sich auf das Ufer.", work = -6, risk = -.05),
                        o("both", "Beides absuchen", "Gründlicher, dauert länger.", work = 10, risk = -.08),
                        o("drone", "Drohne über das Ufer", "Schneller Überblick aus der Luft.", needs = "GEAR:Drohne", work = -10, risk = -.08)),
                ),
                "Vermisster unterkühlt, aber wohlbehalten gefunden.",
                "Suche ohne Ergebnis in der Nacht abgebrochen.",
                listOf(f("FAILURE", "Fortsetzung der Vermisstensuche", 1, listOf("HELI", "HUND"), 40, 90, "Bei Tagesanbruch wird die Suche ausgeweitet.")),
            ),
            Scenario(
                "Kind von Begleitperson getrennt",
                listOf("„Meine Tochter, vier Jahre, war eben noch neben mir am Markt – jetzt ist sie weg!“"),
                listOf(
                    call("Jede Minute zählt.", "describe",
                        o("describe", "Beschreibung an alle Streifen", "Kleidung und Größe gehen an alle Kräfte im Umkreis.", risk = -.1, time = -2),
                        o("stay", "Mutter bleibt am Treffpunkt", "Kind läuft oft zum letzten Ort zurück.", risk = -.05, flag = "meet")),
                    arrival("Markt voller Menschen, Durchsagen möglich.", "speaker",
                        o("speaker", "Lautsprecherdurchsage", "Händler und Passanten helfen suchen.", work = -5, risk = -.06),
                        o("exits", "Ausgänge besetzen", "Bindet Kräfte, verhindert Weglaufen auf die Straße.", work = 5, risk = -.1, add = listOf("STREIFE"))),
                ),
                "Kind nach wenigen Minuten wohlbehalten bei der Mutter.",
                "Kind erst nach langer Zeit gefunden – heftige Kritik.",
            ),
            Scenario(
                "Gefahrenstelle auf der Autobahn",
                listOf("„Auf der Autobahn liegt eine Palette auf der linken Spur, Autos weichen in letzter Sekunde aus!“"),
                listOf(
                    call("Hohe Unfallgefahr bei hoher Geschwindigkeit.", "warn",
                        o("warn", "Verkehrswarnung absetzen", "Radiowarnung und Hinweis an Autobahnmeisterei.", risk = -.06),
                        o("block", "Spur sofort sperren lassen", "Sicher, verursacht aber Stau.", risk = -.12, rep = -1, flag = "stau")),
                    arrival("Ladungsteile über zwei Spuren verteilt.", "clear",
                        o("clear", "Spur räumen", "Kräfte räumen mit Absicherung.", work = 6),
                        o("cam", "Fahrzeugkamera dokumentiert", "Kameradaten helfen, den Verursacher zu finden.", needs = "GEAR:Fahrzeugkamera", flag = "verursacher", work = 2)),
                ),
                "Gefahrenstelle beseitigt, kein Unfall.",
                "Auffahrunfall an der Gefahrenstelle.",
                listOf(
                    f("FAILURE", "Unfall mit mehreren Fahrzeugen", 1, listOf("VERKEHR", "RETTUNG"), 35, 60, "Folgeunfall an der Gefahrenstelle."),
                    f("FLAG:verursacher", "Verursacher ermitteln", 3, listOf("KRIPO"), 15, 300, "Das Kennzeichen des verlierenden Lkw ist bekannt."),
                    f("FLAG:stau", "Stau nach Ampelausfall", 2, listOf("VERKEHR"), 15, 90, "Die Sperrung führt zu Stau im Zulauf.", .5),
                ),
            ),
            Scenario(
                "Hilflose Person am Bahnhof",
                listOf("„Hier liegt ein älterer Herr auf der Bank, er weiß nicht, wo er ist.“"),
                listOf(
                    call("Medizinischer Hintergrund möglich.", "rtw",
                        o("rtw", "Rettungsdienst mitalarmieren", "Medizinische Abklärung parallel.", add = listOf("RETTUNG"), risk = -.08),
                        o("only", "Streife klärt zuerst", "Spart Rettungsmittel, Risiko bei Notfall.", risk = .06)),
                    arrival("Mann orientierungslos, Ausweis in der Tasche.", "family",
                        o("family", "Angehörige verständigen", "Tochter holt den Vater ab.", work = 6, rep = 1),
                        o("aid", "Erste Hilfe leisten", "Ausgebildete Ersthelfer erkennen Unterzuckerung.", needs = "QUAL:Erste Hilfe", risk = -.1, work = -3)),
                ),
                "Mann versorgt und an Angehörige übergeben.",
                "Mann bricht später zusammen – Vorwürfe gegen die Leitstelle.",
            ),
            Scenario(
                "Unklare Notrufverbindung",
                listOf("Rauschen. Eine Frauenstimme flüstert etwas, dann wird aufgelegt."),
                listOf(
                    call("Ein stiller Notruf kann alles bedeuten.", "callback",
                        o("callback", "Zurückrufen", "Rückruf: Ein Mann meldet sich, alles sei in Ordnung – klingt angespannt.", time = -3, flag = "hausstreit"),
                        o("locate", "Standort ermitteln und hinfahren", "Funkzelle eingegrenzt, Streife fährt hin.", risk = -.05),
                        o("ignore", "Als Fehlanruf werten", "Spart Kräfte – falls es ernst war, droht schwere Kritik.", risk = .25, time = -40)),
                    arrival("Wohnung: Frau mit Hämatom, Mann aggressiv.", "separate",
                        o("separate", "Beteiligte trennen", "Häusliche Gewalt: Platzverweis und Beratung vermittelt.", work = 10, risk = -.06),
                        o("calm", "Deeskalation", "Geschulte Ansprache beruhigt den Mann.", needs = "QUAL:Deeskalation", risk = -.1)),
                ),
                "Frau in Sicherheit, Verfahren eingeleitet.",
                "Situation falsch eingeschätzt.",
                listOf(f("FLAG:hausstreit", "Häuslicher Konflikt – Nachsorge", 3, listOf("KRIPO"), 20, 400, "Die Kripo nimmt die Anzeige wegen häuslicher Gewalt auf.", .8)),
            ),
            Scenario(
                "Verdächtiges Fahrzeug",
                listOf("„Seit drei Tagen steht ein Transporter mit laufendem Motor vor der Schule. Jeden Morgen.“"),
                listOf(
                    arrival("Fahrzeug leer, Kennzeichen gehört zu anderem Fahrzeug.", "check",
                        o("check", "Halterabfrage, abschleppen", "Fahrzeug sichergestellt.", work = 6, flag = "plates"),
                        o("watch", "Unauffällig beobachten lassen", "MEK beobachtet, wer das Fahrzeug abholt.", needs = "TYPE:MEK", work = 15, flag = "abholer")),
                ),
                "Fahrzeug sichergestellt.",
                "Fahrzeug verschwindet unbemerkt.",
                listOf(
                    f("FLAG:plates", "Schwerer Autodiebstahl", 2, listOf("KRIPO"), 25, 300, "Das Fahrzeug stammt aus einem Diebstahl.", .7),
                    f("FLAG:abholer", "Fahndung nach Hinweislage", 2, listOf("MEK", "STREIFE"), 30, 120, "Der Abholer fährt zu einer bekannten Adresse."),
                ),
            ),
            Scenario(
                "Auseinandersetzung vor Gaststätte",
                listOf("„Vor der Kneipe prügeln sich vier, fünf Leute! Einer liegt am Boden!“"),
                listOf(
                    call("Verletzte möglich, Lage dynamisch.", "two",
                        o("two", "Zwei Streifen schicken", "Mehr Kräfte vor Ort schrecken ab.", add = listOf("STREIFE"), risk = -.1),
                        o("rtw", "Rettungsdienst mit", "Am Boden liegender wird versorgt.", add = listOf("RETTUNG"), risk = -.06)),
                    arrival("Gruppe zerstreut sich, zwei Beteiligte noch vor Ort.", "id",
                        o("id", "Personalien aufnehmen", "Anzeigen wegen Körperverletzung.", work = 8),
                        o("search", "Flüchtige suchen lassen", "Nahbereichsfahndung nach den anderen.", work = 10, flag = "flucht"),
                        o("cam", "Bodycam-Aufnahmen sichern", "Aufnahmen erleichtern die Beweisführung.", needs = "GEAR:Bodycam", risk = -.08)),
                ),
                "Lage beruhigt, Tatverdächtige ermittelt.",
                "Erneute Schlägerei am selben Ort.",
                listOf(f("FLAG:flucht", "Zeugenaufruf nach Raub", 2, listOf("KRIPO"), 20, 240, "Einer der Flüchtigen hatte eine fremde Geldbörse.", .5)),
            ),
            Scenario(
                "Unfall mit mehreren Fahrzeugen",
                listOf("„Massenunfall! Fünf, sechs Autos ineinander, Rauch aus einem Motor!“"),
                listOf(
                    call("Mehrere Verletzte, Brandgefahr.", "full",
                        o("full", "Feuerwehr und Rettungsdienst", "Voller Rettungsaufwand – sicher, aber teuer.", add = listOf("FEUERWEHR", "RETTUNG"), cost = 500, risk = -.14),
                        o("rtw", "Nur Rettungsdienst dazu", "Rauch wird unterschätzt.", add = listOf("RETTUNG"), risk = .04)),
                    work("Gaffer bremsen auf der Gegenfahrbahn – Folgeunfall droht.", "screen",
                        o("screen", "Sichtschutz, Gegenfahrbahn sichern", "Bindet eine weitere Streife.", add = listOf("STREIFE"), risk = -.08),
                        o("ignore", "Auf Unfallstelle konzentrieren", "Schneller fertig, Risiko auf Gegenfahrbahn.", work = -8, risk = .07, flag = "gaffer")),
                ),
                "Alle Verletzten versorgt, Strecke wieder frei.",
                "Folgeunfälle und Kritik an der Koordination.",
                listOf(f("FLAG:gaffer", "Unfallaufnahme Gegenfahrbahn", 2, listOf("VERKEHR"), 20, 80, "Auf der Gegenfahrbahn kracht es.", .6)),
            ),
            Scenario(
                "Suche im Waldgebiet",
                listOf("„Unser Sohn, 15, ist nach einem Streit mit dem Fahrrad in den Wald gefahren. Sein Handy ist aus.“"),
                listOf(
                    arrival("Hund nimmt am Waldrand eine Spur auf.", "trail",
                        o("trail", "Fährte folgen", "Langsam, aber zielgerichtet.", work = 10, risk = -.06),
                        o("wide", "Bereitschaftspolizei für Flächensuche", "Viele Kräfte, schnelle Abdeckung.", add = listOf("BEREITSCHAFT"), work = -12, cost = 500),
                        o("search", "Ausgebildete Vermisstensuche", "Systematische Suche nach Lehrgang.", needs = "QUAL:Vermisstensuche", risk = -.12, work = -5)),
                ),
                "Jugendlicher gefunden und zu den Eltern gebracht.",
                "Suche bis in die Nacht ohne Ergebnis.",
                listOf(f("FAILURE", "Vermisstensuche aus der Luft", 1, listOf("HELI"), 30, 90, "Die Suche wird aus der Luft fortgesetzt.")),
            ),
            Scenario(
                "Vermisstensuche aus der Luft",
                listOf("„Bergwacht/Feuerwehr: Wir haben eine Person am Hang verloren, bitte Unterstützung aus der Luft.“"),
                listOf(
                    arrival("Hubschrauber über dem Suchgebiet, Nebel zieht auf.", "thermal",
                        o("thermal", "Wärmebildsuche", "Gründliche Rasterflüge.", work = 8, risk = -.06),
                        o("ground", "Bodenkräfte einweisen", "Hubschrauber lotst Hundestaffel.", add = listOf("HUND"), risk = -.1),
                        o("abort", "Wegen Nebel abbrechen", "Besatzung geschützt, Suche verzögert.", work = -10, risk = .1, flag = "nebel")),
                ),
                "Person geortet und gerettet.",
                "Person erst am Folgetag gefunden.",
                listOf(f("FLAG:nebel", "Großflächige Personensuche", 1, listOf("HELI", "HUND", "BEREITSCHAFT"), 45, 160, "Nach dem Nebel wird groß gesucht.")),
            ),
            Scenario(
                "Angemeldete Versammlung",
                listOf("„Versammlungsbehörde: Heute 300 Teilnehmende angemeldet, eine Gegenkundgebung ist angekündigt.“"),
                listOf(
                    call("Zwei Lager, eine Route.", "separate",
                        o("separate", "Routen trennen", "Planung trennt beide Gruppen räumlich.", risk = -.1, work = 5),
                        o("min", "Minimale Präsenz", "Spart Kräfte, wirkt bürgernah – riskant.", risk = .08, rep = 1)),
                    work("Gegendemonstranten blockieren eine Kreuzung.", "talk",
                        o("talk", "Kommunikationsteam spricht an", "Blockade löst sich nach Gesprächen.", work = 10, risk = -.06),
                        o("detour", "Route umleiten", "Versammlung erreicht ihr Ziel über einen Umweg.", work = 6, rep = -1),
                        o("comm", "Geschulte Kommunikation", "Vertrauensvolle Gespräche mit beiden Seiten.", needs = "QUAL:Kommunikation", risk = -.12)),
                ),
                "Versammlungen friedlich verlaufen.",
                "Auseinandersetzungen am Rand.",
                listOf(f("FAILURE", "Sachbeschädigung nach Versammlung", 3, listOf("STREIFE"), 15, 120, "Beschädigte Schaufenster an der Route.")),
            ),
            Scenario(
                "Besucherandrang am Stadion",
                listOf("„Stadionordnungsdienst: Gästefans ohne Karten drängen an den Eingang.“"),
                listOf(
                    arrival("Stimmung gereizt, Pyrotechnik gesehen.", "line",
                        o("line", "Kräftelinie vor dem Eingang", "Klare Präsenz trennt Gruppen.", risk = -.08, work = 8),
                        o("shield", "Mit Schutzausstattung auftreten", "Schutzausstattung reduziert Verletzungsrisiko.", needs = "GEAR:Schutzschild", risk = -.1),
                        o("wait", "Abwarten, Ordner regeln", "Spart Kräfte – Lage kann kippen.", risk = .1, work = -10)),
                ),
                "Spiel ohne Zwischenfälle.",
                "Ausschreitungen am Stadion.",
                listOf(f("FAILURE", "Schlägerei nach Spiel", 1, listOf("BEREITSCHAFT", "STREIFE"), 30, 60, "Nach Abpfiff treffen Gruppen aufeinander.")),
            ),
            Scenario(
                "Erpressungsverdacht",
                listOf("„Ein Restaurantbesitzer zahlt angeblich Schutzgeld. Er hat Angst, selbst anzurufen.“"),
                listOf(
                    arrival("Kripo spricht mit dem Wirt. Er zögert.", "trust",
                        o("trust", "Vertrauen aufbauen, Zeugenschutz anbieten", "Wirt sagt aus.", work = 15, flag = "aussage", rep = 1),
                        o("mek", "Geldübergabe beobachten", "MEK beobachtet die nächste Zahlung.", needs = "TYPE:MEK", flag = "uebergabe", work = 20),
                        o("press", "Druck machen", "Wirt bricht ab und schweigt.", risk = .12, work = -8)),
                ),
                "Ermittlungsverfahren gegen Erpresser.",
                "Wirt zieht seine Aussage zurück.",
                listOf(
                    f("FLAG:uebergabe", "Zugriff bei Geldübergabe", 1, listOf("SEK", "KRIPO"), 30, 120, "Die Übergabe ist bekannt: Spezialkräfte sollen bereitstehen."),
                    f("FLAG:aussage", "Gefährdete Zeugin betreuen", 1, listOf("SEK"), 30, 150, "Der Zeuge erhält Drohungen."),
                ),
            ),
            Scenario(
                "Gefährdete Zeugin betreuen",
                listOf("„Unsere Zeugin im Prozess wurde bedroht und will nicht mehr aus dem Haus.“"),
                listOf(
                    arrival("Spezialkräfte bei der Zeugin. Unbekanntes Auto vor dem Haus.", "move",
                        o("move", "Zeugin in Schutzunterkunft bringen", "Sicherer Ortswechsel.", work = 10, risk = -.1, cost = 800),
                        o("guard", "Vor Ort bewachen", "Bindet Kräfte lange.", work = 25, risk = -.06),
                        o("check", "Auto überprüfen lassen", "Streife kontrolliert das Fahrzeug.", add = listOf("STREIFE"), flag = "auto")),
                ),
                "Zeugin sicher, Prozess kann stattfinden.",
                "Zeugin zieht Aussage zurück.",
                listOf(f("FLAG:auto", "Verdächtiges Fahrzeug", 3, listOf("STREIFE"), 12, 120, "Das Auto ist auf einen Bekannten des Angeklagten zugelassen.")),
            ),
            Scenario(
                "Fahndung nach Hinweislage",
                listOf("„Hinweisgeber: Der gesuchte Mann soll heute bei seiner Freundin übernachten.“"),
                listOf(
                    arrival("MEK bestätigt: Licht in der Wohnung, Fahrzeug vor der Tür.", "wait",
                        o("wait", "Weiter beobachten, Zugriff planen", "SEK wird für den Morgen angefordert.", flag = "zugriff", work = 15),
                        o("now", "Sofort festnehmen", "Schnell, aber gefährlich ohne Spezialkräfte.", risk = .14, work = -12),
                        o("mekq", "Observationsspezialisten", "MEK-Lehrgang zahlt sich aus.", needs = "QUAL:MEK-Observation", risk = -.12)),
                ),
                "Gesuchter lokalisiert.",
                "Gesuchter bemerkt die Beobachtung und flieht.",
                listOf(f("FLAG:zugriff", "Festnahme des Gesuchten", 1, listOf("SEK"), 25, 120, "Zugriff am Morgen mit Spezialkräften.")),
            ),
            Scenario(
                "Unbekannter Gegenstand",
                listOf("„Am Bahnsteig steht ein Rucksack, seit einer halben Stunde, keiner gehört dazu.“"),
                listOf(
                    call("Mögliche Gefahrenlage an Verkehrsknoten.", "cordon",
                        o("cordon", "Bereich räumen", "Bahnverkehr unterbrochen, Pendler verärgert.", risk = -.12, rep = -1),
                        o("check", "Erst Streife prüfen lassen", "Weniger Störung, höheres Risiko.", risk = .08)),
                    arrival("Rucksack enthält Kabel und eine Dose.", "experts",
                        o("experts", "Spezialkräfte zur Prüfung", "Entschärfer prüfen den Inhalt.", add = listOf("SEK"), work = 15, risk = -.1),
                        o("owner", "Besitzer per Kamera suchen", "Videoauswertung zeigt einen Reisenden.", work = 8, flag = "owner")),
                ),
                "Gegenstand harmlos/entschärft, Bahnhof wieder offen.",
                "Stundenlange Sperrung mit großer Kritik.",
            ),
            Scenario(
                "Bedrohung in Wohnhaus",
                listOf("„Mein Ex steht vor der Tür und sagt, er bringt mich um. Er hat ein Messer!“"),
                listOf(
                    call("Akute Bedrohung einer Person.", "stay",
                        o("stay", "Anruferin bleibt in der Leitung", "Leitstelle hält Kontakt bis zum Eintreffen.", risk = -.08),
                        o("flee", "Über Balkon/Nachbarn flüchten lassen", "Anruferin bringt sich in Sicherheit.", risk = -.04, flag = "geflohen")),
                    arrival("Mann im Treppenhaus, hat sich eingeschlossen.", "talk",
                        o("talk", "Ansprechen, Zeit gewinnen", "Mann gibt nach langem Gespräch auf.", work = 18, risk = -.08),
                        o("sek", "SEK übernimmt", "Spezialkräfte beenden die Lage zügig.", needs = "TYPE:SEK", work = -8),
                        o("calm", "Deeskalationsgeschulte Ansprache", "Er legt das Messer ab.", needs = "QUAL:Deeskalation", risk = -.12)),
                ),
                "Mann in Gewahrsam, Frau unverletzt.",
                "Frau verletzt – schwere Kritik.",
                listOf(f("SUCCESS", "Häuslicher Konflikt – Nachsorge", 3, listOf("KRIPO"), 20, 400, "Gewaltschutzverfahren wird eingeleitet.")),
            ),
            Scenario(
                "Zeugenaufruf nach Raub",
                listOf("„Kripo: Nach dem Raub am Kiosk brauchen wir Zeugen und Kamerabilder aus der Straße.“"),
                listOf(
                    arrival("Mehrere Geschäfte mit Kameras.", "cams",
                        o("cams", "Videomaterial sichern", "Täter auf einem Video erkennbar.", work = 12, flag = "video"),
                        o("press", "Öffentlicher Zeugenaufruf", "Viele Hinweise, viel Arbeit.", work = 18, rep = 1, flag = "hinweise"),
                        o("fast", "Nur Pflichtermittlungen", "Schnell abgeschlossen, schwache Beweislage.", work = -10, risk = .1)),
                ),
                "Täter identifiziert.",
                "Ermittlungen ohne Ergebnis eingestellt.",
                listOf(f("FLAG:video", "Fahndung nach Hinweislage", 2, listOf("MEK"), 30, 150, "Der Täter ist bekannt – wo hält er sich auf?", .7)),
            ),
            Scenario(
                "Unwetter: umgestürzter Baum",
                listOf("„Ein Baum liegt quer über der Landstraße, ich habe gerade noch bremsen können!“"),
                listOf(
                    call("Straße blockiert, Dunkelheit.", "fw",
                        o("fw", "Feuerwehr zum Zersägen", "Feuerwehr räumt die Straße.", add = listOf("FEUERWEHR"), risk = -.08),
                        o("close", "Nur absperren", "Sperrung bis morgen – Umwege für Anwohner.", work = -5, rep = -1)),
                    arrival("Weitere Bäume wackeln im Sturm.", "detour",
                        o("detour", "Großräumige Umleitung", "Verkehr sicher umgeleitet.", work = 8, risk = -.06),
                        o("lamp", "Ausleuchten und sichern", "Taschenlampen/Licht machen die Stelle sichtbar.", needs = "GEAR:Taschenlampe", risk = -.08)),
                ),
                "Straße gesichert und geräumt.",
                "Auto fährt in die Unfallstelle.",
                listOf(f("FAILURE", "Verkehrsunfall", 2, listOf("STREIFE"), 20, 60, "In der Dunkelheit ist ein Auto in den Baum gefahren.")),
            ),
            Scenario(
                "Evakuierungsunterstützung",
                listOf("„Feuerwehr-Einsatzleitung: Gasaustritt, wir räumen zwei Straßenzüge. Bitte Unterstützung!“"),
                listOf(
                    call("Viele Anwohner, darunter ein Pflegeheim.", "bepo",
                        o("bepo", "Bereitschaftspolizei bündeln", "Viele Hände für Haustürklingeln.", risk = -.1, cost = 400),
                        o("speaker", "Lautsprecherdurchsagen", "Schneller, erreicht aber nicht alle.", work = -8, risk = .06)),
                    work("Ein älteres Ehepaar weigert sich zu gehen.", "talk",
                        o("talk", "Geduldig überzeugen", "Kostet Zeit, beide gehen freiwillig.", work = 10, rep = 1),
                        o("rtw", "Rettungsdienst begleitet", "Mit medizinischer Begleitung gehen sie mit.", add = listOf("RETTUNG"), risk = -.06),
                        o("leave", "Dokumentieren und weiter", "Spart Zeit – Risiko für die beiden.", work = -6, risk = .1)),
                ),
                "Evakuierung vollständig.",
                "Nicht alle Anwohner erreicht.",
            ),
            Scenario(
                "Nachbarschaftskonflikt",
                listOf("„Der Nachbar hat schon wieder seinen Müll auf mein Grundstück geworfen. Jetzt reicht es!“"),
                listOf(
                    call("Kein Notfall im engeren Sinn.", "queue",
                        o("queue", "Einreihen, später schicken", "Bindet keine Kräfte für dringende Lagen.", time = 60),
                        o("advice", "Telefonisch beraten", "Hinweis auf Schiedsstelle – Fall erledigt sich teilweise.", work = -6, rep = -1)),
                    arrival("Beide Nachbarn schreien sich an.", "mediate",
                        o("mediate", "Vermitteln", "Langes Gespräch, Ruhe für die nächsten Wochen.", work = 8, rep = 1),
                        o("report", "Anzeigen aufnehmen", "Schnell, Konflikt bleibt.", work = -3, flag = "wieder")),
                ),
                "Konflikt entschärft.",
                "Streit eskaliert weiter.",
                listOf(f("FLAG:wieder", "Nachbarschaftskonflikt", 3, listOf("STREIFE"), 10, 120, "Die Nachbarn streiten erneut.", .5)),
            ),
            Scenario(
                "Landungsbrücken: vermisste Reisegruppe",
                listOf("„Reiseleitung: Acht Senioren unserer Gruppe sind nach der Hafenrundfahrt nicht am Bus.“"),
                listOf(
                    call("Touristengebiet, viele Menschen.", "port",
                        o("port", "Hafenbereich absuchen", "Streifen gehen die Anleger ab.", risk = -.04),
                        o("radio", "Durchsagen über Reedereien", "Schiffe und Kioske helfen.", risk = -.08, time = -3)),
                    arrival("Ein Teil der Gruppe sitzt in einem Café, drei fehlen weiter.", "split",
                        o("split", "Zweite Streife für den Rest", "Suche parallelisiert.", add = listOf("STREIFE"), work = -5),
                        o("bus", "Bus wartet, Gruppe ruft an", "Reiseleitung telefoniert die Handys ab.", work = 6, risk = -.04)),
                ),
                "Gruppe vollständig am Bus.",
                "Ein Senior wird erst abends im Krankenhaus gefunden.",
            ),
            Scenario(
                "Bahnhof: Diebstahlserie",
                listOf("„Bundesbahn-Sicherheit: Fünf Taschendiebstähle in einer Stunde, immer am gleichen Bahnsteig.“"),
                listOf(
                    arrival("Kripo und MEK am Bahnhof. Verdächtige Zweiergruppe gesichtet.", "observe",
                        o("observe", "Auf frischer Tat beobachten", "MEK wartet auf die nächste Tat.", work = 12, flag = "tat", risk = -.05),
                        o("control", "Sofort kontrollieren", "Gruppe wird gewarnt, Beute verschwindet.", work = -8, risk = .1),
                        o("cam", "Videoauswertung", "Kamerabilder zeigen die Masche.", work = 6, flag = "video")),
                ),
                "Täter auf frischer Tat gestellt.",
                "Diebe entkommen im Gedränge.",
                listOf(f("FLAG:video", "Zeugenaufruf nach Raub", 2, listOf("KRIPO"), 20, 240, "Fotos der Verdächtigen gehen an die Presse.", .6)),
            ),
            Scenario(
                "Hinweis aus Nachbarlandkreis",
                listOf("„Nachbarbehörde: Ein gesuchtes Fahrzeug ist Richtung eures Gebiets unterwegs.“"),
                listOf(
                    call("Fahrzeug kann jede Minute einfahren.", "net",
                        o("net", "Kontrollpunkte an Einfallstraßen", "Bindet zusätzliche Streifen.", add = listOf("STREIFE"), risk = -.08),
                        o("heli", "Hubschrauber suchen lassen", "Großflächig, teuer.", add = listOf("HELI"), cost = 900, risk = -.12)),
                    arrival("MEK hat das Fahrzeug an einer Tankstelle entdeckt.", "follow",
                        o("follow", "Verdeckt folgen", "Fahrzeug führt zu einem Versteck.", work = 15, flag = "versteck"),
                        o("stop", "Anhalten lassen", "Zügige Kontrolle; Fahrer könnte fliehen.", risk = .08, work = -8)),
                ),
                "Gesuchtes Fahrzeug gestellt.",
                "Fahrzeug entkommt über die Landesgrenze.",
                listOf(f("FLAG:versteck", "Durchsuchung vorbereiten", 1, listOf("SEK", "KRIPO"), 30, 180, "Im Versteck werden weitere Personen vermutet.", .8)),
            ),
            Scenario(
                "Großflächige Personensuche",
                listOf("„Pflegeheim: Ein Bewohner mit Herzproblemen fehlt seit dem Mittag, ohne Medikamente.“"),
                listOf(
                    call("Gesundheitliche Gefahr, großes Gebiet.", "all",
                        o("all", "Volle Suche aus Luft und Boden", "Großer Aufwand, gute Chancen.", cost = 1200, risk = -.1),
                        o("phase", "Erst Nahbereich, dann ausweiten", "Spart Kosten, kostet Zeit.", time = -15, risk = .04)),
                    work("Ein Busfahrer meldet einen verwirrten Fahrgast.", "bus",
                        o("bus", "Hinweis nachgehen", "Streife fährt zur Endhaltestelle.", add = listOf("STREIFE"), risk = -.1, work = -10),
                        o("ignore", "Suche planmäßig fortsetzen", "Der Hinweis bleibt liegen.", risk = .06)),
                ),
                "Bewohner gefunden und versorgt.",
                "Bewohner wird erst nachts gefunden.",
            ),
            Scenario(
                "Schwertransport begleiten",
                listOf("„Spedition: Transformator mit 4,5 Metern Breite, Begleitung ab 22 Uhr angemeldet.“"),
                listOf(
                    arrival("Engstelle an einer Brücke, Falschparker blockieren.", "tow",
                        o("tow", "Abschleppen lassen", "Weg frei, kostet Zeit und Gebühren.", work = 10, cost = 250),
                        o("detour", "Alternativroute", "Längere Fahrt, aber reibungslos.", work = 15, risk = -.04),
                        o("vd", "Verkehrsdienst-Erfahrung", "Lehrgang Verkehrsdienst: elegante Lösung.", needs = "QUAL:Verkehrsdienst", work = -6, risk = -.08)),
                ),
                "Transport planmäßig am Ziel.",
                "Transport bleibt stecken, Straße stundenlang dicht.",
            ),
        )

    private val byId = all.associateBy { it.id }

    fun find(id: String): Scenario? = byId[id]

    /** Stage definition for an incident (scripted or the generic call taking). */
    fun stage(call: IncidentState, id: String): Stage? =
        if (call.scenario.isBlank()) if (id == "call") generic else null
        else find(call.scenario)?.stages?.firstOrNull { it.id == id }
}
