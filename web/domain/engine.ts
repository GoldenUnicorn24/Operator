import { BASE_TEMPLATES, COURSES, GEAR, UNIT_TYPES } from "../data/catalog";
import {
  regions,
  getRegion,
  contains,
  samplePosition,
  routeBetween,
  distanceKm,
} from "../map/regions";
import { SHIFT_OPTIONS, nextStart, timeLabel } from "./clock";
import type {
  GameState,
  Unit,
  UnitType,
  Officer,
  Incident,
  IncidentTemplate,
  Position,
  ShiftStats,
  ShiftReport,
  Vehicle,
} from "./model";
export const ACTIVE = ["open", "dispatching", "working"];
export const unitTypes = [
  "STREIFE",
  "SEK",
  "MEK",
  "KRIPO",
  "BEREITSCHAFT",
  "HUND",
  "VERKEHR",
  "HELI",
] as UnitType[];
const names = [
  "Lukas Weber",
  "Nora Krüger",
  "Tim Scholz",
  "Mika Yilmaz",
  "Felix Hansen",
  "Paul Richter",
  "Alina König",
  "Deniz Becker",
  "Sarah Hoffmann",
  "Jan Peters",
  "Lena Fischer",
  "Marie Neumann",
  "Oliver Brandt",
  "Sophie Adler",
  "Emil Wagner",
  "Hannah Friese",
  "Leonie Meyer",
  "Dennis Krause",
  "Jonas Wolf",
  "Lara Stein",
  "Erik Berger",
  "Mara Hoffmann",
  "Niklas Busch",
  "Sina Köhler",
  "Julian Albers",
  "Johanna Bach",
];
export function stats(): ShiftStats {
  return {
    total: 0,
    solved: 0,
    failed: 0,
    transferred: 0,
    income: 0,
    expenses: 0,
    trainingDone: [],
    specialDispatches: 0,
    rotations: 0,
    wages: 0,
    maintenance: 0,
    trainingProgress: [],
  };
}
export function random(s: GameState) {
  s.rngSeed = (Math.imul(s.rngSeed, 1664525) + 1013904223) >>> 0;
  return s.rngSeed / 4294967296;
}
export function emit(s: GameState, msg: string) {
  s.logs.unshift(`[Tag ${s.day} · ${timeLabel(s.absoluteSimMinutes)}] ${msg}`);
  s.logs = s.logs.slice(0, 250);
}
export function officer(id: number, avatar = 0): Officer {
  return {
    id,
    name: names[(id - 1) % names.length],
    avatar: avatar % 8,
    photo: null,
    owned: ["radio", "vest"],
    equipped: ["radio", "vest"],
    skill: 54 + ((id * 7) % 24),
    energy: 100,
    xp: 0,
    qualifications: [],
    dutyMinutes: 0,
    restMinutes: 960,
  };
}
export function newVehicle(id: number, type: UnitType): Vehicle {
  const role = UNIT_TYPES[type];
  return {
    id,
    name: role.name,
    type,
    capacity: role.capacity,
    speedKmh: role.speedKmh,
    condition: 100,
    cost: role.cost,
    image: role.image,
  };
}
export function makeUnit(
  s: GameState,
  name: string,
  type: UnitType,
  crew = 2,
  position?: Position,
  avatar = 0,
): Unit {
  if (
    !unitTypes.includes(type) ||
    !Number.isInteger(crew) ||
    crew < 1 ||
    crew > UNIT_TYPES[type].capacity
  )
    throw Error("Besatzung überschreitet Fahrzeugkapazität.");
  const id = ++s.seq;
  const p = position ?? samplePosition(s.regionId, () => random(s));
  if (!contains(s.regionId, p))
    throw Error("Standort außerhalb des Bundeslandes.");
  const base = s.facilities
    .filter((f) => f.regionId === s.regionId)
    .reduce((a, b) =>
      distanceSq(a.position, p) < distanceSq(b.position, p) ? a : b,
    );
  return {
    id,
    name,
    type,
    officers: Array.from({ length: crew }, (_, i) =>
      officer(++s.seq, avatar + i),
    ),
    reserveOfficers: [1, 2].map((k) =>
      Array.from({ length: crew }, (_, i) => officer(++s.seq, avatar + i + k)),
    ),
    rosterIndex: 0,
    training: null,
    incidentId: null,
    busyUntil: null,
    experience: 0,
    regionId: s.regionId,
    position: [...p],
    basePosition: [...p],
    facilityId: base.id,
    status: "ready",
    route: null,
    vehicle: newVehicle(++s.seq, type),
    shiftDuty: true,
    restUntil: 0,
    readinessAt: 0,
  };
}
function distanceSq(a: Position, b: Position) {
  return (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2;
}
export function ensureRegion(s: GameState, id: string) {
  const region = getRegion(id);
  if (!region) throw Error("Bundesland unbekannt.");
  if (s.facilities.some((f) => f.regionId === id)) return;
  const previous = s.regionId;
  s.regionId = id;
  for (const [index, c] of region.cities.entries())
    s.facilities.push({
      id: `${id}-${index}`,
      name: `Spielwache ${c.name} · fiktiv`,
      regionId: id,
      position: [...c.position],
      level: 1,
    });
  if (!region.cities.length) throw Error("Keine regionalen Standorte.");
  for (let i = 0; i < 32; i++) {
    const type: UnitType = i < 20 ? "STREIFE" : unitTypes[1 + ((i - 20) % 7)];
    const city = region.cities[i % region.cities.length];
    const p =
      i < region.cities.length
        ? city.position
        : samplePosition(id, () => random(s));
    s.units.push(
      makeUnit(
        s,
        `${type === "STREIFE" ? "Peter" : UNIT_TYPES[type].name} ${id} ${String(i + 1).padStart(2, "0")}`,
        type,
        type === "SEK" ? 4 : 2,
        p,
        i % 8,
      ),
    );
  }
  s.regionId = previous;
}
export function newState(
  mode: "career" | "sandbox" = "career",
  regionId = "HH",
): GameState {
  const region = getRegion(regionId);
  const s: GameState = {
    version: 3,
    schemaVersion: 3,
    mode,
    stage: "planning",
    region: region.name,
    regionId,
    day: 1,
    completedShifts: 0,
    shiftType: "early",
    elapsed: 0,
    shiftStartAbsoluteMinute: 360,
    shiftDurationMinutes: 480,
    shiftElapsedMinutes: 0,
    shiftEndAbsoluteMinute: 840,
    absoluteSimMinutes: 0,
    startHour: 6,
    speed: 1,
    paused: true,
    seq: 1000,
    budget: mode === "sandbox" ? 1000000 : 250000,
    reputation: 60,
    autoCalls: mode === "career",
    rules: {
      budgetLimited: mode === "career",
      trainingEnabled: true,
      fatigueEnabled: true,
      callInterval: 30,
      customEnabled: true,
    },
    units: [],
    templates: structuredClone(BASE_TEMPLATES) as IncidentTemplate[],
    incidents: [],
    facilities: [],
    logs: [],
    history: [],
    shiftStats: stats(),
    nextCallAt: 16,
    rngSeed: 1102026,
    totalHours: 0,
    migrations: [],
    audio: { master: 0.7, radio: 0.7, sfx: 0.65, ambient: 0, mute: false },
  };
  ensureRegion(s, regionId);
  emit(s, "Leitstelle bereit. Alle Dienststellen sind fiktive Spielstandorte.");
  return s;
}
export function switchRegion(s: GameState, id: string) {
  ensureRegion(s, id);
  s.regionId = id;
  s.region = getRegion(id).name;
  emit(s, "Kartenbereich: " + s.region);
}
export function spend(s: GameState, cost: number) {
  if (!Number.isFinite(cost) || cost < 0) throw Error("Ungültiger Betrag.");
  if (s.rules.budgetLimited && s.budget < cost)
    throw Error("Das Budget reicht nicht aus.");
  if (s.rules.budgetLimited) {
    s.budget -= cost;
    s.shiftStats.expenses += cost;
  }
  return true;
}
export function available(s: GameState, u: Unit) {
  return (
    !u.incidentId &&
    !u.training &&
    u.shiftDuty &&
    u.status === "ready" &&
    u.vehicle.condition >= 20 &&
    s.absoluteSimMinutes >= u.restUntil &&
    (!s.rules.fatigueEnabled || u.officers.every((o) => o.energy > 15))
  );
}
export function remainingNeed(s: GameState, i: Incident) {
  const req = [...i.needs];
  for (const id of i.assignedIds) {
    const u = s.units.find((u) => u.id === id);
    const n = req.indexOf(u?.type ?? "");
    if (n >= 0) req.splice(n, 1);
  }
  return req;
}
export function startShift(s: GameState) {
  if (s.stage !== "planning") throw Error("Keine Schichtplanung aktiv.");
  const start = nextStart(s),
    rest = start - s.absoluteSimMinutes;
  for (const u of s.units) {
    for (const o of [u.officers, ...u.reserveOfficers].flat()) {
      o.energy = Math.min(100, o.energy + rest * 0.1);
      o.restMinutes += rest;
      if (o.restMinutes >= 480) o.dutyMinutes = 0;
    }
    u.rosterIndex = 0;
    if (!u.incidentId && !u.route) u.status = u.shiftDuty ? "ready" : "offDuty";
  }
  s.shiftStartAbsoluteMinute = start;
  s.shiftEndAbsoluteMinute = start + s.shiftDurationMinutes;
  s.absoluteSimMinutes = start;
  s.shiftElapsedMinutes = 0;
  s.elapsed = 0;
  s.stage = "running";
  s.paused = false;
  s.speed = 1;
  s.nextCallAt = 12;
  const expenses = s.shiftStats.expenses;
  s.shiftStats = stats();
  s.shiftStats.expenses = expenses;
  for (const i of s.incidents.filter(
    (i) => i.status === "handover" && i.handover,
  )) {
    const h = i.handover!;
    i.status = h.status;
    i.deadlineAt = start + h.remainingDeadline;
    i.completeAt = h.remainingWork === null ? null : start + h.remainingWork;
    for (const uid of i.assignedIds) {
      const u = s.units.find((u) => u.id === uid);
      if (u?.route) u.route.departureAt = start;
    }
    i.history.push("Durch Folgeschicht übernommen");
    delete i.handover;
  }
  emit(
    s,
    `Schichtbeginn ${s.shiftDurationMinutes / 60}h · ${timeLabel(start)}–${timeLabel(s.shiftEndAbsoluteMinute)}. Eine Schicht zählt als ein Spieltag.`,
  );
}
export function createIncident(
  s: GameState,
  tpl: IncidentTemplate,
  position?: Position,
  regionId = s.regionId,
): Incident {
  if (s.stage !== "running") throw Error("Notrufe erst während einer Schicht.");
  if (s.incidents.length >= 1000)
    throw Error("Einsatzarchiv voll. Exportiere den Spielstand.");
  if (tpl.regionId && tpl.regionId !== regionId)
    throw Error("Vorlage gehört zu einem anderen Bundesland.");
  const p =
    position ?? tpl.position ?? samplePosition(regionId, () => random(s));
  if (!contains(regionId, p))
    throw Error("Einsatz liegt außerhalb des gewählten Bundeslandes.");
  const first = tpl.chain?.find(
    (n) => n.id === (tpl.chainStartId ?? tpl.chain?.[0]?.id),
  );
  const i: Incident = {
    ...structuredClone(tpl),
    ...(first
      ? {
          name: first.name,
          priority: first.priority,
          needs: [...first.needs],
          duration: first.duration,
        }
      : {}),
    id: ++s.seq,
    status: "open",
    created: s.elapsed,
    createdAbsoluteMinute: s.absoluteSimMinutes,
    assignedIds: [],
    completeAt: null,
    region: getRegion(regionId).name,
    regionId,
    position: [...p],
    deadlineAt:
      s.absoluteSimMinutes +
      (tpl.deadlineMinutes ??
        (tpl.priority === 1 ? 90 : tpl.priority === 2 ? 180 : 360)),
    riskDelta: 0,
    history: ["Notruf eingegangen"],
    followUpSpawned: false,
    chainSteps: 0,
    ...(first ? { chainNode: first.id } : {}),
  };
  s.incidents.unshift(i);
  s.shiftStats.total++;
  emit(s, `Notruf P${i.priority}: ${i.name} · ${i.region}`);
  return i;
}
export function newIncident(
  s: GameState,
  templateId?: number,
  position?: Position,
) {
  const candidates = s.templates.filter(
    (t) =>
      (!t.regionId || t.regionId === s.regionId) &&
      (s.rules.customEnabled || t.id <= 100),
  );
  const tpl =
    templateId === undefined
      ? candidates[Math.floor(random(s) * candidates.length)]
      : candidates.find((t) => t.id === templateId);
  if (!tpl) throw Error("Keine passende Einsatzvorlage.");
  return createIncident(s, tpl, position);
}
export function assign(s: GameState, incidentId: number, unitId: number) {
  if (s.stage !== "running") throw Error("Keine laufende Schicht.");
  const i = s.incidents.find((i) => i.id === incidentId),
    u = s.units.find((u) => u.id === unitId);
  if (
    !i ||
    !u ||
    !["open", "dispatching"].includes(i.status) ||
    !available(s, u) ||
    u.regionId !== i.regionId ||
    !remainingNeed(s, i).includes(u.type)
  )
    throw Error("Einheit nicht frei, falsche Region oder fehlende Eignung.");
  i.assignedIds.push(u.id);
  i.status = "dispatching";
  u.incidentId = i.id;
  u.status = "responding";
  const delay = UNIT_TYPES[u.type].dispatchDelayMinutes;
  const route = routeBetween(
    u.position,
    i.position,
    u.vehicle.speedKmh * (u.vehicle.condition < 50 ? 0.75 : 1),
  );
  u.route = {
    ...route,
    progress: 0,
    elapsedMinutes: 0,
    departureAt: s.absoluteSimMinutes + delay,
  };
  u.readinessAt = u.route.departureAt;
  u.busyUntil =
    s.absoluteSimMinutes +
    delay +
    Math.ceil(route.durationMinutes) +
    i.duration;
  if (["SEK", "MEK"].includes(u.type)) s.shiftStats.specialDispatches++;
  i.history.push(
    `${u.name}: Anfahrt, ca. ${Math.ceil(route.durationMinutes + delay)} Min (Simulation)`,
  );
  emit(s, i.history.at(-1)!);
}
export function recall(s: GameState, unitId: number) {
  const u = s.units.find((u) => u.id === unitId);
  if (!u || !u.incidentId) return;
  const i = s.incidents.find((i) => i.id === u.incidentId);
  if (i) {
    i.assignedIds = i.assignedIds.filter((id) => id !== u.id);
    i.status = "dispatching";
    i.completeAt = null;
    i.history.push(`${u.name} zurückgerufen`);
  }
  u.incidentId = null;
  returnHome(s, u);
}
function returnHome(s: GameState, u: Unit) {
  const r = routeBetween(u.position, u.basePosition, u.vehicle.speedKmh * 0.85);
  u.route = {
    ...r,
    progress: 0,
    elapsedMinutes: 0,
    departureAt: s.absoluteSimMinutes,
  };
  u.status = "returning";
  u.busyUntil = null;
}
export function release(s: GameState, i: Incident) {
  for (const uid of i.assignedIds) {
    const u = s.units.find((u) => u.id === uid);
    if (u?.incidentId === i.id) {
      u.incidentId = null;
      returnHome(s, u);
    }
  }
}
export function decide(s: GameState, id: number, decisionId: string) {
  const i = s.incidents.find((i) => i.id === id),
    d = i?.decisions?.find((d) => d.id === decisionId);
  if (!i || !d || i.decisionId || !ACTIVE.includes(i.status))
    throw Error("Entscheidung ist nicht verfügbar.");
  spend(s, d.cost);
  i.decisionId = d.id;
  i.duration = Math.max(1, i.duration + d.durationDelta);
  if (i.completeAt)
    i.completeAt = Math.max(
      s.absoluteSimMinutes + 1,
      i.completeAt + d.durationDelta,
    );
  i.riskDelta += d.riskDelta;
  if (d.followUp) i.followUp = d.followUp;
  i.history.push(d.label);
  emit(s, "Entscheidung: " + d.label);
}
function move(s: GameState, u: Unit) {
  const r = u.route;
  if (!r || s.absoluteSimMinutes <= r.departureAt) return;
  r.elapsedMinutes++;
  r.progress = Math.min(1, r.elapsedMinutes / Math.max(1, r.durationMinutes));
  const lengths = r.points.slice(1).map((p, n) => distanceKm(r.points[n], p));
  const total = lengths.reduce((a, b) => a + b, 0);
  let target = total * r.progress,
    n = 0;
  while (n < lengths.length - 1 && target > lengths[n]) {
    target -= lengths[n];
    n++;
  }
  const f = lengths[n] > 0 ? target / lengths[n] : 0;
  u.position = [
    r.points[n][0] + (r.points[n + 1][0] - r.points[n][0]) * f,
    r.points[n][1] + (r.points[n + 1][1] - r.points[n][1]) * f,
  ];
  u.vehicle.condition = Math.max(
    0,
    u.vehicle.condition -
      (r.distanceKm / Math.max(1, r.durationMinutes)) * 0.003,
  );
  if (r.progress >= 1) {
    u.position = [...r.points.at(-1)!];
    u.route = null;
    u.status = u.incidentId ? "onScene" : u.shiftDuty ? "ready" : "offDuty";
  }
}
function rotate(s: GameState, u: Unit) {
  if (u.training || !u.shiftDuty || u.status === "resting") return;
  const all = [u.officers, ...u.reserveOfficers];
  const rotateAfter = s.shiftDurationMinutes > 480 ? 480 : Infinity;
  if (u.officers.some((o) => o.dutyMinutes >= rotateAfter)) {
    const candidates = u.reserveOfficers
      .map((team, index) => ({
        team,
        index,
        rest: Math.min(...team.map((o) => o.restMinutes)),
      }))
      .filter((v) =>
        v.team.every((o) => o.restMinutes >= 480 && o.energy >= 50),
      )
      .sort((a, b) => b.rest - a.rest);
    const next = candidates[0]?.index ?? -1;
    if (next >= 0) {
      const outgoing = u.officers;
      u.officers = u.reserveOfficers[next];
      u.reserveOfficers[next] = outgoing;
      u.rosterIndex++;
      u.officers.forEach((o) => {
        o.dutyMinutes = 0;
        o.restMinutes = 0;
      });
      s.shiftStats.rotations++;
      emit(
        s,
        u.name + ": Personalwechsel, Einsatz und Fahrzeug bleiben erhalten.",
      );
    } else if (!u.incidentId) {
      u.restUntil = s.absoluteSimMinutes + 240;
      u.status = "resting";
    }
  }
}
function tickPersonnel(s: GameState, u: Unit) {
  if (u.training) return;
  const onDuty =
    s.stage === "running" &&
    u.shiftDuty &&
    u.status !== "resting" &&
    u.status !== "offDuty";
  for (const o of u.officers) {
    if (onDuty) {
      o.dutyMinutes++;
      o.restMinutes = 0;
      if (s.rules.fatigueEnabled)
        o.energy = Math.max(0, o.energy - (u.incidentId ? 0.055 : 0.025));
    } else {
      o.restMinutes++;
      if (s.rules.fatigueEnabled) o.energy = Math.min(100, o.energy + 0.12);
    }
  }
  for (const o of u.reserveOfficers.flat()) {
    o.restMinutes++;
    if (s.rules.fatigueEnabled) o.energy = Math.min(100, o.energy + 0.12);
  }
  if (u.status === "resting" && s.absoluteSimMinutes >= u.restUntil) {
    u.status = "ready";
    u.officers.forEach((o) => (o.dutyMinutes = 0));
  }
  rotate(s, u);
}
function resolve(s: GameState, i: Incident) {
  const staff = i.assignedIds.flatMap(
    (id) => s.units.find((u) => u.id === id)?.officers ?? [],
  );
  const competence =
    staff.reduce(
      (v, o) =>
        v +
        o.skill +
        o.equipped.reduce(
          (b, id) => b + (GEAR.find((g) => g.id === id)?.bonus ?? 0),
          0,
        ) -
        (100 - o.energy) * 0.25,
      0,
    ) / Math.max(1, staff.length);
  const chance = Math.max(
    0.2,
    Math.min(0.99, 0.65 + (competence - 60) * 0.008 - i.riskDelta),
  );
  const success = random(s) < chance;
  i.status = success ? "resolved" : "failed";
  if (success) {
    s.shiftStats.solved++;
    const reward =
      i.reward ?? (i.priority === 1 ? 1900 : i.priority === 2 ? 1100 : 600);
    s.shiftStats.income += reward;
    if (s.rules.budgetLimited) s.budget += reward;
    s.reputation = Math.min(100, s.reputation + 1);
  } else {
    s.shiftStats.failed++;
    s.reputation = Math.max(0, s.reputation - 2);
  }
  for (const o of staff) {
    o.xp += success ? 14 : 5;
    if (o.xp >= 100) {
      o.skill = Math.min(100, o.skill + 1);
      o.xp -= 100;
    }
  }
  i.history.push(
    `${success ? "Erfolgreich" : "Mit Schwierigkeiten"} · Eignung ${Math.round(competence)}, Erfolgschance ${Math.round(chance * 100)}%`,
  );
  emit(s, `${i.name}: ${i.history.at(-1)}`);
  release(s, i);
  spawnFollowUp(s, i, success);
}
function spawnFollowUp(s: GameState, i: Incident, success: boolean) {
  if (!i.followUpSpawned && i.chainSteps < 20) {
    i.followUpSpawned = true;
    const node = i.chain?.find(
      (n) => n.id === (i.chainNode ?? i.chain?.[0]?.id),
    );
    const nextId = success ? node?.onSuccess : node?.onFailure;
    const next = i.chain?.find((n) => n.id === nextId);
    if (next) {
      const follow = createIncident(
        s,
        {
          ...i,
          ...next,
          id: 0,
          chain: i.chain,
          chainStartId: next.id,
          position: i.position,
          followUp: null,
        },
        i.position,
        i.regionId,
      );
      follow.chainNode = next.id;
      follow.chainSteps = i.chainSteps + 1;
    } else if (success && i.followUp?.name) {
      createIncident(
        s,
        {
          id: 0,
          name: i.followUp.name,
          priority: i.followUp.priority,
          needs: [i.followUp.type],
          duration: 15,
          description: `Folgeereignis: ${i.name}`,
          deadlineMinutes: 180,
        },
        i.position,
        i.regionId,
      );
    }
  }
}

export function advance(s: GameState, minutes: number) {
  if (!Number.isInteger(minutes) || minutes < 0 || minutes > 1440)
    throw Error("Ungültiger Zeitschritt.");
  if (s.paused || s.stage !== "running") return;
  for (let k = 0; k < minutes && s.stage === "running"; k++) {
    s.absoluteSimMinutes++;
    s.shiftElapsedMinutes++;
    s.elapsed = s.shiftElapsedMinutes;
    for (const u of s.units) {
      move(s, u);
      tickPersonnel(s, u);
    }
    for (const i of [...s.incidents]) {
      if (!ACTIVE.includes(i.status)) continue;
      if (i.status !== "working" && s.absoluteSimMinutes > i.deadlineAt) {
        i.status = "failed";
        s.shiftStats.failed++;
        s.reputation = Math.max(0, s.reputation - 2);
        if (s.rules.budgetLimited) {
          s.budget -= 420;
          s.shiftStats.expenses += 420;
        }
        emit(s, "Frist überschritten: " + i.name);
        release(s, i);
        spawnFollowUp(s, i, false);
        continue;
      }
      if (
        i.status === "dispatching" &&
        remainingNeed(s, i).length === 0 &&
        i.assignedIds.every(
          (id) => s.units.find((u) => u.id === id)?.status === "onScene",
        )
      ) {
        i.status = "working";
        const teams = i.assignedIds.map(
          (id) => s.units.find((u) => u.id === id)!,
        );
        const skill =
          teams.flatMap((u) => u.officers).reduce((a, o) => a + o.skill, 0) /
          teams.flatMap((u) => u.officers).length;
        i.completeAt =
          s.absoluteSimMinutes +
          Math.max(1, Math.round(i.duration * (1 - (skill - 60) / 200)));
        i.history.push("Alle Kräfte eingetroffen. Bearbeitung läuft.");
        emit(s, i.name + ": Kräfte vor Ort.");
      }
      if (i.status === "working" && s.absoluteSimMinutes >= i.completeAt!)
        resolve(s, i);
    }
    if (
      s.autoCalls &&
      s.incidents.length < 1000 &&
      s.elapsed >= s.nextCallAt &&
      s.elapsed < s.shiftDurationMinutes - 30 &&
      s.incidents.filter((i) => ACTIVE.includes(i.status)).length < 30
    ) {
      newIncident(s);
      s.nextCallAt =
        s.elapsed +
        s.rules.callInterval +
        Math.floor(random(s) * s.rules.callInterval * 0.5);
    }
    if (s.absoluteSimMinutes >= s.shiftEndAbsoluteMinute) finishShift(s);
  }
}
export function finishShift(s: GameState, early = false) {
  if (s.stage !== "running") return;
  if (early && s.elapsed < 30)
    throw Error("Vorzeitiges Ende frühestens nach 30 Minuten.");
  s.stage = "debrief";
  s.paused = true;
  let critical = 0;
  for (const i of s.incidents) {
    if (ACTIVE.includes(i.status)) {
      i.handover = {
        fromDay: s.day,
        status: i.status,
        remainingDeadline: Math.max(15, i.deadlineAt - s.absoluteSimMinutes),
        remainingWork:
          i.completeAt === null
            ? null
            : Math.max(1, i.completeAt - s.absoluteSimMinutes),
      };
      i.status = "handover";
      i.history.push("Dokumentierte Übergabe an die nächste Schicht");
      s.shiftStats.transferred++;
      if (i.priority === 1) critical++;
    }
  }
  for (const u of s.units) {
    if (u.training) {
      u.training.remainingDays--;
      s.shiftStats.trainingProgress.push(
        `${u.name}: ${Math.max(0, u.training.remainingDays)} Tage übrig`,
      );
      if (u.training.remainingDays <= 0) {
        const c = COURSES.find((c) => c.id === u.training!.courseId);
        if (c) {
          for (const o of [u.officers, ...u.reserveOfficers].flat()) {
            o.skill = Math.min(100, o.skill + c.skill);
            if (!o.qualifications.includes(c.id)) o.qualifications.push(c.id);
          }
          s.shiftStats.trainingDone.push(`${u.name} – ${c.name}`);
        }
        u.training = null;
      }
    }
  }
  const hours = s.elapsed / 60;
  const duty = s.units.filter((u) => u.shiftDuty);
  const wages = Math.round(
    duty.reduce((sum, u) => sum + u.officers.length * 22.5 * hours, 0),
  );
  const maintenance = Math.round(
    duty.reduce((sum, u) => sum + (100 - u.vehicle.condition) * 2, 0),
  );
  const funding = s.mode === "career" ? Math.round((9000 * hours) / 8) : 0;
  s.shiftStats.wages = wages;
  s.shiftStats.maintenance = maintenance;
  if (s.rules.budgetLimited) {
    s.budget += funding - wages - maintenance;
    s.shiftStats.income += funding;
    s.shiftStats.expenses += wages + maintenance;
  }
  s.totalHours += hours;
  s.completedShifts++;
  const personnel = duty.flatMap((u) => u.officers);
  const report: ShiftReport = {
    ...structuredClone(s.shiftStats),
    day: s.day,
    region: s.region,
    regionId: s.regionId,
    shift: s.shiftType,
    durationMinutes: s.shiftDurationMinutes,
    elapsed: s.elapsed,
    startAbsoluteMinute: s.shiftStartAbsoluteMinute,
    endAbsoluteMinute: s.absoluteSimMinutes,
    budget: s.budget,
    reputation: s.reputation,
    fatigue: Math.round(
      personnel.reduce((n, o) => n + 100 - o.energy, 0) /
        Math.max(1, personnel.length),
    ),
    totalHours: s.totalHours,
    criticalHandoffs: critical,
  };
  s.history.unshift(report);
  s.history = s.history.slice(0, 100);
  emit(
    s,
    `Schichtende: ${report.solved} Erfolge, ${report.transferred} Übergaben, ${report.rotations} Personalwechsel.`,
  );
}
export function nextDay(s: GameState) {
  if (s.stage !== "debrief") return;
  s.day++;
  s.stage = "planning";
  s.elapsed = 0;
  s.shiftElapsedMinutes = 0;
  s.paused = true;
  s.speed = 1;
  s.shiftStats = stats();
  emit(
    s,
    "Folgetag vorbereitet. Übergaben, Fahrzeuge, Ausrüstung und Lehrgänge bleiben erhalten.",
  );
}
export function startTraining(s: GameState, unitId: number, courseId: string) {
  const u = s.units.find((u) => u.id === unitId),
    c = COURSES.find((c) => c.id === courseId);
  if (
    s.stage !== "planning" ||
    !s.rules.trainingEnabled ||
    !u ||
    !c ||
    u.training ||
    u.incidentId ||
    (c.forType && c.forType !== u.type)
  )
    throw Error("Fortbildung ist für diese Einheit derzeit nicht möglich.");
  spend(s, c.cost);
  u.training = { courseId, remainingDays: c.days };
  emit(s, `${u.name} beginnt ${c.name}: ${c.days} abgeschlossene Spieltage.`);
}
export function toggleGear(
  s: GameState,
  unitId: number,
  index: number,
  gearId: string,
) {
  const u = s.units.find((u) => u.id === unitId),
    o = u?.officers[index],
    g = GEAR.find((g) => g.id === gearId);
  if (!u || !o || !g || s.stage === "debrief" || u.incidentId)
    throw Error("Ausrüstung kann gerade nicht geändert werden.");
  if (o.equipped.includes(gearId)) {
    o.equipped = o.equipped.filter((id) => id !== gearId);
    return;
  }
  if (o.equipped.length >= 4)
    throw Error("Maximal vier aktive Ausrüstungsslots.");
  if (!o.owned.includes(gearId)) {
    spend(s, g.cost);
    o.owned.push(gearId);
  }
  o.equipped.push(gearId);
}
export function maintainVehicle(s: GameState, id: number) {
  const u = s.units.find((u) => u.id === id);
  if (!u || u.incidentId || u.route || s.stage !== "planning")
    throw Error("Wartung nur mit freiem Fahrzeug während der Planung.");
  spend(s, Math.ceil((100 - u.vehicle.condition) * 25));
  u.vehicle.condition = 100;
  emit(s, u.name + ": Fahrzeug gewartet.");
}
export function rank(s: GameState) {
  return s.completedShifts >= 20 && s.reputation >= 80
    ? "Leitstellenleitung"
    : s.completedShifts >= 8
      ? "Dienstgruppenleitung"
      : s.completedShifts >= 3
        ? "Einsatzkoordination"
        : "Leitstellenassistenz";
}

export function recruitmentUnlocked(s: GameState, type: UnitType) {
  // Regional starting resources stay available. Additional air support becomes
  // a career procurement unlock; sandbox has immediate access.
  return (
    s.mode === "sandbox" ||
    type !== "HELI" ||
    (s.completedShifts >= 3 && s.reputation >= 65)
  );
}
