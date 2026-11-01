import test from "node:test";
import assert from "node:assert/strict";
import {
  contains,
  distanceKm,
  getRegion,
  regions,
  routeBetween,
  samplePosition,
} from "../web/map/regions";
import {
  clusterMarkers,
  defaultFilter,
  filterMarkers,
  type MapMarker,
} from "../web/map/clusters";

test("full_state_bounds_each_region: all sixteen real geometries include known outlying locations", () => {
  assert.equal(regions.length, 16);
  assert.equal(new Set(regions.map((r) => r.id)).size, 16);
  const known: Record<string, [number, number]> = {
    HH: [8.496, 53.919],
    HB: [8.5809, 53.5396],
    NI: [9.9352, 51.5413],
    BY: [13.431, 48.566],
    BE: [13.405, 52.52],
    BB: [14.334, 51.756],
    BW: [7.849, 47.998],
    HE: [9.48, 51.313],
    MV: [13.089, 54.309],
    NW: [6.084, 50.776],
    RP: [6.641, 49.755],
    SL: [6.996, 49.235],
    SN: [14.987, 51.153],
    ST: [11.154, 52.851],
    SH: [8.306, 54.907],
    TH: [12.436, 50.986],
  };
  for (const [id, p] of Object.entries(known)) {
    assert.ok(contains(id, p), id + " contains outlying settlement");
    const b = getRegion(id).bounds;
    assert.ok(
      p[0] >= b[0] && p[0] <= b[2] && p[1] >= b[1] && p[1] <= b[3],
      id + " full bounds",
    );
  }
  const ni = getRegion("NI").bounds;
  assert.ok(ni[0] < 6.7 && ni[2] > 11.5 && ni[1] < 51.4 && ni[3] > 53.8);
  const by = getRegion("BY").bounds;
  assert.ok(by[0] < 9.1 && by[2] > 13.8 && by[1] < 47.3 && by[3] > 50.5);
});

test("incident_within_state: seeded statewide positions never enter another country, exclave or polygon hole", () => {
  let seed = 735;
  const rng = () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 4294967296;
  };
  for (const r of regions) {
    for (let i = 0; i < 100; i++)
      assert.ok(contains(r.id, samplePosition(r.id, rng)), r.id);
    for (const city of r.cities)
      assert.ok(contains(r.id, city.position), r.id + ": " + city.name);
  }
  assert.equal(
    contains("BB", [13.405, 52.52]),
    false,
    "Berlin is a hole in Brandenburg",
  );
  assert.equal(
    contains("NI", [8.8017, 53.0793]),
    false,
    "Bremen is a hole in Niedersachsen",
  );
  assert.equal(contains("NI", [13.405, 52.52]), false);
  assert.equal(contains("HH", [NaN, 53]), false);
});

test("routing_cross_district: Hannover–Oldenburg ETA derives from geographic distance and vehicle speed", () => {
  const hannover: [number, number] = [9.732, 52.3759],
    oldenburg: [number, number] = [8.2146, 53.1435];
  const distance = distanceKm(hannover, oldenburg);
  assert.ok(distance > 130 && distance < 135);
  const route = routeBetween(hannover, oldenburg, 64);
  assert.equal(route.approximate, true);
  assert.ok(route.distanceKm > 166 && route.distanceKm < 173);
  assert.ok(route.durationMinutes > 155 && route.durationMinutes < 163);
  assert.deepEqual(route.points[0], hannover);
  assert.deepEqual(route.points.at(-1), oldenburg);
  assert.equal(
    routeBetween(hannover, oldenburg, 32).durationMinutes,
    route.durationMinutes * 2,
  );
  assert.throws(() => routeBetween(hannover, oldenburg, 0));
});

test("hamburg_main_road_graph: actual offline road geometry produces a connected A* approach", () => {
  const centre: [number, number] = [9.9937, 53.5511],
    harburg: [number, number] = [9.982, 53.461];
  const route = routeBetween(centre, harburg, 60);
  assert.equal(route.basis, "main-roads");
  assert.ok(route.points.length > 20);
  assert.ok(
    route.distanceKm > 12 && route.distanceKm < 25,
    "Hamburg centre to Harburg via main roads",
  );
  assert.deepEqual(route.points[0], centre);
  assert.deepEqual(route.points.at(-1), harburg);
  assert.equal(
    route.durationMinutes,
    route.distanceKm,
    "60 km/h means one minute per kilometre",
  );
  const island = routeBetween([8.496, 53.919], centre, 60);
  assert.equal(
    island.basis,
    "distance-model",
    "Neuwerk has no road connection",
  );
});

test("cluster_open_and_filter: hundreds of objects remain counted and individually accessible", () => {
  const markers: MapMarker[] = Array.from({ length: 300 }, (_, i) => ({
    id: i,
    key: "unit:" + i,
    kind: "unit",
    name: "Einheit " + i,
    position: [9, 52],
    type: i % 3 === 0 ? "SEK" : i % 3 === 1 ? "MEK" : "STREIFE",
    status: i % 2 ? "free" : "enroute",
    x: (i % 20) * 3,
    y: Math.floor(i / 20) * 3,
  }));
  const clusters = clusterMarkers(markers, 40);
  assert.equal(clusters.flatMap((c) => c.members).length, 300);
  assert.equal(
    new Set(clusters.flatMap((c) => c.members.map((m) => m.id))).size,
    300,
  );
  assert.ok(clusters.some((c) => c.members.length > 1));
  assert.equal(
    clusters.reduce((n, c) => n + c.units, 0),
    300,
  );
  const sek = filterMarkers(markers, {
    ...defaultFilter,
    type: "SEK",
    availability: "free",
  });
  assert.equal(sek.length, 50);
  assert.ok(sek.every((m) => m.type === "SEK" && m.status === "free"));
  const call: MapMarker = {
    id: "p1",
    key: "incident:p1",
    kind: "incident",
    name: "Notruf",
    position: [9, 52],
    status: "open",
    priority: 1,
    x: 0,
    y: 0,
  };
  const mixed = clusterMarkers([...markers, call], 40);
  assert.equal(mixed.find((c) => c.members.includes(call))?.priority, 1);
  assert.equal(
    mixed.reduce((n, c) => n + c.incidents, 0),
    1,
  );
  assert.deepEqual(
    filterMarkers([call], { ...defaultFilter, priority: "2" }),
    [],
  );
  const neighbor = [
    { ...call, key: "a", x: 39, y: 0 },
    { ...call, key: "b", x: 41, y: 0 },
  ];
  assert.equal(
    clusterMarkers(neighbor, 40).length,
    1,
    "Neighboring cells cannot hide overlapping pins",
  );
  const ready = { ...markers[0], status: "ready" };
  assert.equal(
    filterMarkers([ready], { ...defaultFilter, availability: "free" }).length,
    1,
  );
});
