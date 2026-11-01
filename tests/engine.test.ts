import test from "node:test";
import assert from "node:assert/strict";
import * as e from "../web/domain/engine";
import { planShift } from "../web/domain/clock";
import { getRegion, distanceKm } from "../web/map/regions";
import { BASE_TEMPLATES } from "../web/data/catalog";
import type { GameState, IncidentTemplate } from "../web/domain/model";
function game(
  mode: "career" | "sandbox" = "sandbox",
  preset = "early",
  hour = 6,
  region = "NI",
) {
  const s = e.newState(mode, region);
  s.autoCalls = false;
  planShift(s, preset, hour);
  e.startShift(s);
  return s;
}
for (const mode of ["career", "sandbox"] as const)
  for (const [duration, preset, start] of [
    [480, "night", 22],
    [720, "night12", 18],
    [1440, "full24", 6],
  ] as const) {
    test(`shift_${duration / 60}h ${mode}: exact end, one report, hour based costs`, () => {
      const s = game(mode, preset, start);
      const money = s.budget;
      e.advance(s, duration - 1);
      assert.equal(s.stage, "running");
      assert.equal(s.completedShifts, 0);
      e.advance(s, 1);
      assert.equal(s.stage, "debrief");
      assert.equal(s.history.length, 1);
      assert.equal(s.history[0].elapsed, duration);
      assert.equal(
        s.history[0].endAbsoluteMinute - s.history[0].startAbsoluteMinute,
        duration,
      );
      assert.equal(s.completedShifts, 1);
      assert.equal(s.day, 1);
      const budget = s.budget;
      e.advance(s, 100);
      e.finishShift(s);
      assert.equal(s.history.length, 1);
      assert.equal(s.budget, budget);
      assert.equal(
        s.history[0].wages,
        Math.round(
          (s.units.reduce((a, u) => a + u.officers.length, 0) *
            22.5 *
            duration) /
            60,
        ),
      );
      if (mode === "career") assert.notEqual(s.budget, money);
      else assert.equal(s.budget, money);
      e.nextDay(s);
      e.nextDay(s);
      assert.equal(s.day, 2);
      e.startShift(s);
      assert.equal(s.history.length, 1);
      assert(s.shiftStartAbsoluteMinute >= s.history[0].endAbsoluteMinute);
    });
  }
test("midnight_once: local calendar rollover never ends a shift", () => {
  const s = game("sandbox", "full24", 22);
  e.advance(s, 120);
  assert.equal(s.absoluteSimMinutes % 1440, 0);
  assert.equal(s.stage, "running");
  assert.equal(s.day, 1);
  e.advance(s, 1320);
  assert.equal(s.completedShifts, 1);
  assert.equal(s.history[0].elapsed, 1440);
});
test("manual_pause_freezes_all: simulation, motion and deadlines stay unchanged", () => {
  const s = game();
  const i = e.newIncident(s, 1),
    u = s.units.find((u) => u.type === "STREIFE")!;
  e.assign(s, i.id, u.id);
  s.paused = true;
  const before = structuredClone(s);
  e.advance(s, 120);
  assert.deepEqual(s, before);
});
test("routing_cross_district: physical arrival precedes work and returns home", () => {
  const s = game();
  const r = getRegion("NI"),
    u = s.units.find((u) => u.type === "STREIFE")!;
  u.position = [...r.cities.find((c) => c.name === "Oldenburg")!.position];
  u.basePosition = [...u.position];
  const i = e.createIncident(
    s,
    { ...BASE_TEMPLATES[0], deadlineMinutes: 500 },
    r.cities.find((c) => c.name === "Hannover")!.position,
  );
  const origin: [number, number] = [...u.position];
  e.assign(s, i.id, u.id);
  assert(u.route!.distanceKm > 100);
  assert(u.route!.durationMinutes > 100);
  assert.equal(i.status, "dispatching");
  e.advance(s, 20);
  assert.notDeepEqual(u.position, origin);
  assert.equal(i.status, "dispatching");
  assert(distanceKm(u.position, i.position) < distanceKm(origin, i.position));
  e.advance(s, 150);
  assert(["working", "resolved", "failed"].includes(i.status));
  assert(u.position.every(Number.isFinite));
  e.advance(s, 200);
  assert.equal(u.incidentId, null);
  assert.deepEqual(u.position, u.basePosition);
});
test("dispatch_assigns_capability: type, occupancy, rest and region enforced", () => {
  const s = game();
  const i = e.newIncident(s, 4),
    police = s.units.find((u) => u.type === "STREIFE")!,
    mek = s.units.find((u) => u.type === "MEK")!;
  assert.throws(() => e.assign(s, i.id, mek.id));
  e.assign(s, i.id, police.id);
  assert.throws(() => e.assign(s, i.id, police.id));
  e.switchRegion(s, "BY");
  const other = s.units.find((u) => u.regionId === "BY" && u.type === "SEK")!;
  assert.throws(() => e.assign(s, i.id, other.id));
});
test("region_switch_retains_vehicles: moving positions and calls persist", () => {
  const s = game();
  const i = e.newIncident(s, 1),
    u = s.units.find((u) => u.type === "STREIFE")!;
  e.assign(s, i.id, u.id);
  e.advance(s, 10);
  const pos = [...u.position],
    route = structuredClone(u.route);
  e.switchRegion(s, "BY");
  assert.equal(s.units.filter((u) => u.regionId === "BY").length, 32);
  e.switchRegion(s, "NI");
  assert.deepEqual(u.position, pos);
  assert.deepEqual(u.route, route);
  assert.equal(s.incidents[0].id, i.id);
  assert.equal(s.units.filter((u) => u.regionId === "NI").length, 32);
});
test("training_by_completed_shift: two days independent of duration and optional", () => {
  for (const preset of ["early", "day12", "full24"]) {
    const s = e.newState("sandbox");
    s.autoCalls = false;
    planShift(s, preset, 6);
    const u = s.units[0],
      skill = u.officers[0].skill;
    e.startTraining(s, u.id, "comms");
    assert.equal(u.training!.remainingDays, 2);
    e.startShift(s);
    e.advance(s, s.shiftDurationMinutes);
    assert.equal(u.training!.remainingDays, 1);
    assert.equal(u.officers[0].skill, skill);
    e.nextDay(s);
    e.startShift(s);
    e.advance(s, s.shiftDurationMinutes);
    assert.equal(u.training, null);
    assert.equal(u.officers[0].skill, skill + 8);
    assert(u.officers[0].qualifications.includes("comms"));
    assert.equal(s.history[0].trainingDone.length, 1);
  }
});
test("handoff_24h: staff rotates and active incidents resume once with same assignment", () => {
  const s = game("career", "full24");
  const u = s.units[0],
    first = u.officers.map((o) => o.id);
  e.advance(s, 1430);
  assert(s.shiftStats.rotations >= s.units.length * 2);
  assert.notDeepEqual(
    u.officers.map((o) => o.id),
    first,
  );
  const i = e.createIncident(
    s,
    { ...BASE_TEMPLATES[0], duration: 60, deadlineMinutes: 180 },
    u.position,
  );
  e.assign(s, i.id, u.id);
  e.advance(s, 10);
  assert.equal(i.status, "handover");
  assert.equal(u.incidentId, i.id);
  assert.equal(s.history[0].transferred, 1);
  const remaining = i.handover!.remainingWork;
  e.nextDay(s);
  e.startShift(s);
  assert.equal(i.status, "working");
  assert.equal(i.completeAt, s.absoluteSimMinutes + remaining!);
  e.advance(s, remaining!);
  assert(["resolved", "failed"].includes(i.status));
  assert.equal(s.incidents.filter((x) => x.id === i.id).length, 1);
});
test("equipment_not_duplicated and sandbox_unlimited", () => {
  const s = e.newState("career"),
    u = s.units[0],
    before = s.budget;
  e.toggleGear(s, u.id, 0, "medical");
  assert.equal(s.budget, before - 280);
  e.toggleGear(s, u.id, 0, "medical");
  e.toggleGear(s, u.id, 0, "medical");
  assert.equal(s.budget, before - 280);
  assert.equal(u.officers[0].owned.filter((x) => x === "medical").length, 1);
  s.rules.budgetLimited = false;
  s.budget = 0;
  e.toggleGear(s, u.id, 0, "bodycam");
  assert.equal(s.budget, 0);
  assert.equal(u.officers[0].equipped.length, 4);
  assert.throws(() => e.toggleGear(s, u.id, 0, "helmet"));
});
test("incident_chain: branch fires once with bounded steps and decision effects", () => {
  const s = game();
  const u = s.units[0];
  const tpl: IncidentTemplate = {
    id: 0,
    name: "Kette",
    priority: 3,
    needs: ["STREIFE"],
    description: "Testkette",
    duration: 1,
    chain: [
      {
        id: "a",
        name: "Anruf",
        needs: ["STREIFE"],
        priority: 3,
        duration: 1,
        onSuccess: "b",
        onFailure: "b",
      },
      {
        id: "b",
        name: "Nachkontrolle",
        needs: ["KRIPO"],
        priority: 3,
        duration: 5,
      },
    ],
    decisions: [
      {
        id: "secure",
        label: "Zeit investieren",
        durationDelta: 2,
        riskDelta: -0.1,
        cost: 50,
      },
    ],
  };
  const i = e.createIncident(s, tpl, u.position);
  e.decide(s, i.id, "secure");
  assert.equal(i.duration, 3);
  assert.throws(() => e.decide(s, i.id, "secure"));
  e.assign(s, i.id, u.id);
  e.advance(s, 20);
  assert.equal(s.incidents.filter((i) => i.name === "Nachkontrolle").length, 1);
  e.advance(s, 10);
  assert.equal(s.incidents.filter((i) => i.name === "Nachkontrolle").length, 1);
});
test("exhausted personnel rest; long shifts can be configured at every hourly start", () => {
  for (let hour = 0; hour < 24; hour++) {
    const s = e.newState("sandbox");
    planShift(s, "full24", hour);
    s.autoCalls = false;
    e.startShift(s);
    assert.equal(s.shiftStartAbsoluteMinute, hour * 60);
    assert.equal(s.shiftEndAbsoluteMinute, hour * 60 + 1440);
  }
  const s = game();
  s.units[0].officers[0].energy = 10;
  assert.equal(e.available(s, s.units[0]), false);
});
