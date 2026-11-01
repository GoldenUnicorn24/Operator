import test from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { BASE_TEMPLATES, COURSES, GEAR, UNIT_TYPES } from "../web/data/catalog";

test("catalog has forty distinct playable incidents, correct requirements, and Alpha IDs", () => {
  assert.ok(BASE_TEMPLATES.length >= 40);
  assert.equal(
    new Set(BASE_TEMPLATES.map((t) => t.id)).size,
    BASE_TEMPLATES.length,
  );
  assert.equal(
    new Set(BASE_TEMPLATES.map((t) => t.name)).size,
    BASE_TEMPLATES.length,
  );
  assert.deepEqual(
    BASE_TEMPLATES.slice(0, 9).map((t) => t.name),
    [
      "Ruhestörung",
      "Verkehrsunfall",
      "Ladendiebstahl",
      "Bewaffnete Bedrohung",
      "Observation",
      "Einbruchserie",
      "Großveranstaltung",
      "Geisellage",
      "Tatortaufnahme",
    ],
  );
  for (const t of BASE_TEMPLATES) {
    assert.ok([1, 2, 3].includes(t.priority));
    assert.ok(
      t.needs.length >= 1 && t.needs.every((type) => type in UNIT_TYPES),
    );
    assert.ok(t.duration > 0 && t.deadlineMinutes > 0 && t.reward >= 0);
    assert.ok(t.description.length > 40);
    if (t.followUp) assert.ok(t.followUp.type in UNIT_TYPES);
    for (const decision of t.decisions ?? []) {
      assert.ok(Number.isFinite(decision.riskDelta));
      assert.ok(t.duration + decision.durationDelta > 0);
      assert.ok(decision.cost >= 0);
    }
  }
  assert.ok(BASE_TEMPLATES.some((t) => t.regionId === "HH"));
  for (const role of Object.keys(UNIT_TYPES))
    assert.ok(
      BASE_TEMPLATES.some((t) => t.needs.includes(role)),
      role,
    );
});

test("optional multi-day training preserves Alpha course IDs and has eight courses", () => {
  assert.ok(COURSES.length >= 8);
  for (const id of ["comms", "firstaid", "team", "sek", "mek"])
    assert.ok(COURSES.some((c) => c.id === id));
  assert.equal(new Set(COURSES.map((c) => c.id)).size, COURSES.length);
  assert.ok(COURSES.every((c) => c.days >= 2 && c.cost > 0 && c.skill > 0));
  assert.equal(COURSES.find((c) => c.id === "comms")?.days, 2);
});

test("SEK and MEK have different competencies, readiness, vehicles, and role content", () => {
  assert.ok(
    UNIT_TYPES.SEK.dispatchDelayMinutes >
      UNIT_TYPES.STREIFE.dispatchDelayMinutes,
  );
  assert.ok(UNIT_TYPES.MEK.capabilities.includes("Observation"));
  assert.ok(!UNIT_TYPES.SEK.capabilities.includes("Observation"));
  assert.notEqual(UNIT_TYPES.MEK.image, UNIT_TYPES.STREIFE.image);
  assert.notEqual(UNIT_TYPES.MEK.image, UNIT_TYPES.SEK.image);
  assert.ok(UNIT_TYPES.HELI.speedKmh > 2 * UNIT_TYPES.STREIFE.speedKmh);
});

test("equipment and fleet references resolve to bundled original graphics", () => {
  const base = "app/src/main/assets/";
  for (const gear of GEAR) assert.ok(existsSync(base + gear.image), gear.image);
  for (const role of Object.values(UNIT_TYPES))
    assert.ok(existsSync(base + role.image), role.image);
  for (const id of [
    "radio",
    "vest",
    "medical",
    "bodycam",
    "helmet",
    "shield",
    "camera",
    "drone",
  ]) {
    assert.ok(GEAR.some((g) => g.id === id));
  }
});
