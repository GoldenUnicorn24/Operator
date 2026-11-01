import "./compat";
import { AudioMixer } from "./audio/mixer";
import { BASE_TEMPLATES, COURSES, GEAR, UNIT_TYPES } from "./data/catalog";
import * as engine from "./domain/engine";
import { SHIFT_OPTIONS, timeLabel, planShift as setPlan } from "./domain/clock";
import type {
  GameState,
  Unit,
  Officer,
  Incident,
  Position,
  UnitType,
  IncidentTemplate,
  Vehicle,
} from "./domain/model";
import { regions, getRegion, contains, routeBetween } from "./map/regions";
import { LiveMap } from "./map/view";
import { SaveRepository, parseSave } from "./storage/repository";
import * as editor from "./editor/schema";

const repo = new SaveRepository(localStorage);
const dispatchChoices = new Map<number, string>();
let state: GameState | null = null,
  chosenMode: "career" | "sandbox" = "career",
  selectedTab = "dispatch";
let selected: {
  kind: "unit" | "incident" | "facility";
  id: number | string;
} | null = null;
let selectedUnitId: number | null = null,
  placing: "unit" | "incident" | null = null;
let saveFailure = false,
  focusBlocked = false;
let mixer = new AudioMixer();
const el = <T extends HTMLElement = HTMLElement>(id: string) =>
  document.getElementById(id) as T;
const value = (id: string) => el<HTMLInputElement>(id).value;
const safe = (v: unknown) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (ch) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        ch
      ]!,
  );
const money = (v: number) => Math.round(v).toLocaleString("de-DE") + " €";
const needState = () => {
  if (!state) throw Error("Bitte zuerst ein Spiel beginnen.");
  return state;
};
const activeUnits = () =>
  state!.units.filter((u) => u.regionId === state!.regionId);
const photoForUnit = (u: Unit) =>
  u.type === "SEK"
    ? "images/sek_team.webp"
    : u.type === "MEK"
      ? "images/mek_team.webp"
      : u.vehicle.image;
const imgFor = (o: Officer) =>
  o.photo ?? `images/officer_${String(o.avatar + 1).padStart(2, "0")}.webp`;
const avgSkill = (u: Unit) =>
  Math.round(u.officers.reduce((a, o) => a + o.skill, 0) / u.officers.length);
const statusLabel = (u: Unit) =>
  u.training
    ? "AUSBILDUNG"
    : ({
        ready: "FREI",
        responding: "ANFAHRT",
        onScene: "VOR ORT",
        returning: "RÜCKFAHRT",
        resting: "RUHE",
        offDuty: "DIENSTFREI",
      }[u.status] ?? u.status);
let toastTimer: ReturnType<typeof setTimeout>;
function toast(message: string) {
  el("toast").textContent = message;
  el("toast").hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el("toast").hidden = true), 6000);
}
function save() {
  if (!state) return;
  try {
    repo.save(state);
    saveFailure = false;
  } catch (e) {
    if (!saveFailure)
      toast(
        "Speichern fehlgeschlagen: " +
          (e as Error).message +
          ". Bitte Spielstand exportieren.",
      );
    saveFailure = true;
  }
}
function showModal(html: string) {
  el("modalBody").innerHTML = html;
  el("modal").classList.remove("hidden");
  el<HTMLElement>("modalBody")
    .querySelector<HTMLElement>("button,select,input")
    ?.focus();
}
function closeModal() {
  el("modal").classList.add("hidden");
}
function chooseGameMode(mode: "career" | "sandbox") {
  chosenMode = mode;
  document
    .querySelectorAll<HTMLElement>(".mode-card")
    .forEach((e) => e.classList.toggle("selected", e.dataset.mode === mode));
}
function showWelcome() {
  showModal(
    `<img class="hero" src="images/dispatch_center.webp" alt="Fiktive Leitstelle"><small class="eyebrow">EINSATZLEITSTELLE 110 / DEUTSCHLAND</small><h2>Deine Entscheidungen. Dein Land.</h2><p class="muted">Führe deine Leitstelle durch einen Dienst. Verteile Kräfte, wäge Prioritäten ab und entwickle dein Personal weiter. Alle 16 Landesübersichten sind offline verfügbar.</p><div class="game-modes"><button class="mode-card selected" data-mode="career" onclick="chooseGameMode('career')"><b>Karriere</b><small>Budget, Personalbelastung, Erfahrung und Reputation. Jeder abgeschlossene Dienst zählt.</small></button><button class="mode-card" data-mode="sandbox" onclick="chooseGameMode('sandbox')"><b>Sandbox</b><small>Eigene Regeln, freie Mittel, Einsatzketten und konfigurierbare Alarmhäufigkeit.</small></button></div><button class="primary wide" onclick="startNewGame()">Spielmodus wählen →</button><p class="muted small space-top">Entwicklungsbuild · Kein echter Polizeifunk. Fahrspuren und ETA sind gekennzeichnete Simulationsnäherungen.</p>`,
  );
}
function startNewGame() {
  state = engine.newState(chosenMode);
  mixer = new AudioMixer(state.audio);
  resumeAudio();
  map.fit();
  render();
  showPlanning();
}
function showPlanning() {
  if (state?.stage !== "planning") return;
  const s = state;
  showModal(
    `<img class="hero" src="images/dispatch_center.webp" alt="Schichtvorbereitung"><small class="eyebrow">TAG ${s.day} / ${s.mode === "career" ? "KARRIERE" : "SANDBOX"}</small><h2>Schichtplanung</h2><p class="muted">${activeUnits().filter((u) => engine.available(s, u)).length} freie Einheiten in ${safe(s.region)}. Eine Schicht = ein Managementtag. Ausbildung zählt abgeschlossene Schichten.</p><div class="field-grid"><label>Schichtvorlage<select id="planShift" onchange="planShift(this.value)">${Object.entries(
      SHIFT_OPTIONS,
    )
      .map(
        ([id, p]) =>
          `<option value="${id}" ${id === s.shiftType ? "selected" : ""}>${p.name} · ${p.label}</option>`,
      )
      .join(
        "",
      )}</select></label><label>Beginn (volle Stunde)<select id="planStart" onchange="planStart(Number(this.value))">${Array.from({ length: 24 }, (_, h) => `<option value="${h}" ${h === s.startHour ? "selected" : ""}>${String(h).padStart(2, "0")}:00</option>`).join("")}</select></label><label>Schichtlänge<select id="planDuration" onchange="planDuration(Number(this.value))">${[8, 12, 24].map((h) => `<option value="${h}" ${s.shiftDurationMinutes === h * 60 ? "selected" : ""}>${h} Stunden</option>`).join("")}</select></label><label>Einsatzregion<select id="planRegion" onchange="planRegion(this.value)">${regions.map((r) => `<option value="${r.id}" ${r.id === s.regionId ? "selected" : ""}>${safe(r.name)}</option>`).join("")}</select></label></div><div class="notice">${s.shiftDurationMinutes / 60}h · ${timeLabel(s.startHour * 60)}–${timeLabel(s.startHour * 60 + s.shiftDurationMinutes)}${s.startHour * 60 + s.shiftDurationMinutes >= 1440 ? " Folgetag" : ""}. 12/24h-Dienste wechseln nach je 8h auf ausgeruhte Ablösungsteams. Einsätze bleiben dabei bestehen.</div>${s.mode === "sandbox" ? `<h3>Deine Regeln</h3>${(["budgetLimited", "trainingEnabled", "fatigueEnabled", "customEnabled"] as const).map((k, i) => `<label class="check-row"><input type="checkbox" ${s.rules[k] ? "checked" : ""} onchange="toggleRule('${k}',this.checked)">${["Budgetbegrenzung", "Freiwillige Fortbildungen", "Personalermüdung", "Eigene Vorlagen aktiv"][i]}</label>`).join("")}<label class="check-row"><input type="checkbox" ${s.autoCalls ? "checked" : ""} onchange="toggleAuto(this.checked)">Automatische Notrufe</label><label>Notrufabstand (Spielminuten)<input id="callInterval" type="number" value="${s.rules.callInterval}" min="10" max="240" onchange="setCallInterval(Number(this.value))"></label>` : `<p class="muted small">Dienstgrad: ${engine.rank(s)}. Personal- und Wartungskosten steigen mit den geleisteten Stunden.</p>`}<div class="modal-actions"><button onclick="openPlanningArea('units')">Personal & Dienstplan</button><button onclick="openPlanningArea('editor')">Editoren</button><button onclick="openPlanningArea('dispatch')">Karte ansehen</button><button class="primary" onclick="startShift()">Schicht beginnen</button></div>`,
  );
}
function planShift(id: string) {
  setPlan(needState(), id);
  render();
  showPlanning();
}
function planDuration(hours: number) {
  setPlan(
    needState(),
    hours === 24 ? "full24" : hours === 12 ? "day12" : "early",
    needState().startHour,
  );
  render();
  showPlanning();
}
function planStart(hour: number) {
  const s = needState();
  setPlan(s, s.shiftType, hour);
  render();
  showPlanning();
}
function planRegion(id: string) {
  engine.switchRegion(needState(), id);
  selected = null;
  map.fit();
  render();
  showPlanning();
}
function toggleRule(key: keyof GameState["rules"], enabled: boolean) {
  const s = needState();
  if (s.mode !== "sandbox" || s.stage !== "planning" || key === "callInterval")
    return;
  (s.rules[key] as boolean) = enabled;
  render();
}
function toggleAuto(enabled: boolean) {
  if (state?.stage === "planning") {
    state.autoCalls = enabled;
    save();
  }
}
function setCallInterval(v: number) {
  editor.finite(v, "Notrufabstand", 10, 240);
  needState().rules.callInterval = Math.round(v);
  save();
}
function openPlanningArea(tab: string) {
  closeModal();
  switchTab(tab);
}
function startShift() {
  engine.startShift(needState());
  resumeAudio();
  mixer.play("radio_squelch");
  closeModal();
  switchTab("dispatch");
  render();
}
function togglePause() {
  const s = needState();
  if (s.stage !== "running") return;
  s.paused = !s.paused;
  if (!s.paused && !focusBlocked) resumeAudio();
  render();
}
function setSpeed(n: number) {
  const s = needState();
  if (s.stage === "running" && [1, 4, 8, 16].includes(n)) {
    s.speed = n;
    render();
  }
}
function advanceMinutes(n: number) {
  const s = needState();
  if (s.paused)
    return toast(
      "Die Simulation ist pausiert. Zum Vorspulen zuerst fortsetzen.",
    );
  step(n);
}
function step(n: number) {
  const s = needState();
  const total = s.shiftStats.total,
    solved = s.shiftStats.solved,
    failed = s.shiftStats.failed,
    stage = s.stage;
  engine.advance(s, n);
  if (s.shiftStats.total > total) mixer.play("incoming_call");
  if (s.shiftStats.solved > solved) mixer.play("incident_success");
  if (s.shiftStats.failed > failed) mixer.play("incident_failure");
  render();
  if (stage === "running" && s.stage === "debrief") {
    mixer.play("shift_end");
    showDebrief();
  }
}
function skipShift() {
  const s = needState();
  if (s.stage !== "running") return;
  if (s.incidents.some((i) => ["open", "dispatching"].includes(i.status)))
    return toast("Erst offene Notrufe disponieren oder übergeben.");
  if (s.paused) return toast("Zum Vorspulen die Simulation fortsetzen.");
  while (s.stage === "running") {
    engine.advance(s, 1);
    if (s.incidents.some((i) => ["open", "dispatching"].includes(i.status))) {
      render();
      mixer.play("incoming_call");
      return toast("Vorspulen angehalten: neuer bedienpflichtiger Notruf.");
    }
  }
  render();
  mixer.play("shift_end");
  showDebrief();
}
function endShiftPrompt() {
  const s = needState();
  if (s.stage !== "running") return;
  if (s.elapsed < 30)
    return toast("Vorzeitiger Abschluss erst nach 30 Spielminuten.");
  const active = s.incidents.filter((i) => engine.ACTIVE.includes(i.status));
  const critical = active.filter((i) => i.priority === 1).length;
  if (
    confirm(
      `Schicht vorzeitig beenden? ${active.length} offene Lagen (${critical} kritische P1) werden mit Fahrzeugen und Bearbeitungsstand dokumentiert übergeben.`,
    )
  ) {
    engine.finishShift(s, true);
    render();
    mixer.play("shift_end");
    showDebrief();
  }
}
function showDebrief() {
  if (state?.stage !== "debrief") return;
  const r = state.history[0];
  if (!r) return;
  const rows: [string, string][] = [
    ["Notrufe", String(r.total)],
    ["Erfolgreich / Fehlgeschlagen", `${r.solved} / ${r.failed}`],
    ["Übergeben / davon P1", `${r.transferred} / ${r.criticalHandoffs}`],
    ["SEK-/MEK-Dispositionen", String(r.specialDispatches)],
    ["Interne Personalwechsel", String(r.rotations)],
    [
      "Geleistete Spielstunden",
      `${(r.elapsed / 60).toFixed(1)}h · Gesamt ${r.totalHours.toFixed(1)}h`,
    ],
    ["Einnahmen / Zuweisungen", "+" + money(r.income)],
    ["Gehälter", money(r.wages)],
    ["Wartung", money(r.maintenance)],
    ["Ausgaben gesamt", money(r.expenses)],
    ["Budget", money(r.budget)],
    ["Durchschnittliche Müdigkeit", `${r.fatigue}%`],
    ["Reputation", `${r.reputation}%`],
  ];
  showModal(
    `<img class="hero" src="images/hamburg.webp" alt="Leitstelle bei Nacht"><small class="eyebrow">DIENST ABGESCHLOSSEN / TAG ${r.day}</small><div class="report-title"><h2>Tagesauswertung</h2><strong>${r.durationMinutes / 60}h</strong></div><p class="muted">${safe(r.region)} · ${timeLabel(r.startAbsoluteMinute)}–${timeLabel(r.endAbsoluteMinute)} · ${r.elapsed} Minuten</p>${r.criticalHandoffs ? `<div class="report-note">${r.criticalHandoffs} kritische Lagen an die nächste Dienstgruppe übergeben. Die Einsätze und Kräfte bleiben gespeichert.</div>` : ""}<table class="report-table">${rows.map(([a, b]) => `<tr><td>${a}</td><td>${b}</td></tr>`).join("")}</table><h3>Ausbildung</h3><p class="muted">${r.trainingProgress.map(safe).join("<br>") || "Keine laufenden Lehrgänge."}</p>${r.trainingDone.length ? `<p class="color-mint">Abgeschlossen: ${r.trainingDone.map(safe).join(", ")}</p>` : ""}<div class="modal-actions"><button onclick="viewHistoryAfterDebrief()">Tagesbericht ansehen</button><button class="primary" onclick="nextDay()">Nächsten Tag vorbereiten →</button></div>`,
  );
}
function viewHistoryAfterDebrief() {
  closeModal();
  switchTab("history");
}
function nextDay() {
  engine.nextDay(needState());
  render();
  showPlanning();
}
function selectRegion(id: string) {
  engine.switchRegion(needState(), id);
  selected = null;
  map.fit();
  closeModal();
  switchTab("dispatch");
  render();
}
function switchTab(tab: string) {
  if (!el(tab)) return;
  selectedTab = tab;
  document
    .querySelectorAll<HTMLElement>(".tabs button")
    .forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
  document
    .querySelectorAll("main .page")
    .forEach((p) => p.classList.toggle("active-page", p.id === tab));
  render();
  if (tab === "dispatch") map.update(needState());
}
function newIncident(id?: number) {
  const i = engine.newIncident(needState(), id);
  mixer.play("incoming_call");
  if (i.priority <= 2) mixer.play(i.priority === 1 ? "p1_alarm" : "p2_alarm");
  selected = { kind: "incident", id: i.id };
  render();
}
function showcaseCalls() {
  const s = needState(),
    r = getRegion(s.regionId);
  for (const city of r.cities.slice(0, s.regionId === "BY" ? 2 : 3))
    engine.createIncident(
      s,
      {
        ...BASE_TEMPLATES[0],
        name: "Notruf · " + city.name,
        priority: city === r.cities[0] ? 1 : 2,
        deadlineMinutes: 360,
      },
      city.position,
    );
  mixer.play("incoming_call");
  render();
}
function assign(id: number, uid: string | number) {
  engine.assign(needState(), id, Number(uid || dispatchChoices.get(id)));
  dispatchChoices.delete(id);
  mixer.play("dispatch_ack");
  mixer.play("unit_departure");
  render();
}
function rememberDispatch(id: number, uid: string) {
  dispatchChoices.set(id, uid);
}
function recall(uid: number) {
  engine.recall(needState(), uid);
  mixer.play("radio_squelch");
  render();
}
function decide(id: number, key: string) {
  engine.decide(needState(), id, key);
  mixer.play("dispatch_ack");
  render();
}
function locateUnit(id: number) {
  const u = needState().units.find((u) => u.id === id);
  if (!u) return;
  engine.switchRegion(needState(), u.regionId);
  selected = { kind: "unit", id };
  closeModal();
  switchTab("dispatch");
  map.focus(u.position);
  render();
}
function locateIncident(id: number) {
  const i = needState().incidents.find((i) => i.id === id);
  if (!i) return;
  engine.switchRegion(needState(), i.regionId);
  selected = { kind: "incident", id };
  switchTab("dispatch");
  map.focus(i.position);
  render();
}
function renderMapDetail() {
  if (!state) return;
  const s = state,
    detail = el("mapDetail");
  if (
    detail.contains(document.activeElement) &&
    document.activeElement?.tagName === "SELECT"
  )
    return;
  if (placing) {
    detail.innerHTML = `<h3>Standort wählen</h3><p>Tippe im Bundesland auf den ${placing === "unit" ? "Heimatstandort der Einheit" : "Einsatzort"}. Grenzen werden geprüft.</p><button onclick="cancelPlacement()">Abbrechen</button>`;
    return;
  }
  if (!selected) {
    detail.innerHTML = `<div class="detail-line"><span class="status-line"><i class="status-light"></i>${s.paused ? "PAUSIERT" : "LIVE"} · ${activeUnits().length} Fahrzeuge · ${s.incidents.filter((i) => i.regionId === s.regionId && engine.ACTIVE.includes(i.status)).length} aktive Lagen</span><button ${s.stage !== "running" ? "disabled" : ""} onclick="showcaseCalls()">Verteilte Notrufe auslösen</button></div><small>Pins/Cluster öffnen · Anfahrt = Entfernung × 1,28 / Fahrzeugtempo + Bereitschaft · keine Verkehrsdaten</small>`;
    return;
  }
  if (selected.kind === "incident") {
    const i = s.incidents.find((i) => i.id === Number(selected!.id));
    if (!i) {
      selected = null;
      return;
    }
    const need = engine.remainingNeed(s, i),
      units = s.units.filter(
        (u) =>
          u.regionId === i.regionId &&
          engine.available(s, u) &&
          need.includes(u.type),
      );
    detail.innerHTML = `<div class="detail-line"><h3>P${i.priority} · ${safe(i.name)}</h3><button onclick="clearMapSelection()">Schließen</button></div><p>${safe(i.description)}</p><div class="profile-tags"><span>${{ open: "Offener Notruf", dispatching: "Anfahrt", working: "Bearbeitung", resolved: "Abgeschlossen", failed: "Fehlgeschlagen", handover: "Übergabe" }[i.status]}</span><span>${i.position[1].toFixed(4)}, ${i.position[0].toFixed(4)}</span><span>Frist: ${Math.max(0, Math.ceil(i.deadlineAt - s.absoluteSimMinutes))} Min</span></div><p class="small muted">${
      i.assignedIds
        .map((uid) => {
          const u = s.units.find((u) => u.id === uid)!;
          return (
            safe(u.name) +
            " · " +
            statusLabel(u) +
            (u.route
              ? " · ETA " +
                Math.ceil(
                  Math.max(
                    0,
                    u.route.durationMinutes - u.route.elapsedMinutes,
                  ) + Math.max(0, u.route.departureAt - s.absoluteSimMinutes),
                ) +
                " Min"
              : "")
          );
        })
        .join("<br>") || "Noch keine Kräfte zugewiesen."
    }</p>${["open", "dispatching"].includes(i.status) && s.stage === "running" ? `<p class="small">Benötigt: ${need.map((t) => UNIT_TYPES[t]?.name ?? safe(t)).join(", ") || "Kräfte auf Anfahrt"}</p><div class="btnrow"><select id="mapDispatch" aria-label="Einheit disponieren" onchange="rememberDispatch(${i.id},this.value)"><option value="">Passende freie Einheit wählen</option>${units.map((u) => `<option value="${u.id}" ${dispatchChoices.get(i.id) === String(u.id) ? "selected" : ""}>${safe(u.name)} · ca. ${Math.ceil(routeBetween(u.position, i.position, u.vehicle.speedKmh).durationMinutes + UNIT_TYPES[u.type].dispatchDelayMinutes)} Min</option>`).join("")}</select><button class="primary" onclick="assign(${i.id},document.getElementById('mapDispatch').value)">Entsenden</button></div>` : ""}${!i.decisionId && engine.ACTIVE.includes(i.status) ? `<div class="btnrow">${(i.decisions ?? []).map((d) => `<button onclick="decide(${i.id},'${safe(d.id)}')">${safe(d.label)}<small class="metric-sub">${d.durationDelta >= 0 ? "+" : ""}${d.durationDelta} Min · ${money(d.cost)}</small></button>`).join("")}</div>` : ""}<details><summary>Funk- & Einsatzverlauf</summary><p class="small muted">${i.history.map(safe).join("<br>")}</p></details>`;
  } else if (selected.kind === "unit") {
    const u = s.units.find((u) => u.id === Number(selected!.id));
    if (!u) return;
    detail.innerHTML = `<div class="detail-line"><h3>${safe(u.name)}</h3><span class="status">${statusLabel(u)}</span></div><div class="detail-line"><img class="vehicle-preview" src="${safe(u.vehicle.image)}" alt="Einsatzfahrzeug"><p>${UNIT_TYPES[u.type].name}<br>${u.position[1].toFixed(4)}, ${u.position[0].toFixed(4)}<br>${u.route ? `Simulations-ETA ${Math.ceil(Math.max(0, u.route.durationMinutes - u.route.elapsedMinutes) + Math.max(0, u.route.departureAt - s.absoluteSimMinutes))} Min` : "Am Standort"} · Zustand ${Math.round(u.vehicle.condition)}%</p></div><div class="btnrow"><button onclick="showUnitDetails(${u.id})">Personal / Dienstplan</button>${u.incidentId ? `<button onclick="recall(${u.id})">Zurückrufen</button>` : ""}<button onclick="clearMapSelection()">Schließen</button></div>`;
  } else {
    const f = s.facilities.find((f) => f.id === selected!.id);
    if (!f) return;
    detail.innerHTML = `<h3>${safe(f.name)}</h3><p class="muted">Fiktiver Spielstandort · Ausbaustufe ${f.level}/10 · ${s.units.filter((u) => u.facilityId === f.id).length} zugeordnete Fahrzeuge</p><button onclick="upgradeFacility('${safe(f.id)}')">Wache ausbauen · ${money(f.level * 12000)}</button>`;
  }
}
function clearMapSelection() {
  selected = null;
  renderMapDetail();
}
function upgradeFacility(id: string) {
  const s = needState(),
    f = s.facilities.find((f) => f.id === id);
  if (!f || s.stage !== "planning") throw Error("Ausbau nur in der Planung.");
  if (f.level >= 10) throw Error("Maximale Ausbaustufe erreicht.");
  engine.spend(s, f.level * 12000);
  f.level++;
  for (const u of s.units.filter((u) => u.facilityId === id)) {
    u.vehicle.condition = Math.min(100, u.vehicle.condition + 10);
    for (const o of u.officers) o.energy = Math.min(100, o.energy + 10);
  }
  engine.emit(s, f.name + ": Ausbau verbessert Erholung und Fahrzeugzustand.");
  render();
}
function placeOnMap(kind: "unit" | "incident") {
  placing = kind;
  selected = null;
  map.setPlacement(true);
  switchTab("dispatch");
  map.fit();
  toast("Im Bundesland auf den gewünschten Ort tippen.");
}
function cancelPlacement() {
  placing = null;
  map.setPlacement(false);
  renderMapDetail();
}
const map = new LiveMap(
  el("liveMap"),
  (kind, id) => {
    selected = { kind, id };
    renderMapDetail();
  },
  (p) => {
    if (!state) return;
    if (!contains(state.regionId, p))
      return toast("Dieser Punkt liegt außerhalb des Bundeslandes.");
    const kind = placing ?? "incident";
    el<HTMLInputElement>(kind === "unit" ? "uLat" : "eLat").value =
      p[1].toFixed(5);
    el<HTMLInputElement>(kind === "unit" ? "uLon" : "eLon").value =
      p[0].toFixed(5);
    placing = null;
    map.setPlacement(false);
    switchTab("editor");
    toast("Geografischen Standort übernommen.");
  },
);
function renderIncidents() {
  const s = needState();
  if (
    el("incidentList").contains(document.activeElement) &&
    document.activeElement?.tagName === "SELECT"
  )
    return;
  const active = s.incidents.filter(
    (i) => i.regionId === s.regionId && engine.ACTIVE.includes(i.status),
  );
  el("callCounter").textContent = active.length + " aktiv";
  el("incidentList").innerHTML =
    active
      .map((i) => {
        const need = engine.remainingNeed(s, i);
        return `<article class="incident p${i.priority}"><div class="incident-header"><strong>${safe(i.name)}</strong><span class="pill">P${i.priority} · ${i.status === "working" ? "VOR ORT" : i.status === "dispatching" ? "ANFAHRT" : "NOTRUF"}</span></div><small>${safe(i.description)}</small><small class="color-amber">${i.status === "working" ? `Bearbeitung: ${Math.max(0, i.completeAt! - s.absoluteSimMinutes)} Min` : `Frist: ${Math.max(0, i.deadlineAt - s.absoluteSimMinutes)} Min · Benötigt: ${need.join(", ") || "alle Kräfte unterwegs"}`}</small><div class="btnrow"><button onclick="locateIncident(${i.id})">Auf Karte disponieren →</button></div></article>`;
      })
      .join("") ||
    '<div class="notice">Keine offenen Notrufe in diesem Land. Zeit läuft weiter; eigene Vorlagen können im Editor ausgelöst werden.</div>';
  el("quickUnits").innerHTML = activeUnits()
    .slice(0, 10)
    .map(
      (u) =>
        `<div class="unit-short"><button onclick="locateUnit(${u.id})">${safe(u.name)}</button><span class="status ${u.incidentId ? "busy" : ""}">${statusLabel(u)}</span></div>`,
    )
    .join("");
}
function renderUnits() {
  el("unitList").innerHTML =
    `<p class="muted">${activeUnits().length} Fahrzeuge · Dienstplan mit bis zu drei Teams pro Fahrzeug. Abstrakte Aufgabenprofile: SEK besondere Gefahrenlagen, MEK Observation, Kripo Ermittlungen.</p><div class="people-grid">${activeUnits()
      .map(
        (u) =>
          `<div class="unit-card"><img loading="lazy" src="${safe(photoForUnit(u))}" alt="${UNIT_TYPES[u.type].name}"><div class="body"><b>${safe(u.name)}</b><p class="muted small">${UNIT_TYPES[u.type].name} · ${u.officers.length} Beamte<br>Kompetenz ${avgSkill(u)} · Fahrzeug ${Math.round(u.vehicle.condition)}%</p><span class="status ${u.incidentId ? "busy" : ""}">${statusLabel(u)}</span><div class="space-top"><button onclick="showUnitDetails(${u.id})">Personal / Ausrüstung →</button></div></div></div>`,
      )
      .join("")}</div>`;
}
function showUnitDetails(id: number) {
  const s = needState(),
    u = s.units.find((u) => u.id === id);
  if (!u) return;
  selectedUnitId = id;
  const courses = COURSES.filter((c) => !c.forType || c.forType === u.type);
  showModal(
    `<div class="modal-actions"><button onclick="backFromProfile()">Schließen</button><button onclick="locateUnit(${id})">Auf Karte</button></div><img class="hero" src="${safe(photoForUnit(u))}" alt="Fiktive Einheit"><h2>${safe(u.name)}</h2><p class="muted">${UNIT_TYPES[u.type].name} · ${statusLabel(u)} · Ø Kompetenz ${avgSkill(u)}</p><div class="profile-tags">${UNIT_TYPES[u.type].capabilities.map((c) => `<span>${safe(c)}</span>`).join("")}</div><h3>Aktuelles Dienstteam</h3><div class="roster">${u.officers.map((o, i) => `<button class="officer-tile" onclick="showOfficerDetails(${id},${i})"><img src="${imgFor(o)}" alt="Fiktives Porträt ${safe(o.name)}"><small>${safe(o.name)} · ${Math.round(o.energy)}% Ausdauer</small></button>`).join("")}</div><h3>Dienstplan & Personalwechsel</h3><p class="muted">${u.reserveOfficers.length} Ablösungsteams · ${u.rosterIndex} Wechsel im aktuellen Dienst. Nach 8h übernehmen ausgeruhte Kräfte; mindestens 8h Ruhe vor erneutem Einsatz.</p><label class="check-row"><input type="checkbox" ${u.shiftDuty ? "checked" : ""} ${s.stage !== "planning" ? "disabled" : ""} onchange="setDuty(${id},this.checked)">In kommender Schicht im Dienst</label><div class="btnrow"><button ${u.incidentId || u.training ? "disabled" : ""} onclick="manualRotation(${id})">Ablösung übernehmen</button><button ${s.stage !== "planning" || u.reserveOfficers.length >= 2 ? "disabled" : ""} onclick="hireRelief(${id})">Ablösung einstellen · ${money(u.officers.length * 1900)}</button><button ${s.stage !== "running" || u.incidentId ? "disabled" : ""} onclick="restUnit(${id})">4h Ruhe planen</button></div><h3>Fortbildungen · freiwillig</h3>${u.training ? `<div class="notice">${safe(COURSES.find((c) => c.id === u.training!.courseId)?.name ?? u.training.courseId)} · noch ${u.training.remainingDays} abgeschlossene Spieltage.</div>` : ""}${courses.map((c) => `<div class="listrow"><div><b>${safe(c.name)}</b><div class="small muted">${c.days} Tage · ${money(c.cost)} · +${c.skill} Kompetenz<br>${safe(c.desc)}</div></div><button ${u.training || u.incidentId || s.stage !== "planning" || !s.rules.trainingEnabled ? "disabled" : ""} onclick="startTraining(${id},'${c.id}')">Beginnen</button></div>`).join("")}`,
  );
}
function setDuty(id: number, v: boolean) {
  const s = needState(),
    u = s.units.find((u) => u.id === id);
  if (s.stage !== "planning" || !u || u.incidentId) return;
  u.shiftDuty = v;
  u.status = v ? "ready" : "offDuty";
  render();
}
function hireRelief(id: number) {
  const s = needState(),
    u = s.units.find((u) => u.id === id);
  if (!u || s.stage !== "planning" || u.reserveOfficers.length >= 2)
    throw Error("Ablösung derzeit nicht einstellbar.");
  engine.spend(s, u.officers.length * 1900);
  u.reserveOfficers.push(
    u.officers.map((o, j) => engine.officer(++s.seq, (o.avatar + j + 1) % 8)),
  );
  render();
  showUnitDetails(id);
}
function manualRotation(id: number) {
  const s = needState(),
    u = s.units.find((u) => u.id === id);
  if (!u || u.incidentId || u.training)
    throw Error(
      "Personalwechsel während einer Lage erfolgt automatisch nach Dienstzeit.",
    );
  const index = u.reserveOfficers.findIndex((t) =>
    t.every((o) => o.restMinutes >= 480 && o.energy >= 50),
  );
  if (index < 0) throw Error("Keine ausgeruhte Ablösung verfügbar.");
  const previous = u.officers;
  u.officers = u.reserveOfficers[index];
  u.reserveOfficers[index] = previous;
  u.rosterIndex++;
  u.officers.forEach((o) => {
    o.dutyMinutes = 0;
    o.restMinutes = 0;
  });
  s.shiftStats.rotations++;
  render();
  showUnitDetails(id);
}
function restUnit(id: number) {
  const s = needState(),
    u = s.units.find((u) => u.id === id);
  if (!u || u.incidentId || s.stage !== "running")
    throw Error("Ruhe nur für freie Kräfte.");
  u.restUntil = s.absoluteSimMinutes + 240;
  u.status = "resting";
  render();
  showUnitDetails(id);
}
function backFromProfile() {
  closeModal();
  render();
}
function showOfficerDetails(id: number, index: number) {
  const s = needState(),
    u = s.units.find((u) => u.id === id),
    o = u?.officers[index];
  if (!u || !o) return;
  showModal(
    `<div class="modal-actions"><button onclick="showUnitDetails(${id})">← Einheit</button><button onclick="backFromProfile()">Schließen</button></div><h2>Beamtenprofil</h2><div class="person-detail"><img src="${imgFor(o)}" alt="Fiktives Porträt ${safe(o.name)}"><div><b>${safe(o.name)}</b><p class="muted">${safe(u.name)}<br>${o.xp >= 50 ? "Polizeiobermeister/in" : "Polizeimeister/in"} · fiktiv</p><div class="summary-row"><span>Kompetenz</span><strong>${o.skill}/100</strong></div><div class="summary-row"><span>Erfahrung</span><strong>${o.xp} XP</strong></div><div class="summary-row"><span>Ausdauer</span><strong>${Math.round(o.energy)}%</strong></div><div class="summary-row"><span>Dienst / Ruhe</span><strong>${Math.floor(o.dutyMinutes / 60)}h / ${Math.floor(o.restMinutes / 60)}h</strong></div></div></div><p class="muted">Lehrgänge: ${o.qualifications.map((k) => COURSES.find((c) => c.id === k)?.name ?? safe(k)).join(", ") || "Noch keine abgeschlossene Fortbildung"}</p><h3>Individuelles Porträt</h3><div class="avatar-chooser">${Array.from({ length: 8 }, (_, i) => `<button onclick="changeOfficerPortrait(${id},${index},${i})" aria-label="Porträt ${i + 1}"><img class="${o.avatar === i && !o.photo ? "selected" : ""}" src="images/officer_${String(i + 1).padStart(2, "0")}.webp" alt="Fiktives Porträt ${i + 1}"></button>`).join("")}</div><label>Eigenes Bild (PNG/JPEG/WebP, max. 3 MB)<input type="file" accept="image/png,image/jpeg,image/webp" onchange="uploadOfficerPortrait(event,${id},${index})"></label><h3>Inventar & Ausrüstung · ${o.equipped.length}/4</h3><p class="muted small">Eigene Gegenstände sind personenbezogen. Ablegen erhält den Besitz; kein doppelter Kauf. Schutz und Waffen bleiben abstrakte Kompetenzwerte.</p><div class="equipment-grid">${GEAR.map((g) => `<div class="equipment"><div class="item-pic"><img class="gear-image" src="${g.image}" alt="${safe(g.name)}"></div><b>${g.name}</b><small>${safe(g.desc)} · Bonus +${g.bonus}</small><button ${u.incidentId || s.stage === "debrief" ? "disabled" : ""} class="${o.equipped.includes(g.id) ? "success" : ""}" onclick="buyOrToggleGear(${id},${index},'${g.id}')">${o.equipped.includes(g.id) ? "✓ Ablegen" : o.owned.includes(g.id) ? "Anlegen" : money(g.cost) + " kaufen"}</button></div>`).join("")}</div>`,
  );
}
function buyOrToggleGear(id: number, index: number, gearId: string) {
  engine.toggleGear(needState(), id, index, gearId);
  mixer.play("dispatch_ack");
  render();
  showOfficerDetails(id, index);
}
function changeOfficerPortrait(id: number, index: number, avatar: number) {
  const o = needState().units.find((u) => u.id === id)?.officers[index];
  if (!o || !Number.isInteger(avatar) || avatar < 0 || avatar > 7) return;
  o.avatar = avatar;
  o.photo = null;
  render();
  showOfficerDetails(id, index);
}
async function uploadOfficerPortrait(event: Event, id: number, index: number) {
  const file = (event.target as HTMLInputElement).files?.[0],
    o = needState().units.find((u) => u.id === id)?.officers[index];
  if (!file || !o) return;
  if (
    !["image/png", "image/jpeg", "image/webp"].includes(file.type) ||
    file.size > 3 * 1024 * 1024
  )
    throw Error("Bitte PNG/JPEG/WebP bis 3 MB wählen.");
  const raw = await new Promise<string>((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result));
    r.onerror = () => reject(Error("Bild konnte nicht gelesen werden."));
    r.readAsDataURL(file);
  });
  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const pic = new Image();
    pic.onload = () => resolve(pic);
    pic.onerror = () => reject(Error("Ungültiges Bild."));
    pic.src = raw;
  });
  if (img.width * img.height > 50000000) throw Error("Bildauflösung zu groß.");
  const canvas = document.createElement("canvas");
  canvas.width = 192;
  canvas.height = 192;
  const side = Math.min(img.width, img.height);
  canvas
    .getContext("2d")!
    .drawImage(
      img,
      (img.width - side) / 2,
      (img.height - side) / 2,
      side,
      side,
      0,
      0,
      192,
      192,
    );
  o.photo = canvas.toDataURL("image/jpeg", 0.78);
  render();
  showOfficerDetails(id, index);
}
function startTraining(id: number, courseId: string) {
  engine.startTraining(needState(), id, courseId);
  render();
  showUnitDetails(id);
}
function renderTraining() {
  const s = needState();
  el("trainingList").innerHTML =
    `${!s.rules.trainingEnabled ? '<div class="notice">Fortbildungen in den Sandbox-Regeln deaktiviert.</div>' : ""}${activeUnits()
      .map(
        (u) =>
          `<div class="listrow"><div><b>${safe(u.name)}</b><div class="small muted">${u.type} · Kompetenz ${avgSkill(u)} · ${u.training ? `${safe(COURSES.find((c) => c.id === u.training!.courseId)?.name ?? u.training.courseId)} · noch ${u.training.remainingDays} Tage` : "Keine laufende Ausbildung"}</div></div><button onclick="showUnitDetails(${u.id})">Kurse</button></div>`,
      )
      .join("")}`;
}
function saveIncidentTemplate() {
  const s = needState();
  const lat = value("eLat"),
    lon = value("eLon"),
    extra = value("eExtra"),
    follow = value("eFollow").trim();
  const tpl: IncidentTemplate = {
    id: 0,
    name: value("eName").trim(),
    priority: Number(value("ePriority")),
    needs: [value("eType"), ...(extra ? [extra] : [])],
    duration: Number(value("eDuration")),
    description: value("eDescription"),
    deadlineMinutes: Number(value("eDeadline")),
    reward: Number(value("eReward")),
    regionId: s.regionId,
    followUp: follow ? { name: follow, priority: 2, type: "STREIFE" } : null,
  };
  if (lat || lon)
    tpl.position = editor.position([Number(lon), Number(lat)], s.regionId);
  editor.saveTemplate(s, tpl);
  engine.emit(s, "Eigene Einsatzvorlage: " + tpl.name);
  render();
  toast("Einsatzvorlage gespeichert.");
}
function renderTemplates() {
  const s = needState();
  el("templateList").innerHTML =
    `<p class="muted small">${s.templates.length} datengetriebene Vorlagen · eigene Inhalte ${s.rules.customEnabled ? "aktiv" : "deaktiviert"}</p>${s.templates.map((t) => `<div class="listrow"><div><b>${safe(t.name)}</b><div class="small muted">P${t.priority} · ${t.needs.join(" + ")}${t.regionId ? " · " + getRegion(t.regionId).name : ""}${t.followUp || t.chain ? " · Folgeereignis" : ""}</div></div><div><button onclick="loadTemplateEditor(${t.id})">Bearbeiten</button><button ${s.stage !== "running" || (t.regionId && t.regionId !== s.regionId) ? "disabled" : ""} onclick="newIncident(${t.id})">Auslösen</button></div></div>`).join("")}`;
  const previous = value("editTemplate");
  el("editTemplate").innerHTML = s.templates
    .map(
      (t) =>
        `<option value="${t.id}" ${String(t.id) === previous ? "selected" : ""}>${safe(t.name)}</option>`,
    )
    .join("");
  if (!value("templateJson")) loadTemplateEditor(s.templates[0].id, false);
}
function loadTemplateEditor(id: number, scroll = true) {
  const t = needState().templates.find((t) => t.id === id);
  if (!t) return;
  el<HTMLSelectElement>("editTemplate").value = String(id);
  el<HTMLTextAreaElement>("templateJson").value = JSON.stringify(t, null, 2);
  if (scroll)
    el("templateJson").scrollIntoView({ block: "center", behavior: "smooth" });
}
function saveAdvancedTemplate() {
  const id = Number(value("editTemplate"));
  editor.saveTemplate(needState(), JSON.parse(value("templateJson")), id);
  render();
  toast("Vorlage mit Referenzprüfung gespeichert.");
}
function duplicateTemplate() {
  const t = editor.saveTemplate(needState(), JSON.parse(value("templateJson")));
  render();
  loadTemplateEditor(t.id);
  toast("Neue Vorlage gespeichert.");
}
function saveUnit() {
  const s = needState();
  const p: Position =
    value("uLat") && value("uLon")
      ? [Number(value("uLon")), Number(value("uLat"))]
      : getRegion(s.regionId).cities[0].position;
  const u = editor.createUnit(s, {
    name: value("uName").trim(),
    type: value("uType") as UnitType,
    crew: Number(value("uCrew")),
    position: p,
    avatar: Number(value("uPortrait")),
  });
  engine.emit(
    s,
    "Einheit mit Fahrzeug und zwei Ablösungsteams eingestellt: " + u.name,
  );
  render();
  toast("Einheit erstellt.");
}
function renderVehicles() {
  const s = needState(),
    units = activeUnits();
  if (!selectedUnitId || !units.some((u) => u.id === selectedUnitId))
    selectedUnitId = units[0]?.id ?? null;
  const u = units.find((u) => u.id === selectedUnitId);
  if (!u) return;
  el("vehicleEditor").innerHTML =
    `<label>Einheit / Fahrzeug<select id="vehicleUnit" onchange="selectVehicleUnit(Number(this.value))">${units.map((x) => `<option value="${x.id}" ${x.id === u.id ? "selected" : ""}>${safe(x.name)}</option>`).join("")}</select></label><img class="vehicle-preview" src="${safe(u.vehicle.image)}" alt="Fahrzeug"><div class="field-grid"><label>Funkrufname<input id="vCallsign" value="${safe(u.name)}" maxlength="90"></label><label>Organisation<select id="vType">${engine.unitTypes.map((t) => `<option value="${t}" ${t === u.type ? "selected" : ""}>${UNIT_TYPES[t].name}</option>`).join("")}</select></label><label>Personal pro Team (ergänzen)<input id="vCrew" type="number" min="${u.officers.length}" max="8" value="${u.officers.length}"></label><label>Fahrzeugname<input id="vName" value="${safe(u.vehicle.name)}" maxlength="90"></label><label>Sitzplätze<input id="vCapacity" type="number" min="1" max="8" value="${u.vehicle.capacity}"></label><label>Fahrtempo (km/h, Simulation)<input id="vSpeed" type="number" min="20" max="250" value="${u.vehicle.speedKmh}"></label><label>Heimatwache<select id="vFacility">${s.facilities
      .filter((f) => f.regionId === s.regionId)
      .map(
        (f) =>
          `<option value="${f.id}" ${u.facilityId === f.id ? "selected" : ""}>${safe(f.name)}</option>`,
      )
      .join(
        "",
      )}</select></label><label>Neufahrzeugwert (€)<input id="vCost" type="number" min="0" max="500000" value="${u.vehicle.cost}"></label><label>Heimatstandort Breitengrad<input id="vLat" type="number" step="0.00001" value="${u.basePosition[1]}"></label><label>Heimatstandort Längengrad<input id="vLon" type="number" step="0.00001" value="${u.basePosition[0]}"></label><label>Fahrzeugbild<select id="vImage">${Object.values(
      UNIT_TYPES,
    )
      .map(
        (r) =>
          `<option value="${r.image}" ${r.image === u.vehicle.image ? "selected" : ""}>${r.name}</option>`,
      )
      .join(
        "",
      )}</select></label></div><p class="muted small">Zustand ${u.vehicle.condition.toFixed(1)}% · Beschaffung bucht die positive Wertdifferenz; Wartung stellt Zustand wieder her.</p><div class="btnrow"><button ${s.stage !== "planning" || u.incidentId || u.route ? "disabled" : ""} onclick="saveVehicle(${u.id})">Einheit/Fahrzeug speichern</button><button ${s.stage !== "planning" || u.incidentId || u.route ? "disabled" : ""} onclick="maintainVehicle(${u.id})">Warten · ${money(Math.ceil((100 - u.vehicle.condition) * 25))}</button><button onclick="showUnitDetails(${u.id})">Dienstplan</button></div>`;
}
function selectVehicleUnit(id: number) {
  selectedUnitId = id;
  renderVehicles();
}
function saveVehicle(id: number) {
  const s = needState(),
    u = s.units.find((u) => u.id === id)!;
  const name = editor.text(value("vCallsign"), "Funkrufname", 90),
    p = editor.position(
      [Number(value("vLon")), Number(value("vLat"))],
      s.regionId,
    ),
    facility = s.facilities.find(
      (f) => f.id === value("vFacility") && f.regionId === s.regionId,
    );
  if (!facility) throw Error("Wache unbekannt.");
  const candidate = editor.validateVehicle({
    ...u.vehicle,
    name: value("vName"),
    capacity: Number(value("vCapacity")),
    speedKmh: Number(value("vSpeed")),
    cost: Number(value("vCost")),
    image: value("vImage"),
  });
  const crew = Number(value("vCrew")),
    type = value("vType") as UnitType;
  if (
    !Number.isInteger(crew) ||
    crew < u.officers.length ||
    crew > candidate.capacity ||
    !engine.unitTypes.includes(type)
  )
    throw Error("Besatzung/Organisation ungültig.");
  const cost =
    Math.max(0, candidate.cost - u.vehicle.cost) +
    (crew - u.officers.length) * 1900 * (1 + u.reserveOfficers.length);
  if (s.rules.budgetLimited && s.budget < cost)
    throw Error("Budget reicht für Fahrzeug und Personal nicht.");
  editor.editVehicle(s, id, candidate);
  editor.editUnit(s, id, { name, type, crew, avatar: u.officers[0].avatar });
  u.basePosition = p;
  u.position = [...p];
  u.facilityId = facility.id;
  render();
  toast("Einheit, Heimatstandort und Fahrzeug gespeichert.");
}
function maintainVehicle(id: number) {
  engine.maintainVehicle(needState(), id);
  render();
  toast("Fahrzeug gewartet.");
}
function downloadJson(text: string, name: string) {
  const bridge = (window as any).AndroidBridge;
  if (bridge?.saveJson) {
    bridge.saveJson(text);
    return;
  }
  const url = URL.createObjectURL(
    new Blob([text], { type: "application/json" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 15000);
}
function exportSave() {
  downloadJson(
    JSON.stringify(needState(), null, 2),
    `EL110-Tag${state!.day}.json`,
  );
}
function exportContentFile() {
  downloadJson(editor.exportContent(needState()), "EL110-Vorlagen.json");
}
async function readFile(event: Event, max: number) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0];
  input.value = "";
  if (!file) return null;
  if (file.size > max) throw Error("Datei zu groß.");
  return new Promise<string>((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result));
    r.onerror = () => reject(Error("Datei konnte nicht gelesen werden."));
    r.readAsText(file);
  });
}
async function importSave(event: Event) {
  const raw = await readFile(event, 15 * 1024 * 1024);
  if (!raw) return;
  const next = repo.import(raw);
  state = next;
  state.paused = true;
  mixer.destroy();
  mixer = new AudioMixer(state.audio);
  selected = null;
  map.fit();
  render();
  if (state.stage === "debrief") showDebrief();
  else if (state.stage === "planning") showPlanning();
  toast("Spielstand validiert und importiert.");
}
async function importContentFile(event: Event) {
  const raw = await readFile(event, 1000000);
  if (raw) {
    const count = editor.importContent(needState(), raw);
    render();
    toast(`${count} Vorlagen importiert.`);
  }
}
function saveSlot() {
  const name = prompt("Slot 1, 2 oder 3 speichern?", "1");
  if (!name) return;
  if (!["1", "2", "3"].includes(name))
    throw Error("Bitte Slot 1, 2 oder 3 wählen.");
  repo.save(needState(), name);
  toast(`Slot ${name} gespeichert.`);
}
function loadSlot() {
  const name = prompt("Slot 1, 2 oder 3 laden?", "1");
  if (!name) return;
  if (!["1", "2", "3"].includes(name))
    throw Error("Bitte Slot 1, 2 oder 3 wählen.");
  const next = repo.load(name);
  if (!next) throw Error("Slot ist leer.");
  state = next;
  state.paused = true;
  selected = null;
  map.fit();
  render();
  if (state.stage === "planning") showPlanning();
  else if (state.stage === "debrief") showDebrief();
}
function resetGame() {
  if (
    !confirm(
      "Aktuelles Spiel durch ein neues ersetzen? Exportiere es zuvor oder speichere einen Slot.",
    )
  )
    return;
  repo.clear();
  state = null;
  chosenMode = "career";
  showWelcome();
}
function renderHistory() {
  const s = needState();
  el("careerPanel").innerHTML =
    `<div class="summary-row"><span>${s.mode === "career" ? "Karriere" : "Sandbox"} · ${engine.rank(s)}</span><strong>Tag ${s.day}</strong></div><div class="summary-row"><span>Abgeschlossene Schichten / Stunden</span><strong>${s.completedShifts} / ${s.totalHours.toFixed(1)}h</strong></div><div class="summary-row"><span>Budget / Reputation</span><strong>${money(s.budget)} / ${s.reputation}%</strong></div><p class="muted small">Beschaffung Luftunterstützung: ${engine.recruitmentUnlocked(s, "HELI") ? "freigeschaltet" : "ab 3 Schichten und 65% Reputation"}.</p><div class="timeline">${s.history.map((r) => `<article><strong>Tag ${r.day} · ${safe(r.region)} · ${r.durationMinutes / 60}h</strong><small>${r.solved} Erfolge · ${r.failed} Fehlschläge · ${r.transferred} Übergaben · ${r.rotations} Personalwechsel<br>Budget ${money(r.budget)} · Gehälter ${money(r.wages)}</small></article>`).join("") || '<p class="muted">Noch kein abgeschlossener Dienst.</p>'}</div>`;
  el("log").innerHTML = s.logs
    .map((l) => `<div class="logitem">${safe(l)}</div>`)
    .join("");
}
function renderAudio() {
  if (el("audioSettings").contains(document.activeElement)) return;
  const s = needState();
  el("audioSettings").innerHTML =
    `<div class="mixer-grid">${(["master", "radio", "sfx", "ambient"] as const).map((k, i) => `<label>${["Gesamt", "Funk", "Signale & Fahrzeuge", "Leitstellenambiente"][i]}<input type="range" min="0" max="1" step="0.05" value="${s.audio[k]}" oninput="setAudio('${k}',Number(this.value))"></label>`).join("")}</div><label class="check-row"><input type="checkbox" ${s.audio.mute ? "checked" : ""} onchange="setAudio('mute',this.checked)">Stumm</label><div class="btnrow"><button onclick="testSound()">Funkprobe</button><button onclick="testAmbient()">Ambiente-Probe</button></div>`;
}
function setAudio(channel: keyof GameState["audio"], n: number | boolean) {
  mixer.set(channel, n);
  needState().audio = mixer.getSettings();
  save();
}
function resumeAudio() {
  if (!focusBlocked) mixer.resume();
}
function testSound() {
  resumeAudio();
  mixer.play("radio_squelch");
  mixer.play("dispatch_ack");
}
function testAmbient() {
  resumeAudio();
  mixer.play("ambient_dispatch");
}
function render() {
  if (!state) return;
  const s = state;
  el("topTime").textContent =
    `TAG ${s.day} · ${timeLabel(s.absoluteSimMinutes)}`;
  el("topMode").textContent = s.mode === "career" ? "KARRIERE" : "SANDBOX";
  el("statShift").textContent =
    s.stage === "planning"
      ? "Planung"
      : s.stage === "debrief"
        ? "Schichtende"
        : `${s.shiftDurationMinutes / 60}h Dienst`;
  el("statIncidents").textContent = String(
    s.incidents.filter((i) => engine.ACTIVE.includes(i.status)).length,
  );
  el("statUnits").textContent =
    activeUnits().filter((u) => engine.available(s, u)).length +
    "/" +
    activeUnits().length;
  el("statBudget").textContent = money(s.budget);
  el("statRep").textContent = s.reputation + "%";
  el("dispatchRegion").textContent = "Leitstelle " + s.region;
  el("mapTitle").textContent = s.region;
  el("shiftName").textContent =
    `${s.shiftDurationMinutes / 60}h · ${SHIFT_OPTIONS[s.shiftType].name}`;
  el("shiftClock").textContent = timeLabel(
    s.stage === "planning" ? s.startHour * 60 : s.absoluteSimMinutes,
  );
  el("shiftProgress").style.width =
    Math.min(100, (100 * s.shiftElapsedMinutes) / s.shiftDurationMinutes) + "%";
  el("shiftDescription").textContent =
    `${s.elapsed} / ${s.shiftDurationMinutes} Minuten · ${timeLabel(s.startHour * 60)}–${timeLabel(s.startHour * 60 + s.shiftDurationMinutes)}${s.startHour * 60 + s.shiftDurationMinutes >= 1440 ? " (+1 Tag)" : ""}`;
  el("pauseButton").textContent = s.paused ? "▶ Weiter" : "⏸ Pause";
  document
    .querySelectorAll<HTMLElement>(".speed")
    .forEach((b) =>
      b.classList.toggle("active", Number(b.dataset.speed) === s.speed),
    );
  el("shiftBanner").innerHTML =
    s.stage === "planning"
      ? '<div class="notice">Schichtplanung · <button onclick="showPlanning()">8/12/24h-Schicht planen →</button></div>'
      : s.stage === "debrief"
        ? '<div class="notice">Dienst abgeschlossen · <button onclick="showDebrief()">Tagesauswertung öffnen →</button></div>'
        : "";
  if (el("liveRegion").innerHTML === "")
    el("liveRegion").innerHTML = regions
      .map((r) => `<option value="${r.id}">${safe(r.name)}</option>`)
      .join("");
  el<HTMLSelectElement>("liveRegion").value = s.regionId;
  el("regionGrid").innerHTML = regions
    .map(
      (r) =>
        `<button class="${r.id === s.regionId ? "selected" : ""}" onclick="selectRegion('${r.id}')">${safe(r.name)}<small>${r.cities.length} Orte · Gesamtkarte offline</small></button>`,
    )
    .join("");
  if (selectedTab === "dispatch") {
    map.update(s);
    renderMapDetail();
    renderIncidents();
  }
  if (selectedTab === "units") renderUnits();
  if (selectedTab === "training") renderTraining();
  if (selectedTab === "editor") {
    renderTemplates();
    renderVehicles();
  }
  if (selectedTab === "history") {
    renderHistory();
    renderAudio();
  }
  save();
}
const actions = {
  chooseGameMode,
  startNewGame,
  showPlanning,
  planShift,
  planDuration,
  planStart,
  planRegion,
  toggleRule,
  toggleAuto,
  setCallInterval,
  openPlanningArea,
  startShift,
  togglePause,
  setSpeed,
  advanceMinutes,
  skipShift,
  endShiftPrompt,
  showDebrief,
  viewHistoryAfterDebrief,
  nextDay,
  selectRegion,
  switchTab,
  newIncident,
  showcaseCalls,
  assign,
  recall,
  rememberDispatch,
  decide,
  locateUnit,
  locateIncident,
  clearMapSelection,
  upgradeFacility,
  placeOnMap,
  cancelPlacement,
  showUnitDetails,
  setDuty,
  hireRelief,
  manualRotation,
  restUnit,
  backFromProfile,
  showOfficerDetails,
  buyOrToggleGear,
  changeOfficerPortrait,
  uploadOfficerPortrait,
  startTraining,
  saveIncidentTemplate,
  loadTemplateEditor,
  saveAdvancedTemplate,
  duplicateTemplate,
  saveUnit,
  selectVehicleUnit,
  saveVehicle,
  maintainVehicle,
  exportSave,
  exportContentFile,
  importSave,
  importContentFile,
  saveSlot,
  loadSlot,
  resetGame,
  setAudio,
  testSound,
  testAmbient,
};
for (const [name, fn] of Object.entries(actions))
  (window as any)[name] = (...args: unknown[]) => {
    try {
      const result = (fn as (...args: unknown[]) => unknown)(...args);
      if (result instanceof Promise)
        result.catch((e) => toast((e as Error).message));
      return result;
    } catch (e) {
      toast((e as Error).message);
    }
  };
document
  .querySelectorAll<HTMLElement>(".tabs button")
  .forEach((b) => b.addEventListener("click", () => switchTab(b.dataset.tab!)));
// Expose a small deterministic test/diagnostic seam. No production state is fetched externally.
(window as any).EL110 = {
  getState: () => state,
  importState: (raw: string) => {
    state = parseSave(raw);
    selected = null;
    map.fit();
    render();
  },
  advance: (minutes: number) => step(minutes),
  map,
  repo,
};
function background() {
  if (state) {
    state.paused = true;
    save();
    render();
  }
  mixer.suspend();
}
document.addEventListener("visibilitychange", () => {
  if (document.hidden) background();
});
window.addEventListener("pagehide", background);
window.addEventListener("el110:lifecycle", (e) => {
  if (!(e as CustomEvent).detail.active) background();
});
window.addEventListener("el110:audio-focus", (e) => {
  focusBlocked = !(e as CustomEvent).detail.active;
  if (focusBlocked) background();
});
try {
  state = repo.load();
  if (state) {
    state.paused = true;
    mixer = new AudioMixer(state.audio);
    render();
    if (state.stage === "planning") showPlanning();
    else if (state.stage === "debrief") showDebrief();
  } else showWelcome();
} catch (e) {
  showWelcome();
  toast((e as Error).message);
}
for (const id of ["eType", "eExtra", "uType"]) {
  const select = el<HTMLSelectElement>(id);
  for (const type of engine.unitTypes)
    if (!Array.from(select.options).some((o) => o.value === type)) {
      const option = document.createElement("option");
      option.value = type;
      option.textContent = UNIT_TYPES[type].name;
      select.append(option);
    }
}
// Simulation has one authoritative minute tick. Rendering never changes the clock.
setInterval(() => {
  if (state?.stage === "running" && !state.paused) step(state.speed);
}, 1000);
document.documentElement.dataset.appReady = "true";
