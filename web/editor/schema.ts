import type {
  GameState,
  IncidentTemplate,
  UnitType,
  Vehicle,
  Position,
} from "../domain/model";
import {
  unitTypes,
  makeUnit,
  spend,
  officer as makeOfficer,
  recruitmentUnlocked,
} from "../domain/engine";
import { contains, getRegion } from "../map/regions";
export function text(value: unknown, label: string, max = 2000): string {
  if (typeof value !== "string" || !value.trim() || value.length > max)
    throw Error(`${label}: gültigen Text bis ${max} Zeichen angeben.`);
  return value.trim();
}
export function finite(
  value: unknown,
  label: string,
  min: number,
  max: number,
): number {
  if (
    typeof value !== "number" ||
    !Number.isFinite(value) ||
    value < min ||
    value > max
  )
    throw Error(`${label}: Zahl zwischen ${min} und ${max} erforderlich.`);
  return value;
}
export function position(value: unknown, regionId: string): Position {
  if (
    !Array.isArray(value) ||
    value.length !== 2 ||
    !value.every(Number.isFinite) ||
    !contains(regionId, value as Position)
  )
    throw Error("Koordinaten liegen nicht im Bundesland.");
  return [...value] as Position;
}
export function validateTemplate(value: unknown): IncidentTemplate {
  if (!value || typeof value !== "object")
    throw Error("Ungültige Einsatzvorlage.");
  const t = value as IncidentTemplate;
  if (!Number.isSafeInteger(t.id) || t.id < 0)
    throw Error("Vorlagen-ID muss eine nichtnegative ganze Zahl sein.");
  text(t.name, "Name", 90);
  text(t.description, "Beschreibung", 2000);
  finite(t.priority, "Priorität", 1, 3);
  if (!Number.isInteger(t.priority))
    throw Error("Priorität muss ganzzahlig sein.");
  finite(t.duration, "Dauer", 1, 360);
  if (
    !Array.isArray(t.needs) ||
    !t.needs.length ||
    t.needs.length > 8 ||
    t.needs.some((n) => !unitTypes.includes(n as UnitType))
  )
    throw Error("Unbekannte oder fehlende Einheitenanforderung.");
  if (t.deadlineMinutes !== undefined)
    finite(t.deadlineMinutes, "Frist", 10, 1440);
  if (t.reward !== undefined) finite(t.reward, "Belohnung", 0, 100000);
  if (t.regionId) {
    getRegion(t.regionId);
    if (t.position) position(t.position, t.regionId);
  } else if (t.position) throw Error("Position benötigt ein Bundesland.");
  if (t.followUp) {
    text(t.followUp.name, "Folgeereignis", 90);
    finite(t.followUp.priority, "Folgepriorität", 1, 3);
    if (!unitTypes.includes(t.followUp.type as UnitType))
      throw Error("Folgeereignis: Einheit unbekannt.");
  }
  if (t.decisions) {
    if (!Array.isArray(t.decisions) || t.decisions.length > 8)
      throw Error("Maximal acht Entscheidungen.");
    const ids = new Set();
    for (const d of t.decisions) {
      text(d.id, "Entscheidungs-ID", 40);
      if (!/^[A-Za-z0-9_-]+$/.test(d.id))
        throw Error("Entscheidungs-ID enthält ungültige Zeichen.");
      text(d.label, "Entscheidung", 160);
      if (ids.has(d.id)) throw Error("Doppelte Entscheidung.");
      ids.add(d.id);
      finite(d.cost, "Kosten", 0, 100000);
      finite(d.durationDelta, "Zeitwirkung", -120, 240);
      finite(d.riskDelta, "Risikowirkung", -0.5, 0.5);
    }
  }
  if (t.chain) {
    if (!Array.isArray(t.chain) || t.chain.length > 20 || !t.chain.length)
      throw Error("Einsatzketten benötigen 1–20 Stufen.");
    const ids = new Set(t.chain.map((n) => n.id));
    if (t.chainStartId && !ids.has(t.chainStartId))
      throw Error("Unbekannte Startstufe.");
    if (ids.size !== t.chain.length) throw Error("Doppelte Stufen-ID.");
    for (const n of t.chain) {
      text(n.id, "Stufen-ID", 40);
      if (!/^[A-Za-z0-9_-]+$/.test(n.id))
        throw Error("Stufen-ID enthält ungültige Zeichen.");
      text(n.name, "Stufenname", 90);
      finite(n.duration, "Stufendauer", 1, 360);
      finite(n.priority, "Stufenpriorität", 1, 3);
      if (
        !Array.isArray(n.needs) ||
        !n.needs.length ||
        n.needs.some((v) => !unitTypes.includes(v as UnitType))
      )
        throw Error("Stufe benötigt bekannte Einheiten.");
      for (const ref of [n.onSuccess, n.onFailure])
        if (ref && !ids.has(ref))
          throw Error("Einsatzkette verweist auf unbekannte Stufe: " + ref);
    }
    // Forbid cycles rather than silently executing an unbounded chain.
    const visit = (id: string, path: Set<string>) => {
      if (path.has(id)) throw Error("Zyklische Einsatzkette.");
      const node = t.chain!.find((n) => n.id === id)!;
      const next = new Set(path).add(id);
      for (const ref of [node.onSuccess, node.onFailure])
        if (ref) visit(ref, next);
    };
    for (const n of t.chain) visit(n.id, new Set());
  }
  return structuredClone(t);
}
export function saveTemplate(s: GameState, value: unknown, id?: number) {
  const t = validateTemplate(value);
  if (s.templates.length >= 500 && !id) throw Error("Maximal 500 Vorlagen.");
  if (id) {
    const index = s.templates.findIndex((t) => t.id === id);
    if (index < 0) throw Error("Vorlage nicht gefunden.");
    t.id = id;
    s.templates[index] = t;
  } else {
    t.id = ++s.seq;
    s.templates.push(t);
  }
  return t;
}
export function removeTemplate(s: GameState, id: number) {
  s.templates = s.templates.filter((t) => t.id !== id);
}
export function importContent(s: GameState, input: string) {
  if (input.length > 1000000) throw Error("Inhalt zu groß.");
  const parsed = JSON.parse(input);
  if (
    parsed.schemaVersion !== 1 ||
    parsed.kind !== "el110-content" ||
    !Array.isArray(parsed.templates) ||
    parsed.templates.length > 100
  )
    throw Error("Unbekanntes Inhaltsformat.");
  const templates = parsed.templates.map(validateTemplate);
  if (s.templates.length + templates.length > 500)
    throw Error("Maximal 500 Vorlagen.");
  for (const t of templates) {
    t.id = ++s.seq;
    s.templates.push(t);
  }
  return templates.length;
}
export function exportContent(s: GameState) {
  return JSON.stringify(
    { schemaVersion: 1, kind: "el110-content", templates: s.templates },
    null,
    2,
  );
}
export function validateVehicle(v: Vehicle) {
  text(v.name, "Fahrzeugname", 90);
  if (!unitTypes.includes(v.type)) throw Error("Fahrzeugtyp unbekannt.");
  finite(v.capacity, "Sitzplätze", 1, 8);
  if (!Number.isInteger(v.capacity))
    throw Error("Sitzplätze müssen ganzzahlig sein.");
  finite(v.speedKmh, "Simulationsgeschwindigkeit", 20, 250);
  finite(v.condition, "Zustand", 0, 100);
  finite(v.cost, "Preis", 0, 500000);
  if (
    typeof v.image !== "string" ||
    !/^images\/[\w_-]+\.(svg|webp)$/.test(v.image)
  )
    throw Error("Nur lokale Fahrzeugbilder.");
  return structuredClone(v);
}
export function editVehicle(s: GameState, unitId: number, value: Vehicle) {
  const u = s.units.find((u) => u.id === unitId);
  if (!u || u.incidentId || u.route || s.stage !== "planning")
    throw Error("Fahrzeugänderung nur mit freier Einheit in der Planung.");
  const v = validateVehicle(value);
  if (v.capacity < u.officers.length)
    throw Error("Fahrzeug hat zu wenige Sitzplätze.");
  spend(s, Math.max(0, v.cost - u.vehicle.cost));
  u.vehicle = { ...v, id: u.vehicle.id };
}
export function createUnit(
  s: GameState,
  input: {
    name: string;
    type: UnitType;
    crew: number;
    position: Position;
    avatar: number;
  },
) {
  if (!recruitmentUnlocked(s, input.type))
    throw Error(
      "Weitere Luftunterstützung ab 3 abgeschlossenen Schichten und 65% Reputation.",
    );
  if (s.stage !== "planning" || s.units.length >= 2000)
    throw Error("Neue Einheiten nur in der Planung (max. 2000).");
  text(input.name, "Funkrufname", 90);
  position(input.position, s.regionId);
  const u = makeUnit(
    s,
    input.name,
    input.type,
    input.crew,
    input.position,
    input.avatar,
  );
  spend(s, input.crew * 5700 + u.vehicle.cost);
  s.units.push(u);
  return u;
}

export function editUnit(
  s: GameState,
  id: number,
  input: { name: string; type: UnitType; crew: number; avatar: number },
) {
  if (!recruitmentUnlocked(s, input.type))
    throw Error(
      "Weitere Luftunterstützung ab 3 abgeschlossenen Schichten und 65% Reputation.",
    );
  const u = s.units.find((u) => u.id === id);
  if (!u || s.stage !== "planning" || u.incidentId || u.route)
    throw Error("Einheit nur frei in der Planung bearbeiten.");
  const name = text(input.name, "Funkrufname", 90);
  if (!unitTypes.includes(input.type)) throw Error("Organisation unbekannt.");
  if (
    !Number.isInteger(input.crew) ||
    input.crew < u.officers.length ||
    input.crew > u.vehicle.capacity
  )
    throw Error(
      "Personal ergänzen: vorhandene Besatzung bis Fahrzeugkapazität. Bestehende Profile bleiben erhalten.",
    );
  const added = input.crew - u.officers.length;
  spend(s, added * 1900 * (1 + u.reserveOfficers.length));
  // Existing portraits, training, inventory, and experience are retained when roles change.
  for (const team of [u.officers, ...u.reserveOfficers])
    for (let j = 0; j < added; j++)
      team.push(makeOfficer(++s.seq, (input.avatar + j) % 8));
  u.name = name;
  u.type = input.type;
  u.vehicle.type = input.type;
  return u;
}
