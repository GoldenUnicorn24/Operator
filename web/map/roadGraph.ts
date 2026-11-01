import source from "../data/geo/hamburg-roads.json";
import { haversineKm } from "./geoMath";
import type { Position } from "./regions";
export const hamburgRoads = source as {
  name: string;
  class: string;
  points: Position[];
}[];
interface Node {
  position: Position;
  edges: Map<number, number>;
}
let graph: Node[] | null = null;
function network(): Node[] {
  if (graph) return graph;
  graph = [];
  const keys = new Map<string, number>();
  const node = (p: Position) => {
    const key = p[0].toFixed(4) + "," + p[1].toFixed(4);
    let index = keys.get(key);
    if (index === undefined) {
      index = graph!.length;
      keys.set(key, index);
      graph!.push({ position: p, edges: new Map() });
    }
    return index;
  };
  for (const road of hamburgRoads)
    for (let i = 1; i < road.points.length; i++) {
      const a = node(road.points[i - 1]),
        b = node(road.points[i]);
      if (a === b) continue;
      const d = haversineKm(graph[a].position, graph[b].position);
      if (d < 0.00001) continue;
      const old = graph[a].edges.get(b);
      if (old === undefined || d < old) {
        graph[a].edges.set(b, d);
        graph[b].edges.set(a, d);
      }
    }
  return graph;
}
class Heap {
  private data: { id: number; score: number }[] = [];
  get length() {
    return this.data.length;
  }
  push(value: { id: number; score: number }) {
    this.data.push(value);
    let i = this.data.length - 1;
    while (i > 0) {
      const parent = (i - 1) >> 1;
      if (this.data[parent].score <= value.score) break;
      this.data[i] = this.data[parent];
      i = parent;
    }
    this.data[i] = value;
  }
  pop() {
    const first = this.data[0],
      last = this.data.pop()!;
    if (this.data.length) {
      let i = 0;
      while (i * 2 + 1 < this.data.length) {
        let child = i * 2 + 1;
        if (
          child + 1 < this.data.length &&
          this.data[child + 1].score < this.data[child].score
        )
          child++;
        if (this.data[child].score >= last.score) break;
        this.data[i] = this.data[child];
        i = child;
      }
      this.data[i] = last;
    }
    return first;
  }
}
/** Main-road A*, bidirectional approximate game graph; not a turn-restriction navigation engine. */
export function hamburgRoadRoute(
  from: Position,
  to: Position,
): Position[] | null {
  const nodes = network();
  let start = -1,
    end = -1,
    startDistance = Infinity,
    endDistance = Infinity;
  for (let i = 0; i < nodes.length; i++) {
    const a = haversineKm(from, nodes[i].position),
      b = haversineKm(to, nodes[i].position);
    if (a < startDistance) {
      startDistance = a;
      start = i;
    }
    if (b < endDistance) {
      endDistance = b;
      end = i;
    }
  }
  // Islands and peripheral roads absent from the extract use the declared distance approximation.
  if (start < 0 || end < 0 || startDistance > 4 || endDistance > 4) return null;
  const heap = new Heap(),
    cost = new Float64Array(nodes.length).fill(Infinity),
    previous = new Int32Array(nodes.length).fill(-1),
    closed = new Uint8Array(nodes.length);
  cost[start] = 0;
  heap.push({
    id: start,
    score: haversineKm(nodes[start].position, nodes[end].position),
  });
  while (heap.length) {
    const current = heap.pop().id;
    if (closed[current]) continue;
    if (current === end) {
      const result: Position[] = [];
      let index = end;
      while (index !== -1) {
        result.push(nodes[index].position);
        index = previous[index];
      }
      result.reverse();
      return [[...from], ...result, [...to]];
    }
    closed[current] = 1;
    for (const [next, distance] of nodes[current].edges) {
      if (closed[next]) continue;
      const candidate = cost[current] + distance;
      if (candidate < cost[next]) {
        cost[next] = candidate;
        previous[next] = current;
        heap.push({
          id: next,
          score:
            candidate + haversineKm(nodes[next].position, nodes[end].position),
        });
      }
    }
  }
  return null;
}
