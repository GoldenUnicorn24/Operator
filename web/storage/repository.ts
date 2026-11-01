import LZString from "lz-string";
import type {
  GameState,
  Officer,
  Unit,
  Incident,
  ShiftReport,
  UnitType,
  IncidentTemplate,
} from "../domain/model";
import {
  newState,
  officer,
  newVehicle,
  stats,
  unitTypes,
} from "../domain/engine";
import {
  regions,
  getRegion,
  contains,
  samplePosition,
  routeBetween,
} from "../map/regions";
import { BASE_TEMPLATES } from "../data/catalog";
import { SHIFT_OPTIONS } from "../domain/clock";
import { validateTemplate, validateVehicle, finite } from "../editor/schema";
export const SAVE_KEY = "el110-save-v3";
const backupKey = SAVE_KEY + "-backup";
export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}
function assert(value: unknown, msg: string): asserts value {
  if (!value) throw Error("Beschädigter Spielstand: " + msg);
}
function list(
  value: unknown,
  max: number,
  label: string,
): asserts value is unknown[] {
  assert(Array.isArray(value) && value.length <= max, label);
}
function validOfficer(o: Officer) {
  assert(
    o &&
      Number.isInteger(o.id) &&
      typeof o.name === "string" &&
      o.name.length <= 90,
    "Beamtenprofil",
  );
  finite(o.skill, "Kompetenz", 0, 100);
  finite(o.energy, "Ausdauer", 0, 100);
  finite(o.xp, "Erfahrung", 0, 100000);
  assert(
    Number.isInteger(o.avatar) && o.avatar >= 0 && o.avatar < 8,
    "Porträt",
  );
  assert(
    o.photo === null ||
      (typeof o.photo === "string" &&
        o.photo.length < 150000 &&
        /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(o.photo)),
    "Porträtformat",
  );
  for (const a of [o.owned, o.equipped]) {
    list(a, 50, "Ausrüstung");
    assert(
      a.every((x) => typeof x === "string" && x.length < 80) &&
        new Set(a).size === a.length,
      "Ausrüstungs-ID",
    );
  }
  assert(
    o.equipped.length <= 4 && o.equipped.every((id) => o.owned.includes(id)),
    "Ausrüstungsbestand",
  );
  list(o.qualifications, 100, "Qualifikationen");
  finite(o.dutyMinutes, "Dienstzeit", 0, 1000000);
  finite(o.restMinutes, "Ruhezeit", 0, 100000000);
}
export function validateState(s: GameState): GameState {
  assert(s && s.schemaVersion === 3 && s.version === 3, "Version");
  assert(
    ["career", "sandbox"].includes(s.mode) &&
      ["planning", "running", "debrief"].includes(s.stage),
    "Spielmodus",
  );
  getRegion(s.regionId);
  assert(typeof s.region === "string", "Region");
  for (const k of [
    "day",
    "seq",
    "completedShifts",
    "shiftStartAbsoluteMinute",
    "shiftElapsedMinutes",
    "shiftEndAbsoluteMinute",
    "absoluteSimMinutes",
    "totalHours",
  ] as const)
    finite(s[k], k, 0, 1e9);
  assert(s.day >= 1 && Number.isInteger(s.day), "Spieltag");
  finite(s.budget, "Budget", -1e9, 1e12);
  finite(s.reputation, "Ansehen", 0, 100);
  assert(
    [480, 720, 1440].includes(s.shiftDurationMinutes) &&
      SHIFT_OPTIONS[s.shiftType],
    "Schichtdauer",
  );
  assert(
    s.shiftElapsedMinutes <= s.shiftDurationMinutes &&
      s.shiftEndAbsoluteMinute ===
        s.shiftStartAbsoluteMinute + s.shiftDurationMinutes,
    "Schichtgrenzen",
  );
  assert(
    Number.isInteger(s.startHour) && s.startHour >= 0 && s.startHour <= 23,
    "Startstunde",
  );
  assert(
    s.rules &&
      typeof s.rules.budgetLimited === "boolean" &&
      typeof s.rules.trainingEnabled === "boolean" &&
      typeof s.rules.fatigueEnabled === "boolean" &&
      typeof s.rules.customEnabled === "boolean",
    "Sandbox-Regeln",
  );
  finite(s.rules.callInterval, "Notruftakt", 10, 240);
  assert(
    [1, 4, 8, 16].includes(s.speed) &&
      typeof s.paused === "boolean" &&
      typeof s.autoCalls === "boolean",
    "Tempo",
  );
  finite(s.rngSeed, "Zufallszustand", 0, 4294967295);
  assert(Number.isSafeInteger(s.seq), "Sequenz");
  list(s.units, 2000, "Einheiten");
  list(s.facilities, 500, "Dienststellen");
  list(s.incidents, 1000, "Einsätze");
  list(s.templates, 500, "Vorlagen");
  list(s.history, 100, "Historie");
  list(s.logs, 250, "Protokoll");
  assert(
    s.logs.every((x) => typeof x === "string" && x.length <= 4000),
    "Protokolltext",
  );
  s.templates.forEach(validateTemplate);
  const ids = new Set<number>();
  for (const u of s.units) {
    assert(u && Number.isInteger(u.id) && !ids.has(u.id), "Einheiten-ID");
    ids.add(u.id);
    assert(
      typeof u.name === "string" &&
        u.name.length <= 90 &&
        unitTypes.includes(u.type),
      "Einheitenname/Typ",
    );
    getRegion(u.regionId);
    assert(
      Array.isArray(u.position) &&
        u.position.length === 2 &&
        u.position.every(Number.isFinite),
      "Position",
    );
    assert(contains(u.regionId, u.basePosition), "Heimatstandort"); // routes may cross a neighbouring state
    list(u.officers, 8, "Besatzung");
    assert(u.officers.length > 0, "Leere Besatzung");
    u.officers.forEach(validOfficer);
    list(u.reserveOfficers, 2, "Ablösung");
    for (const team of u.reserveOfficers) {
      list(team, 8, "Ablösungsteam");
      assert(team.length === u.officers.length, "Ablösungsgröße");
      team.forEach(validOfficer);
    }
    validateVehicle(u.vehicle);
    assert(u.vehicle.capacity >= u.officers.length, "Fahrzeugkapazität");
    if (u.training) {
      assert(typeof u.training.courseId === "string", "Lehrgang");
      finite(u.training.remainingDays, "Lehrgangsdauer", 1, 100);
    }
    if (u.route) {
      assert(
        Array.isArray(u.route.points) &&
          u.route.points.length >= 2 &&
          u.route.points.length <= 2000 &&
          u.route.points.every(
            (p) =>
              Array.isArray(p) && p.length === 2 && p.every(Number.isFinite),
          ),
        "Route",
      );
      finite(u.route.progress, "Fahrfortschritt", 0, 1);
      finite(u.route.durationMinutes, "Fahrdauer", 0, 100000);
      finite(u.route.elapsedMinutes, "Fahrzeit", 0, 100000);
      finite(u.route.distanceKm, "Fahrdistanz", 0, 50000);
      finite(u.route.departureAt, "Abfahrt", 0, 1e9);
    }
    assert(
      [
        "ready",
        "responding",
        "onScene",
        "returning",
        "resting",
        "offDuty",
      ].includes(u.status) && typeof u.shiftDuty === "boolean",
      "Einheitenstatus",
    );
    finite(u.restUntil, "Ruhefrist", 0, 1e9);
    finite(u.readinessAt, "Bereitschaft", 0, 1e9);
    finite(u.rosterIndex, "Personalwechsel", 0, 1e9);
  }
  const incidentIds = new Set<number>();
  for (const i of s.incidents) {
    validateTemplate(i);
    assert(Number.isInteger(i.id) && !incidentIds.has(i.id), "Einsatz-ID");
    incidentIds.add(i.id);
    assert(
      [
        "open",
        "dispatching",
        "working",
        "resolved",
        "failed",
        "handover",
      ].includes(i.status),
      "Einsatzstatus",
    );
    assert(contains(i.regionId, i.position), "Einsatzort");
    list(i.assignedIds, 8, "Zuweisungen");
    assert(
      new Set(i.assignedIds).size === i.assignedIds.length &&
        i.assignedIds.every((id) => ids.has(id)),
      "Zuweisungsreferenz",
    );
    finite(i.deadlineAt, "Frist", 0, 1e9);
    finite(i.createdAbsoluteMinute, "Einsatzzeit", 0, 1e9);
    finite(i.riskDelta, "Einsatzrisiko", -0.5, 0.5);
    finite(i.chainSteps, "Kettenschritte", 0, 20);
    if (i.completeAt !== null) finite(i.completeAt, "Abschlusszeit", 0, 1e9);
    if (i.handover) {
      assert(
        ["open", "dispatching", "working"].includes(i.handover.status),
        "Übergabestatus",
      );
      finite(i.handover.fromDay, "Übergabetag", 1, 1e9);
      finite(i.handover.remainingDeadline, "Übergabefrist", 0, 1e9);
      if (i.handover.remainingWork !== null)
        finite(i.handover.remainingWork, "Übergaberestzeit", 0, 1e9);
    }
    assert(typeof i.followUpSpawned === "boolean", "Folgeereignisstatus");
    assert(
      Array.isArray(i.history) && i.history.length <= 100,
      "Einsatzprotokoll",
    );
  }
  for (const u of s.units)
    if (u.incidentId !== null) {
      const i = s.incidents.find((i) => i.id === u.incidentId);
      assert(i && i.assignedIds.includes(u.id), "Einheitenzuweisung");
    }
  for (const f of s.facilities) {
    assert(
      typeof f.id === "string" &&
        /^[A-Za-z0-9_-]+$/.test(f.id) &&
        typeof f.name === "string" &&
        f.name.length < 200 &&
        contains(f.regionId, f.position),
      "Dienststelle",
    );
    finite(f.level, "Dienststellenstufe", 1, 10);
  }
  assert(
    s.seq >=
      Math.max(
        0,
        ...s.units.flatMap((u) => [
          u.id,
          u.vehicle.id,
          ...u.officers.map((o) => o.id),
          ...u.reserveOfficers.flat().map((o) => o.id),
        ]),
        ...s.templates.map((t) => t.id),
        ...s.incidents.map((i) => i.id),
      ),
    "Sequenz liegt hinter vorhandenen IDs",
  );
  assert(s.shiftStats && typeof s.shiftStats === "object", "Statistik");
  for (const k of Object.keys(stats()) as (keyof ReturnType<typeof stats>)[]) {
    const value = s.shiftStats[k];
    if (Array.isArray(stats()[k]))
      assert(Array.isArray(value), "Statistikliste");
    else finite(value, k, 0, 1e12);
  }
  for (const report of s.history) {
    assert(
      report &&
        Number.isFinite(report.day) &&
        typeof report.region === "string" &&
        typeof report.shift === "string",
      "Schichtbericht",
    );
    getRegion(report.regionId);
    for (const key of [
      "total",
      "solved",
      "failed",
      "transferred",
      "income",
      "expenses",
      "specialDispatches",
      "rotations",
      "wages",
      "maintenance",
      "day",
      "elapsed",
      "startAbsoluteMinute",
      "endAbsoluteMinute",
      "fatigue",
      "totalHours",
      "criticalHandoffs",
    ] as const)
      finite(report[key], "Bericht " + key, 0, 1e12);
    finite(report.budget, "Bericht Budget", -1e9, 1e12);
    finite(report.reputation, "Bericht Ruf", 0, 100);
    assert([480, 720, 1440].includes(report.durationMinutes), "Berichtdauer");
    for (const arr of [report.trainingDone, report.trainingProgress])
      assert(
        Array.isArray(arr) &&
          arr.length < 2000 &&
          arr.every((x) => typeof x === "string" && x.length < 4000),
        "Bericht Ausbildung",
      );
  }
  if (s.stage === "debrief")
    assert(s.history.length > 0, "Fehlende Tagesauswertung");
  assert(s.audio && typeof s.audio.mute === "boolean", "Audio");
  for (const k of ["master", "radio", "sfx", "ambient"] as const)
    finite(s.audio[k], k, 0, 1);
  return s;
}
function upgradeOfficer(o: Partial<Officer>, id: number): Officer {
  return {
    ...officer(id, o.avatar ?? 0),
    ...o,
    qualifications: o.qualifications ?? [],
    dutyMinutes: o.dutyMinutes ?? 0,
    restMinutes: o.restMinutes ?? 960,
  };
}
export function migrate(input: unknown): GameState {
  assert(input && typeof input === "object", "Datei");
  const old = structuredClone(input) as Record<string, any>;
  if (old.version === 3) return validateState(old as GameState);
  assert(
    old.version === 2 || (!old.version && Array.isArray(old.units)),
    "Unbekanntes Speicherformat",
  );
  list(old.units, 150, "Alpha-Einheiten");
  list(old.templates, 500, "Alpha-Vorlagen");
  const region = regions.find((r) => r.name === old.region) ?? getRegion("HH");
  const s = newState(old.mode === "career" ? "career" : "sandbox", region.id);
  s.units = [];
  s.budget = Number.isFinite(old.budget) ? old.budget : s.budget;
  s.day = Number.isFinite(old.day) ? old.day : 1;
  s.stage = ["planning", "running", "debrief"].includes(old.stage)
    ? old.stage
    : "planning";
  s.shiftType = SHIFT_OPTIONS[old.shiftType] ? old.shiftType : "early";
  s.startHour =
    Number.isInteger(old.shiftStart) &&
    old.shiftStart >= 0 &&
    old.shiftStart < 1440
      ? old.shiftStart / 60
      : SHIFT_OPTIONS[s.shiftType].start / 60;
  s.shiftDurationMinutes = SHIFT_OPTIONS[s.shiftType].duration;
  (s as GameState & { legacySource: unknown }).legacySource =
    structuredClone(old);
  s.elapsed = old.elapsed ?? 0;
  s.shiftElapsedMinutes = s.elapsed;
  s.shiftStartAbsoluteMinute = (s.day - 1) * 1440 + s.startHour * 60;
  s.absoluteSimMinutes = s.shiftStartAbsoluteMinute + s.elapsed;
  s.shiftEndAbsoluteMinute =
    s.shiftStartAbsoluteMinute + s.shiftDurationMinutes;
  s.reputation = old.reputation ?? 60;
  s.autoCalls = old.autoCalls ?? false;
  s.rules = {
    ...s.rules,
    ...old.rules,
    callInterval: 30,
    customEnabled: true,
    fatigueEnabled: true,
  };
  s.speed = [1, 4, 8, 16].includes(old.speed) ? old.speed : 1;
  s.paused = true;
  s.seq = Math.max(old.seq ?? 60, 1000);
  s.logs = Array.isArray(old.logs) ? old.logs.slice(0, 249) : [];
  s.shiftStats = { ...stats(), ...old.shiftStats };
  s.nextCallAt = old.nextCallAt ?? s.elapsed + 15;
  let seed = 110;
  const rng = () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 4294967296;
  };
  for (const [index, entry] of old.units.entries()) {
    const u = entry as Record<string, any>;
    assert(
      u && typeof u.name === "string" && unitTypes.includes(u.type),
      "Alpha-Einheit",
    );
    const id = u.id ?? index + 1;
    const p = contains(region.id, u.position)
      ? ([...u.position] as [number, number])
      : samplePosition(region.id, rng);
    const crew = Array.isArray(u.officers)
      ? u.officers.map((o: Officer, j: number) =>
          upgradeOfficer(o, id * 100 + j),
        )
      : Array.from({ length: Math.max(1, Math.min(8, u.crew ?? 2)) }, (_, j) =>
          officer(id * 100 + j, index),
        );
    const migrated: Unit = {
      ...u,
      id,
      name: u.name,
      type: u.type,
      officers: crew,
      reserveOfficers: [],
      rosterIndex: 0,
      training: u.training ?? null,
      incidentId: u.incidentId ?? null,
      busyUntil: u.busyUntil ?? null,
      experience: u.experience ?? 0,
      regionId: region.id,
      position: p,
      basePosition: [...p],
      facilityId: s.facilities[0].id,
      status: u.incidentId ? "onScene" : "ready",
      route: null,
      vehicle: newVehicle(++s.seq, u.type),
      shiftDuty: true,
      restUntil: 0,
      readinessAt: 0,
    };
    if (u.vehicle) {
      migrated.vehicle.name = String(u.vehicle.name || migrated.vehicle.name);
      migrated.vehicle.speedKmh = u.vehicle.speed ?? migrated.vehicle.speedKmh;
      migrated.vehicle.condition = u.vehicle.condition ?? 100;
    }
    migrated.vehicle.capacity = Math.max(
      migrated.vehicle.capacity,
      crew.length,
    );
    s.units.push(migrated);
  }
  s.templates = (old.templates as IncidentTemplate[]).map(
    (t: IncidentTemplate, index: number) => ({
      ...t,
      id: t.id ?? index + 1,
      description: t.description || "Eigene Alpha-Vorlage",
      needs: t.needs ?? [(t as any).type ?? "STREIFE"],
    }),
  );
  for (const t of BASE_TEMPLATES)
    if (!s.templates.some((x) => x.name === t.name))
      s.templates.push({
        ...structuredClone(t),
        id: ++s.seq,
      } as IncidentTemplate);
  s.incidents = (old.incidents ?? []).map((i: Incident) => {
    const p = contains(region.id, i.position)
      ? ([...i.position] as [number, number])
      : samplePosition(region.id, rng);
    const migrated: Incident = {
      ...i,
      status: (i.status as string) === "enroute" ? "dispatching" : i.status,
      description: i.description || "Alpha-Einsatz",
      position: p,
      regionId: region.id,
      region: region.name,
      createdAbsoluteMinute: s.shiftStartAbsoluteMinute + (i.created ?? 0),
      deadlineAt: s.absoluteSimMinutes + 90,
      completeAt:
        i.completeAt === null
          ? null
          : s.shiftStartAbsoluteMinute + (i.completeAt ?? s.elapsed + 20),
      history: ["Aus Alpha 0.2 migriert"],
      riskDelta: 0,
      followUpSpawned: false,
      chainSteps: 0,
    };
    if (i.status === "handover")
      migrated.handover = {
        fromDay: s.day,
        status: "open",
        remainingDeadline: 90,
        remainingWork: null,
      };
    for (const id of i.assignedIds ?? []) {
      const u = s.units.find((u) => u.id === id);
      if (u && u.incidentId === i.id) {
        if ((i.status as string) === "enroute") {
          u.route = {
            ...routeBetween(u.position, p, u.vehicle.speedKmh),
            progress: 0,
            elapsedMinutes: 0,
            departureAt: s.absoluteSimMinutes,
          };
          u.status = "responding";
        } else {
          u.position = [...p];
          u.status = "onScene";
        }
      }
    }
    return migrated;
  });
  s.history = (old.history ?? []).slice(0, 100).map((h: ShiftReport) => ({
    ...stats(),
    ...h,
    regionId: regions.find((r) => r.name === h.region)?.id ?? region.id,
    durationMinutes: 480,
    startAbsoluteMinute:
      (h.day - 1) * 1440 + (SHIFT_OPTIONS[h.shift]?.start ?? 360),
    endAbsoluteMinute:
      (h.day - 1) * 1440 + (SHIFT_OPTIONS[h.shift]?.start ?? 360) + h.elapsed,
    fatigue: 0,
    totalHours: h.elapsed / 60,
    criticalHandoffs: 0,
  }));
  s.completedShifts = s.history.length;
  s.totalHours = s.history.reduce((sum, h) => sum + h.elapsed / 60, 0);
  s.migrations = [
    old.version === 2
      ? "Alpha 0.2 → Schema 3: bestehende Daten erhalten; geografische Positionen initialisiert. Keine Ablösungsteams hinzugekauft."
      : "Alpha 0.1 → Schema 3",
  ];
  s.logs.unshift(s.migrations[0]);
  s.seq = Math.max(
    s.seq,
    ...s.units.flatMap((u) => [
      u.id,
      u.vehicle.id,
      ...u.officers.map((o) => o.id),
    ]),
    ...s.incidents.map((i) => i.id),
    ...s.templates.map((t) => t.id),
  );
  return validateState(s);
}
export function parseSave(raw: string): GameState {
  if (raw.startsWith("EL110Z3:")) {
    const decoded = LZString.decompressFromUTF16(raw.slice(8));
    if (!decoded) throw Error("Beschädigte Speicherkompression.");
    raw = decoded;
  }
  if (raw.length > 15 * 1024 * 1024)
    throw Error("Spielstand ist größer als 15 MB.");
  return migrate(JSON.parse(raw));
}
export class SaveRepository {
  constructor(private storage: StorageLike) {}
  save(s: GameState, slot = "autosave") {
    const encoded =
      "EL110Z3:" + LZString.compressToUTF16(JSON.stringify(validateState(s)));
    const key = slot === "autosave" ? SAVE_KEY : `el110-slot-${slot}`;
    const previous = this.storage.getItem(key);
    if (previous) {
      try {
        parseSave(previous);
        this.storage.setItem(
          slot === "autosave" ? backupKey : key + "-backup",
          previous,
        );
      } catch {
        /* Preserve the last valid backup when current data is corrupt. */
      }
    }
    this.storage.setItem(key, encoded);
  }
  load(slot = "autosave"): GameState | null {
    const key = slot === "autosave" ? SAVE_KEY : `el110-slot-${slot}`;
    const candidates =
      slot === "autosave"
        ? [key, backupKey, "el110-save-v2", "el110-save"]
        : [key, key + "-backup"];
    let found = false;
    for (const k of candidates) {
      const raw = this.storage.getItem(k);
      if (!raw) continue;
      found = true;
      try {
        return parseSave(raw);
      } catch {
        continue;
      }
    }
    if (found)
      throw Error(
        "Kein gültiger Spielstand gefunden. Originaldaten bleiben für Import/Wiederherstellung erhalten.",
      );
    return null;
  }
  import(raw: string, slot = "autosave") {
    const state = parseSave(raw);
    this.save(state, slot);
    return state;
  }
  clear() {
    this.storage.removeItem(SAVE_KEY);
    this.storage.removeItem(backupKey);
    this.storage.removeItem("el110-save-v2");
    this.storage.removeItem("el110-save");
  }
}
