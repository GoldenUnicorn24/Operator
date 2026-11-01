import test from "node:test";
import assert from "node:assert/strict";
import { newState } from "../web/domain/engine";
import { parseSave } from "../web/storage/repository";
import { validateTemplate, importContent } from "../web/editor/schema";
import { BASE_TEMPLATES } from "../web/data/catalog";

test("untrusted imported IDs cannot become inline event-handler code", () => {
  const payload = "x');globalThis.importExecuted=1;//";
  const decision = {
    id: payload,
    label: "Imported choice",
    durationDelta: 0,
    riskDelta: 0,
    cost: 0,
  };
  assert.throws(
    () => validateTemplate({ ...BASE_TEMPLATES[0], decisions: [decision] }),
    /ID/,
  );
  assert.throws(
    () =>
      validateTemplate({
        ...BASE_TEMPLATES[0],
        chain: [
          {
            id: payload,
            name: "Imported stage",
            needs: ["STREIFE"],
            duration: 10,
            priority: 3,
          },
        ],
      }),
    /ID/,
  );
  assert.throws(
    () => validateTemplate({ ...BASE_TEMPLATES[0], id: payload }),
    /ID/,
  );
  const state = newState("sandbox");
  state.facilities[0].id = payload;
  assert.throws(() => parseSave(JSON.stringify(state)), /Dienststelle/);
  assert.equal(
    (globalThis as Record<string, unknown>).importExecuted,
    undefined,
  );
});

test("unsafe content IDs are rejected atomically but valid IDs and plain text remain importable", () => {
  const state = newState("sandbox");
  const before = JSON.stringify(state);
  const base = { ...BASE_TEMPLATES[0], id: 0 };
  const wrap = (templates: unknown[]) =>
    JSON.stringify({ schemaVersion: 1, kind: "el110-content", templates });
  assert.throws(() =>
    importContent(
      state,
      wrap([
        base,
        {
          ...base,
          decisions: [
            {
              id: 'a" onclick="alert(1)',
              label: "Bad",
              durationDelta: 0,
              riskDelta: 0,
              cost: 0,
            },
          ],
        },
      ]),
    ),
  );
  assert.equal(JSON.stringify(state), before);
  assert.equal(
    importContent(
      state,
      wrap([
        {
          ...base,
          name: "<script>plain text</script>",
          decisions: [
            {
              id: "document_1-safe",
              label: "O'Brien: Gespräch",
              durationDelta: 5,
              riskDelta: -0.1,
              cost: 0,
            },
          ],
        },
      ]),
    ),
    1,
  );
  assert.equal(state.templates.at(-1)?.decisions?.[0].id, "document_1-safe");
});

test("24h personnel without relief finish a scheduled rest instead of postponing it every tick", async () => {
  const engine = await import("../web/domain/engine");
  const { planShift } = await import("../web/domain/clock");
  const state = newState("sandbox", "NI");
  state.autoCalls = false;
  const unit = state.units[0];
  // Alpha migration intentionally does not invent purchased relief teams.
  unit.reserveOfficers = [];
  planShift(state, "full24", 6);
  engine.startShift(state);
  engine.advance(state, 480);
  assert.equal(unit.status, "resting");
  const plannedEnd = unit.restUntil;
  engine.advance(state, 1);
  assert.equal(
    unit.restUntil,
    plannedEnd,
    "the scheduled rest end must remain fixed",
  );
  engine.advance(state, 260);
  assert.equal(unit.status, "ready");
  assert.ok(
    unit.officers[0].dutyMinutes < 30,
    "a completed rest resets accumulated continuous duty",
  );
});

test("bad route and availability numbers are rejected before an import can poison subsequent saves", async () => {
  const engine = await import("../web/domain/engine");
  const state = newState("sandbox", "NI");
  state.autoCalls = false;
  engine.startShift(state);
  const incident = engine.newIncident(state, 1);
  engine.assign(state, incident.id, state.units[0].id);
  for (const key of ["distanceKm", "departureAt"]) {
    const malformed = structuredClone(state) as any;
    delete malformed.units[0].route[key];
    assert.throws(() => parseSave(JSON.stringify(malformed)), key);
  }
  for (const key of ["restUntil", "readinessAt"]) {
    const malformed = structuredClone(state) as any;
    malformed.units[0][key] = "not-a-number";
    assert.throws(() => parseSave(JSON.stringify(malformed)), key);
  }
});

test("imported allocation sequence cannot collide with existing unit or vehicle IDs", () => {
  const state = newState("sandbox", "NI");
  state.seq = state.units[0].id - 1;
  assert.throws(
    () => parseSave(JSON.stringify(state)),
    "a future createUnit would duplicate an existing unit ID",
  );
});

test("a multi-stage incident starts with the first stage requirements, priority, duration and name", async () => {
  const engine = await import("../web/domain/engine");
  const state = newState("sandbox", "NI");
  state.autoCalls = false;
  engine.startShift(state);
  const incident = engine.createIncident(
    state,
    {
      id: 0,
      name: "Overall scenario",
      description: "A multi-stage management scenario.",
      priority: 3,
      needs: ["STREIFE"],
      duration: 1,
      chain: [
        {
          id: "initial",
          name: "Initial investigation",
          priority: 1,
          needs: ["KRIPO"],
          duration: 100,
          onSuccess: "next",
        },
        {
          id: "next",
          name: "Observation follow-up",
          priority: 2,
          needs: ["MEK"],
          duration: 30,
        },
      ],
    },
    state.units[0].position,
  );
  assert.equal(incident.name, "Initial investigation");
  assert.deepEqual(incident.needs, ["KRIPO"]);
  assert.equal(incident.duration, 100);
  assert.equal(incident.priority, 1);
});

test("deadline failure follows the configured failure branch exactly once", async () => {
  const engine = await import("../web/domain/engine");
  const state = newState("sandbox", "NI");
  state.autoCalls = false;
  engine.startShift(state);
  const incident = engine.createIncident(
    state,
    {
      id: 0,
      name: "Initial call",
      description: "The unanswered call has a documented escalation.",
      priority: 3,
      needs: ["STREIFE"],
      duration: 10,
      deadlineMinutes: 10,
      chain: [
        {
          id: "initial",
          name: "Initial call",
          priority: 3,
          needs: ["STREIFE"],
          duration: 10,
          onFailure: "escalation",
        },
        {
          id: "escalation",
          name: "Escalation after deadline",
          priority: 1,
          needs: ["SEK"],
          duration: 30,
        },
      ],
    },
    state.units[0].position,
  );
  engine.advance(state, 11);
  assert.equal(incident.status, "failed");
  assert.equal(
    state.incidents.filter((i) => i.name === "Escalation after deadline")
      .length,
    1,
  );
  engine.advance(state, 1);
  assert.equal(
    state.incidents.filter((i) => i.name === "Escalation after deadline")
      .length,
    1,
  );
});

test("malformed debrief report is rejected before it can block the next-day button", () => {
  const state = newState("sandbox");
  state.stage = "debrief";
  (state as any).history = [{ day: 1, budget: 0, trainingDone: [] }];
  assert.throws(() => parseSave(JSON.stringify(state)));
});

test("all shift presets remain saveable while planning before the first tick", async () => {
  const { planShift } = await import("../web/domain/clock");
  const state = newState("sandbox");
  for (const preset of [
    "early",
    "late",
    "night",
    "day12",
    "night12",
    "full24",
  ]) {
    planShift(state, preset);
    assert.doesNotThrow(() => parseSave(JSON.stringify(state)));
  }
});
