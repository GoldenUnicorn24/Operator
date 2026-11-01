import geometry from "../data/geo/germany-states.json";
import { haversineKm } from "./geoMath";
import { hamburgRoadRoute } from "./roadGraph";

export type Position = [number, number];
export type Boundary =
  | { type: "Polygon"; coordinates: Position[][] }
  | { type: "MultiPolygon"; coordinates: Position[][][] };
export interface City {
  name: string;
  position: Position;
}
export interface Region {
  id: string;
  name: string;
  boundary: Boundary;
  bounds: [number, number, number, number];
  cities: City[];
}

// WGS84 settlement centres, editorial geographic facts; not purported police stations.
const cities: Record<string, [string, number, number][]> = {
  SH: [
    ["Kiel", 10.1228, 54.3233],
    ["Lübeck", 10.6866, 53.8655],
    ["Flensburg", 9.4469, 54.7937],
    ["Husum", 9.051, 54.485],
    ["Itzehoe", 9.516, 53.925],
    ["Neumünster", 9.984, 54.072],
    ["Heide", 9.09, 54.196],
    ["Norderstedt", 9.999, 53.685],
    ["Westerland", 8.306, 54.907],
  ],
  HH: [
    ["Hamburg", 9.9937, 53.5511],
    ["Altona", 9.935, 53.553],
    ["Harburg", 9.982, 53.461],
    ["Bergedorf", 10.211, 53.489],
    ["Wandsbek", 10.073, 53.573],
    ["Blankenese", 9.813, 53.558],
    ["Finkenwerder", 9.858, 53.533],
    ["Eppendorf", 9.981, 53.594],
    ["Rahlstedt", 10.156, 53.603],
    ["Billstedt", 10.107, 53.539],
    ["Neuwerk", 8.496, 53.919],
  ],
  HB: [
    ["Bremen", 8.8017, 53.0793],
    ["Bremerhaven", 8.5809, 53.5396],
    ["Bremen-Nord", 8.622, 53.17],
    ["Hemelingen", 8.896, 53.048],
  ],
  NI: [
    ["Hannover", 9.732, 52.3759],
    ["Oldenburg", 8.2146, 53.1435],
    ["Lüneburg", 10.407, 53.2487],
    ["Braunschweig", 10.5268, 52.2689],
    ["Osnabrück", 8.0472, 52.2799],
    ["Göttingen", 9.9352, 51.5413],
    ["Cuxhaven", 8.694, 53.859],
    ["Wilhelmshaven", 8.108, 53.528],
    ["Emden", 7.206, 53.367],
    ["Celle", 10.08, 52.622],
    ["Goslar", 10.429, 51.906],
    ["Wolfsburg", 10.787, 52.423],
    ["Aurich", 7.483, 53.469],
    ["Lingen", 7.317, 52.522],
    ["Stade", 9.472, 53.6],
    ["Uelzen", 10.561, 52.965],
  ],
  MV: [
    ["Schwerin", 11.416, 53.629],
    ["Rostock", 12.1, 54.092],
    ["Stralsund", 13.089, 54.309],
    ["Greifswald", 13.384, 54.096],
    ["Neubrandenburg", 13.261, 53.558],
    ["Wismar", 11.466, 53.893],
    ["Güstrow", 12.178, 53.794],
    ["Pasewalk", 13.99, 53.506],
    ["Waren", 12.684, 53.522],
    ["Bergen auf Rügen", 13.433, 54.418],
  ],
  BB: [
    ["Potsdam", 13.064, 52.401],
    ["Cottbus", 14.334, 51.756],
    ["Frankfurt (Oder)", 14.551, 52.347],
    ["Brandenburg an der Havel", 12.549, 52.411],
    ["Oranienburg", 13.237, 52.755],
    ["Eberswalde", 13.82, 52.834],
    ["Prenzlau", 13.863, 53.316],
    ["Neuruppin", 12.807, 52.928],
    ["Perleberg", 11.863, 53.075],
    ["Senftenberg", 14.005, 51.519],
    ["Luckenwalde", 13.174, 52.09],
  ],
  BE: [
    ["Berlin-Mitte", 13.405, 52.52],
    ["Spandau", 13.197, 52.536],
    ["Köpenick", 13.576, 52.446],
    ["Marzahn", 13.557, 52.545],
    ["Pankow", 13.403, 52.569],
    ["Steglitz", 13.32, 52.457],
    ["Tempelhof", 13.384, 52.467],
    ["Charlottenburg", 13.304, 52.517],
  ],
  ST: [
    ["Magdeburg", 11.6276, 52.1205],
    ["Halle (Saale)", 11.97, 51.482],
    ["Dessau-Roßlau", 12.242, 51.838],
    ["Stendal", 11.858, 52.606],
    ["Halberstadt", 11.057, 51.896],
    ["Wernigerode", 10.785, 51.836],
    ["Wittenberg", 12.649, 51.867],
    ["Naumburg", 11.805, 51.152],
    ["Salzwedel", 11.154, 52.851],
  ],
  NW: [
    ["Düsseldorf", 6.7735, 51.2277],
    ["Köln", 6.96, 50.938],
    ["Dortmund", 7.466, 51.514],
    ["Essen", 7.012, 51.455],
    ["Duisburg", 6.763, 51.434],
    ["Bielefeld", 8.532, 52.023],
    ["Münster", 7.626, 51.963],
    ["Aachen", 6.084, 50.776],
    ["Bonn", 7.099, 50.737],
    ["Siegen", 8.023, 50.874],
    ["Paderborn", 8.755, 51.719],
    ["Kleve", 6.137, 51.789],
    ["Detmold", 8.874, 51.938],
    ["Winterberg", 8.53, 51.194],
  ],
  HE: [
    ["Wiesbaden", 8.241, 50.083],
    ["Frankfurt am Main", 8.682, 50.111],
    ["Kassel", 9.48, 51.313],
    ["Darmstadt", 8.652, 49.872],
    ["Gießen", 8.675, 50.585],
    ["Marburg", 8.77, 50.809],
    ["Fulda", 9.677, 50.555],
    ["Hanau", 8.917, 50.127],
    ["Limburg", 8.068, 50.383],
    ["Bad Hersfeld", 9.708, 50.871],
  ],
  TH: [
    ["Erfurt", 11.029, 50.979],
    ["Jena", 11.589, 50.928],
    ["Gera", 12.082, 50.878],
    ["Weimar", 11.329, 50.98],
    ["Eisenach", 10.32, 50.975],
    ["Nordhausen", 10.789, 51.501],
    ["Suhl", 10.693, 50.61],
    ["Meiningen", 10.415, 50.568],
    ["Altenburg", 12.436, 50.986],
    ["Saalfeld", 11.36, 50.648],
  ],
  SN: [
    ["Dresden", 13.737, 51.05],
    ["Leipzig", 12.374, 51.34],
    ["Chemnitz", 12.921, 50.833],
    ["Zwickau", 12.496, 50.718],
    ["Görlitz", 14.987, 51.153],
    ["Bautzen", 14.424, 51.181],
    ["Plauen", 12.139, 50.497],
    ["Freiberg", 13.343, 50.91],
    ["Torgau", 13.004, 51.56],
    ["Annaberg-Buchholz", 13.003, 50.58],
  ],
  RP: [
    ["Mainz", 8.248, 49.992],
    ["Koblenz", 7.588, 50.356],
    ["Trier", 6.641, 49.755],
    ["Kaiserslautern", 7.769, 49.444],
    ["Ludwigshafen", 8.435, 49.477],
    ["Worms", 8.362, 49.634],
    ["Landau", 8.113, 49.198],
    ["Idar-Oberstein", 7.317, 49.714],
    ["Bad Kreuznach", 7.867, 49.844],
    ["Bitburg", 6.527, 49.975],
  ],
  SL: [
    ["Saarbrücken", 6.996, 49.235],
    ["Neunkirchen", 7.179, 49.345],
    ["Homburg", 7.336, 49.326],
    ["Saarlouis", 6.752, 49.313],
    ["Merzig", 6.636, 49.443],
    ["St. Wendel", 7.17, 49.466],
    ["Völklingen", 6.858, 49.252],
  ],
  BW: [
    ["Stuttgart", 9.1829, 48.7758],
    ["Karlsruhe", 8.404, 49.006],
    ["Mannheim", 8.467, 49.489],
    ["Freiburg", 7.849, 47.998],
    ["Heidelberg", 8.693, 49.399],
    ["Ulm", 9.991, 48.401],
    ["Heilbronn", 9.218, 49.142],
    ["Konstanz", 9.175, 47.664],
    ["Tübingen", 9.057, 48.521],
    ["Offenburg", 7.942, 48.473],
    ["Aalen", 10.093, 48.837],
    ["Villingen-Schwenningen", 8.45, 48.059],
    ["Ravensburg", 9.611, 47.782],
  ],
  BY: [
    ["München", 11.5761, 48.1372],
    ["Nürnberg", 11.0767, 49.4521],
    ["Augsburg", 10.898, 48.37],
    ["Regensburg", 12.097, 49.019],
    ["Würzburg", 9.932, 49.792],
    ["Bayreuth", 11.578, 49.945],
    ["Bamberg", 10.892, 49.899],
    ["Hof", 11.913, 50.314],
    ["Passau", 13.431, 48.566],
    ["Landshut", 12.152, 48.544],
    ["Rosenheim", 12.128, 47.856],
    ["Kempten", 10.314, 47.728],
    ["Lindau", 9.684, 47.55],
    ["Aschaffenburg", 9.145, 49.977],
    ["Ingolstadt", 11.424, 48.766],
    ["Garmisch-Partenkirchen", 11.096, 47.493],
  ],
};

export function polygons(boundary: Boundary): Position[][][] {
  return boundary.type === "Polygon"
    ? [boundary.coordinates]
    : boundary.coordinates;
}
export function geometryBounds(
  boundary: Boundary,
): [number, number, number, number] {
  const points = polygons(boundary).flat(2);
  return [
    Math.min(...points.map((p) => p[0])),
    Math.min(...points.map((p) => p[1])),
    Math.max(...points.map((p) => p[0])),
    Math.max(...points.map((p) => p[1])),
  ];
}
export const regions: Region[] = (
  geometry as { id: string; name: string; boundary: Boundary }[]
).map((r) => ({
  ...r,
  bounds: geometryBounds(r.boundary),
  cities: (cities[r.id] || []).map(([name, lon, lat]) => ({
    name,
    position: [lon, lat] as Position,
  })),
}));
export function getRegion(id: string): Region {
  const region = regions.find((r) => r.id === id || r.name === id);
  if (!region) throw new Error("Unbekanntes Bundesland: " + id);
  return region;
}
function inRing(point: Position, ring: Position[]): boolean {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const a = ring[i],
      b = ring[j];
    const cross =
      (point[0] - a[0]) * (b[1] - a[1]) - (point[1] - a[1]) * (b[0] - a[0]);
    if (
      Math.abs(cross) < 1e-10 &&
      point[0] >= Math.min(a[0], b[0]) - 1e-10 &&
      point[0] <= Math.max(a[0], b[0]) + 1e-10 &&
      point[1] >= Math.min(a[1], b[1]) - 1e-10 &&
      point[1] <= Math.max(a[1], b[1]) + 1e-10
    )
      return true;
    if (
      a[1] > point[1] !== b[1] > point[1] &&
      point[0] < ((b[0] - a[0]) * (point[1] - a[1])) / (b[1] - a[1]) + a[0]
    )
      inside = !inside;
  }
  return inside;
}
export function contains(id: string, point: Position): boolean {
  if (
    !Array.isArray(point) ||
    point.length !== 2 ||
    !point.every(Number.isFinite)
  )
    return false;
  const r = getRegion(id),
    b = r.bounds;
  if (point[0] < b[0] || point[0] > b[2] || point[1] < b[1] || point[1] > b[3])
    return false;
  return polygons(r.boundary).some(
    (p) => inRing(point, p[0]) && !p.slice(1).some((h) => inRing(point, h)),
  );
}
export function samplePosition(
  id: string,
  rng: () => number = Math.random,
): Position {
  const region = getRegion(id),
    b = region.bounds;
  for (let i = 0; i < 20000; i++) {
    const p: Position = [
      b[0] + rng() * (b[2] - b[0]),
      b[1] + rng() * (b[3] - b[1]),
    ];
    if (contains(id, p)) return p;
  }
  // Degenerate deterministic RNGs can miss a disconnected island; use a validated settlement.
  const centre = region.cities.find((c) => contains(id, c.position));
  if (centre) return [...centre.position];
  throw new Error("Kein gültiger Standort im Bundesland gefunden.");
}
export const distanceKm = haversineKm;
export function routeBetween(
  a: Position,
  b: Position,
  speedKmh = 60,
): {
  points: Position[];
  distanceKm: number;
  durationMinutes: number;
  approximate: true;
  basis: "main-roads" | "distance-model";
} {
  if (![...a, ...b, speedKmh].every(Number.isFinite) || speedKmh <= 0)
    throw new Error("Ungültige Fahrdaten.");
  if (contains("HH", a) && contains("HH", b)) {
    const points = hamburgRoadRoute(a, b);
    if (points) {
      const distance = points
        .slice(1)
        .reduce((sum, p, i) => sum + distanceKm(points[i], p), 0);
      return {
        points,
        distanceKm: distance,
        durationMinutes: Math.max(1, (distance / speedKmh) * 60),
        approximate: true,
        basis: "main-roads",
      };
    }
  }
  // Road detour factor, no claim to traffic data or navigable turn-by-turn routing.
  const distance = distanceKm(a, b) * 1.28;
  const points: Position[] = Array.from({ length: 17 }, (_, n) => [
    a[0] + ((b[0] - a[0]) * n) / 16,
    a[1] + ((b[1] - a[1]) * n) / 16,
  ]);
  return {
    points,
    distanceKm: distance,
    durationMinutes: Math.max(1, (distance / speedKmh) * 60),
    approximate: true,
    basis: "distance-model",
  };
}
