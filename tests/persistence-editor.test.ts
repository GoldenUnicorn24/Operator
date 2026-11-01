import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import * as e from "../web/domain/engine";
import {
  SaveRepository,
  parseSave,
  validateState,
} from "../web/storage/repository";
import * as editor from "../web/editor/schema";
import { BASE_TEMPLATES } from "../web/data/catalog";
const fixture = readFileSync(
  new URL("./fixtures/alpha02.json", import.meta.url),
  "utf8",
);
class Memory {
  data = new Map<string, string>();
  getItem(k: string) {
    return this.data.get(k) ?? null;
  }
  setItem(k: string, v: string) {
    this.data.set(k, v);
  }
  removeItem(k: string) {
    this.data.delete(k);
  }
}
test("save_roundtrip: real routes, roster, reports, custom state and slots", () => {
  const s = e.newState("sandbox", "NI");
  s.autoCalls = false;
  e.startShift(s);
  const i = e.newIncident(s, 1);
  e.assign(s, i.id, s.units[0].id);
  e.advance(s, 10);
  const storage = new Memory(),
    repo = new SaveRepository(storage);
  repo.save(s);
  assert.deepEqual(repo.load(), s);
  repo.save(s, "1");
  e.advance(s, 10);
  repo.save(s, "2");
  assert.notDeepEqual(
    repo.load("1")!.absoluteSimMinutes,
    repo.load("2")!.absoluteSimMinutes,
  );
});
test("save_migration_alpha02: authentic fixture, all relevant fields preserved and old key untouched", () => {
  const old = JSON.parse(fixture);
  old.units[0].officers[0].xp = 47;
  old.units[0].training = { courseId: "comms", remainingDays: 1 };
  old.templates.push({
    id: 901,
    name: "Eigener Alpha-Einsatz",
    priority: 2,
    needs: ["STREIFE", "KRIPO"],
    duration: 20,
    description: "Eigene Lage",
    followUp: { name: "Nachkontrolle", priority: 3, type: "KRIPO" },
  });
  const raw = JSON.stringify(old),
    storage = new Memory();
  storage.setItem("el110-save-v2", raw);
  const repo = new SaveRepository(storage),
    s = repo.load()!;
  assert.equal(s.schemaVersion, 3);
  assert.equal(s.units.length, old.units.length);
  assert.equal(s.budget, old.budget);
  assert.equal(s.units[0].officers[0].xp, 47);
  assert.deepEqual(
    s.units[0].officers.map((o) => o.name),
    old.units[0].officers.map((o: any) => o.name),
  );
  assert.deepEqual(s.units[0].training, old.units[0].training);
  assert(
    s.templates.some((t) => t.id === 901 && t.name === "Eigener Alpha-Einsatz"),
  );
  assert(s.templates.length >= 40);
  repo.save(s);
  assert.equal(storage.getItem("el110-save-v2"), raw);
  assert.deepEqual(repo.load(), s);
});
test("save_migration_alpha02 running assignments and debrief imports", () => {
  const old = JSON.parse(fixture);
  old.stage = "running";
  old.elapsed = 100;
  old.units[0].incidentId = 111;
  old.incidents = [
    {
      ...old.templates[0],
      id: 111,
      status: "working",
      created: 80,
      assignedIds: [old.units[0].id],
      completeAt: 110,
      region: old.region,
      followUp: null,
    },
  ];
  const s = parseSave(JSON.stringify(old));
  assert.equal(s.incidents[0].completeAt, s.shiftStartAbsoluteMinute + 110);
  assert.deepEqual(s.units[0].position, s.incidents[0].position);
  assert.equal(s.units[0].incidentId, 111);
  assert.equal(s.paused, true);
  s.paused = false;
  e.advance(s, 10);
  assert(
    ["resolved", "failed"].includes(
      s.incidents.find((i) => i.id === 111)!.status,
    ),
  );
});
test("bad import atomic, backup recovery, malformed nested data rejected", () => {
  const storage = new Memory(),
    repo = new SaveRepository(storage),
    s = e.newState();
  repo.save(s);
  const before = storage.getItem("el110-save-v3");
  for (const bad of [
    "{}",
    "not JSON",
    JSON.stringify({ ...s, units: [{ id: 1 }] }),
    JSON.stringify({ ...s, shiftDurationMinutes: 500 }),
    JSON.stringify({ ...s, budget: null }),
  ]) {
    assert.throws(() => repo.import(bad));
    assert.equal(storage.getItem("el110-save-v3"), before);
  }
  repo.save(s);
  storage.setItem("el110-save-v3", "corrupted");
  assert.deepEqual(repo.load(), s);
  repo.save(s);
  assert.deepEqual(repo.load(), s);
});
test("custom_editor_validation: dangling reference, cycles, coordinates and transaction checks", () => {
  const s = e.newState("sandbox", "NI"),
    t = {
      ...BASE_TEMPLATES[0],
      chain: [
        {
          id: "a",
          name: "A",
          priority: 3,
          needs: ["STREIFE"],
          duration: 10,
          onSuccess: "missing",
        },
      ],
    };
  assert.throws(() => editor.saveTemplate(s, t));
  t.chain[0].onSuccess = "a";
  assert.throws(() => editor.saveTemplate(s, t));
  assert.throws(() =>
    editor.saveTemplate(s, {
      ...BASE_TEMPLATES[0],
      regionId: "NI",
      position: [11.576, 48.137],
    }),
  );
  const count = s.templates.length;
  assert.throws(() =>
    editor.importContent(
      s,
      JSON.stringify({
        schemaVersion: 1,
        kind: "el110-content",
        templates: [BASE_TEMPLATES[0], t],
      }),
    ),
  );
  assert.equal(s.templates.length, count);
  editor.saveTemplate(s, { ...BASE_TEMPLATES[0], name: "Meine Lage" }, 1);
  assert.equal(s.templates[0].name, "Meine Lage");
  const raw = editor.exportContent(s);
  const clone = e.newState("sandbox");
  assert.equal(editor.importContent(clone, raw), s.templates.length);
});
test("vehicle and unit editor apply cost, capacity and real geographic state", () => {
  const s = e.newState("career", "NI");
  const u = editor.createUnit(s, {
    name: "Neue Streife",
    type: "STREIFE",
    crew: 2,
    position: [9.732, 52.3759],
    avatar: 2,
  });
  assert.deepEqual(u.basePosition, [9.732, 52.3759]);
  assert.equal(u.reserveOfficers.length, 2);
  const before = s.budget;
  editor.editVehicle(s, u.id, {
    ...u.vehicle,
    speedKmh: 80,
    cost: u.vehicle.cost + 1000,
  });
  assert.equal(u.vehicle.speedKmh, 80);
  assert.equal(s.budget, before - 1000);
  assert.throws(() =>
    editor.editVehicle(s, u.id, { ...u.vehicle, capacity: 1 }),
  );
  u.vehicle.condition = 70;
  e.maintainVehicle(s, u.id);
  assert.equal(u.vehicle.condition, 100);
  validateState(s);
});

test("all sixteen regions, backups and three save slots fit within the Android localStorage budget", async () => {
  const { regions } = await import("../web/map/regions");
  const s = e.newState("sandbox");
  for (const r of regions) e.ensureRegion(s, r.id);
  const storage = new Memory(),
    repo = new SaveRepository(storage);
  for (const slot of ["autosave", "1", "2", "3"]) {
    repo.save(s, slot);
    repo.save(s, slot);
    assert.deepEqual(repo.load(slot), s);
  }
  const bytes = [...storage.data.entries()].reduce(
    (sum, [k, v]) => sum + (k.length + v.length) * 2,
    0,
  );
  assert(bytes < 4 * 1024 * 1024, `compressed local saves use ${bytes} bytes`);
});

test("career procurement unlock and unit-role edits change real requirements without losing personnel", () => {
  const s = e.newState("career", "NI");
  const input = {
    name: "Luftunterstützung",
    type: "HELI" as const,
    crew: 2,
    position: [9.732, 52.3759] as [number, number],
    avatar: 0,
  };
  assert.throws(() => editor.createUnit(s, input), /3 abgeschlossenen/);
  s.completedShifts = 3;
  s.reputation = 65;
  s.budget = 1000000;
  assert.equal(editor.createUnit(s, input).type, "HELI");
  const u = s.units[0],
    before = u.officers.map((o) => o.id);
  u.vehicle.capacity = 4;
  editor.editUnit(s, u.id, {
    name: "Verkehr neu",
    type: "VERKEHR",
    crew: 3,
    avatar: 1,
  });
  assert.equal(u.type, "VERKEHR");
  assert.equal(u.officers.length, 3);
  assert.deepEqual(
    u.officers.slice(0, 2).map((o) => o.id),
    before,
  );
  assert.equal(u.reserveOfficers[0].length, 3);
  validateState(s);
});
