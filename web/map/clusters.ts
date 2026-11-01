import type { Position } from "./regions";

export interface MapMarker {
  id: string | number;
  key: string;
  kind: "unit" | "incident" | "facility";
  name: string;
  position: Position;
  type?: string;
  status?: string;
  priority?: number;
  x: number;
  y: number;
}
export interface MarkerFilter {
  type: string;
  availability: string;
  incidents: string;
  priority: string;
  facilities: boolean;
}
export interface MarkerCluster {
  key: string;
  members: MapMarker[];
  x: number;
  y: number;
  units: number;
  incidents: number;
  facilities: number;
  priority: number;
}
export const defaultFilter: MarkerFilter = {
  type: "all",
  availability: "all",
  incidents: "active",
  priority: "all",
  facilities: true,
};
export function filterMarkers(
  markers: MapMarker[],
  filter: MarkerFilter,
): MapMarker[] {
  return markers.filter((m) => {
    if (m.kind === "facility") return filter.facilities;
    if (m.kind === "unit")
      return (
        (filter.type === "all" || m.type === filter.type) &&
        (filter.availability === "all" ||
          (filter.availability === "free"
            ? ["free", "available", "ready", "patrol"].includes(
                m.status || "free",
              )
            : !["free", "available", "ready", "patrol"].includes(
                m.status || "free",
              )))
      );
    return (
      (filter.priority === "all" || m.priority === Number(filter.priority)) &&
      (filter.incidents === "all" ||
        (filter.incidents === "active"
          ? !["resolved", "failed", "handover", "closed"].includes(
              m.status || "open",
            )
          : filter.incidents === "open"
            ? m.status === "open"
            : filter.incidents === "working"
              ? ["dispatching", "working", "enroute", "on_scene"].includes(
                  m.status || "",
                )
              : ["resolved", "failed", "handover", "closed"].includes(
                  m.status || "",
                )))
    );
  });
}
/** Spatial-hash proximity clustering; neighboring cells also merge to avoid hidden overlapping pins. */
export function clusterMarkers(
  markers: MapMarker[],
  cellSize = 44,
): MarkerCluster[] {
  const cells = new Map<string, number[]>(),
    parents = markers.map((_, i) => i);
  const find = (i: number): number => {
    let root = i;
    while (parents[root] !== root) root = parents[root];
    while (parents[i] !== i) {
      const next = parents[i];
      parents[i] = root;
      i = next;
    }
    return root;
  };
  for (let i = 0; i < markers.length; i++) {
    const marker = markers[i],
      x = Math.floor(marker.x / cellSize),
      y = Math.floor(marker.y / cellSize);
    for (let dx = -1; dx <= 1; dx++)
      for (let dy = -1; dy <= 1; dy++)
        for (const j of cells.get(`${x + dx}:${y + dy}`) || []) {
          const other = markers[j];
          if (
            (marker.x - other.x) ** 2 + (marker.y - other.y) ** 2 <=
            cellSize ** 2
          ) {
            const a = find(i),
              b = find(j);
            if (a !== b) parents[Math.max(a, b)] = Math.min(a, b);
          }
        }
    const key = `${x}:${y}`,
      cell = cells.get(key) || [];
    cell.push(i);
    cells.set(key, cell);
  }
  const groups = new Map<number, MapMarker[]>();
  for (let i = 0; i < markers.length; i++) {
    const key = find(i),
      group = groups.get(key) || [];
    group.push(markers[i]);
    groups.set(key, group);
  }
  return [...groups.entries()].map(([key, members]) => ({
    key: markers[key].key,
    members,
    x: members.reduce((s, m) => s + m.x, 0) / members.length,
    y: members.reduce((s, m) => s + m.y, 0) / members.length,
    units: members.filter((m) => m.kind === "unit").length,
    incidents: members.filter((m) => m.kind === "incident").length,
    facilities: members.filter((m) => m.kind === "facility").length,
    priority: Math.min(
      3,
      ...members
        .filter((m) => m.kind === "incident")
        .map((m) => m.priority || 3),
    ),
  }));
}
