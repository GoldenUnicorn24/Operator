import {
  contains,
  distanceKm,
  getRegion,
  polygons,
  regions,
  type Position,
  type Region,
} from "./regions";
import {
  clusterMarkers,
  defaultFilter,
  filterMarkers,
  type MapMarker,
  type MarkerFilter,
} from "./clusters";
import { hamburgRoads } from "./roadGraph";
import "./view.css";

type Kind = "unit" | "incident" | "facility";
type MapState = {
  regionId: string;
  units: any[];
  incidents: any[];
  facilities: any[];
  absoluteSimMinutes: number;
  paused?: boolean;
};
const NS = "http://www.w3.org/2000/svg";
const escape = (s: unknown) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ]!,
  );
const project = (p: Position): Position => [
  p[0],
  (-Math.log(Math.tan(Math.PI / 4 + (p[1] * Math.PI) / 360)) * 180) / Math.PI,
];
const inverse = (p: Position): Position => [
  p[0],
  ((2 * Math.atan(Math.exp((-p[1] * Math.PI) / 180)) - Math.PI / 2) * 180) /
    Math.PI,
];

/** Offline vector adapter. It renders real bundled geography without a network/GPU dependency. */
export class LiveMap {
  private state: MapState | null = null;
  private root: HTMLDivElement;
  private svg: SVGSVGElement;
  private base: SVGGElement;
  private routes: SVGGElement;
  private markers: SVGGElement;
  private legend: HTMLDivElement;
  private members: HTMLDivElement;
  private mini: SVGSVGElement;
  private filter: MarkerFilter = { ...defaultFilter };
  private region: Region | null = null;
  private width = 800;
  private height = 500;
  private measuredWidth = 0;
  private centre: Position = [10, -62];
  private scale = 100;
  private fitScale = 100;
  private zoomLevel = 1;
  private heat = false;
  private coverage = false;
  private day: "auto" | "day" | "night" = "auto";
  private placing = false;
  private pointers = new Map<number, { x: number; y: number }>();
  private pointerStart: {
    x: number;
    y: number;
    centre: Position;
    distance: number;
    moved: boolean;
  } | null = null;
  private observer: ResizeObserver | null = null;
  private readonly onResize = () => this.resize();
  private pathCache = new Map<string, string>();
  private markerNodes = new Map<string, SVGGElement>();
  private allVisible: MapMarker[] = [];
  private clusterByKey = new Map<string, MapMarker[]>();
  private showAll = false;
  private selectedCluster: string | null = null;

  constructor(
    private container: HTMLElement,
    private onSelect: (kind: Kind, id: number | string) => void,
    private onPlace?: (p: Position) => void,
  ) {
    this.root = document.createElement("div");
    this.root.className = "live-map";
    this.root.innerHTML = `<div class="map-toolbar"><button class="map-fit" type="button">Ganzes Bundesland</button><button class="map-in" aria-label="Karte vergrößern">+</button><button class="map-out" aria-label="Karte verkleinern">−</button><button class="map-day" aria-label="Tag-/Nachtansicht wechseln">Tag/Nacht</button>${onPlace ? '<button class="map-place" aria-pressed="false">Standort setzen</button>' : ""}</div>
      <div class="map-filters"><label>Organisation<select data-filter="type"><option value="all">Alle Einheiten</option><option>STREIFE</option><option>SEK</option><option>MEK</option><option>KRIPO</option><option>BEREITSCHAFT</option><option>HUND</option><option>VERKEHR</option><option>HELI</option></select></label><label>Verfügbarkeit<select data-filter="availability"><option value="all">Alle Fahrzeuge</option><option value="free">Freie Fahrzeuge</option><option value="busy">Gebundene Fahrzeuge</option></select></label><label>Einsätze<select data-filter="incidents"><option value="active">Offen + laufend</option><option value="open">Offene Notrufe</option><option value="working">Auf Anfahrt / vor Ort</option><option value="closed">Abgeschlossen / Übergabe</option><option value="all">Alle Einsätze</option></select></label><label>Priorität<select data-filter="priority"><option value="all">P1 / P2 / P3</option><option value="1">P1 – Kritisch</option><option value="2">P2 – Hoch</option><option value="3">P3 – Normal</option></select></label><label class="map-toggle"><input type="checkbox" data-facilities checked>Wachen</label><label class="map-toggle"><input type="checkbox" data-heat>Heatmap</label><label class="map-toggle"><input type="checkbox" data-coverage>Abdeckung</label></div>
      <div class="map-placement" hidden>Auf die Karte tippen: Standort für den Editor wählen. Nur Punkte innerhalb der Landesgrenze werden übernommen.</div>
      <div class="map-stage"><svg class="map-canvas" role="img" aria-label="Geografische Landeskarte mit Live-Fahrzeugen und Einsätzen" tabindex="0"><g data-base></g><g data-routes></g><g data-markers></g></svg><svg class="map-mini" viewBox="0 0 108 82" aria-label="Landesübersicht"></svg><span class="map-scale"></span></div>
      <div class="map-legend" aria-live="polite"></div><div class="map-members"></div><div class="map-attribution">Grenzen: © GeoBasis-DE / BKG 2021 · geoBoundaries · <a href="https://www.govdata.de/dl-de/by-2-0" target="_blank" rel="noopener">dl-de/by-2-0</a><br>Hauptstraßen Hamburg: Freie und Hansestadt Hamburg, BWVI · dl-de/by-2-0.<br>Gestrichelte Fahrkorridore / ETA: Simulationsnäherung. Hamburg: Hauptstraßengraph ohne Abbiegebeschränkungen; andere Länder: Distanzmodell. Fiktive Wachen.</div>`;
    container.append(this.root);
    this.svg = this.root.querySelector(".map-canvas")!;
    this.base = this.svg.querySelector("[data-base]")!;
    this.routes = this.svg.querySelector("[data-routes]")!;
    this.markers = this.svg.querySelector("[data-markers]")!;
    this.legend = this.root.querySelector(".map-legend")!;
    this.members = this.root.querySelector(".map-members")!;
    this.mini = this.root.querySelector(".map-mini")!;
    this.root
      .querySelector(".map-fit")!
      .addEventListener("click", () => this.fit());
    this.root
      .querySelector(".map-in")!
      .addEventListener("click", () => this.zoom(1.6));
    this.root
      .querySelector(".map-out")!
      .addEventListener("click", () => this.zoom(1 / 1.6));
    this.root.querySelector(".map-day")!.addEventListener("click", () => {
      this.day =
        this.day === "auto" ? "day" : this.day === "day" ? "night" : "auto";
      this.paint();
    });
    this.root
      .querySelector(".map-place")
      ?.addEventListener("click", () => this.setPlacement(!this.placing));
    this.root
      .querySelectorAll<HTMLSelectElement>("[data-filter]")
      .forEach((input) =>
        input.addEventListener("change", () => {
          (this.filter as any)[input.dataset.filter!] = input.value;
          this.paintMarkers();
        }),
      );
    this.root
      .querySelector("[data-facilities]")!
      .addEventListener("change", (e) => {
        this.filter.facilities = (e.target as HTMLInputElement).checked;
        this.paintMarkers();
      });
    this.root.querySelector("[data-heat]")!.addEventListener("change", (e) => {
      this.heat = (e.target as HTMLInputElement).checked;
      this.paintMarkers();
    });
    this.root
      .querySelector("[data-coverage]")!
      .addEventListener("change", (e) => {
        this.coverage = (e.target as HTMLInputElement).checked;
        this.paintMarkers();
      });
    this.svg.addEventListener(
      "wheel",
      (e) => {
        e.preventDefault();
        this.zoom(e.deltaY < 0 ? 1.2 : 1 / 1.2, this.localPoint(e));
      },
      { passive: false },
    );
    this.svg.addEventListener("pointerdown", (e) => this.pointerDown(e));
    this.svg.addEventListener("pointermove", (e) => this.pointerMove(e));
    this.svg.addEventListener("pointerup", (e) => this.pointerUp(e));
    this.svg.addEventListener("pointercancel", (e) => {
      this.pointers.delete(e.pointerId);
      this.pointerStart = null;
    });
    this.svg.addEventListener("keydown", (e) => {
      if (e.key === "+" || e.key === "=") this.zoom(1.5);
      else if (e.key === "-") this.zoom(1 / 1.5);
      else if (e.key === "Home") this.fit();
      else if (
        ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(e.key)
      ) {
        e.preventDefault();
        this.centre[0] +=
          (e.key === "ArrowLeft" ? -40 : e.key === "ArrowRight" ? 40 : 0) /
          this.scale;
        this.centre[1] +=
          (e.key === "ArrowUp" ? -40 : e.key === "ArrowDown" ? 40 : 0) /
          this.scale;
        this.paint();
      }
    });
    this.svg.addEventListener("click", (e) => this.selectTarget(e));
    this.svg.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        this.selectTarget(e);
      }
    });
    if (typeof ResizeObserver !== "undefined") {
      this.observer = new ResizeObserver(this.onResize);
      this.observer.observe(container);
    } else window.addEventListener("resize", this.onResize);
    this.resize();
  }

  update(state: MapState): void {
    this.state = state;
    const changed = this.region?.id !== state.regionId;
    this.region = getRegion(state.regionId);
    this.updateTheme();
    if (changed) this.fit();
    else this.paintMarkers();
  }
  setPlacement(active: boolean): void {
    this.placing = Boolean(active && this.onPlace);
    this.root
      .querySelector(".map-place")
      ?.setAttribute("aria-pressed", String(this.placing));
    const hint = this.root.querySelector(".map-placement") as HTMLElement;
    hint.hidden = !this.placing;
    hint.textContent =
      "Auf die Karte tippen: Standort für den Editor wählen. Nur Punkte innerhalb der Landesgrenze werden übernommen.";
  }
  fit(): void {
    if (!this.region) return;
    const b = this.region.bounds,
      a = project([b[0], b[1]]),
      c = project([b[2], b[3]]);
    this.centre = [(a[0] + c[0]) / 2, (a[1] + c[1]) / 2];
    this.fitScale = Math.min(
      (this.width - 64) / Math.abs(c[0] - a[0]),
      (this.height - 60) / Math.abs(c[1] - a[1]),
    );
    this.scale = this.fitScale;
    this.zoomLevel = 1;
    this.paint();
  }
  focus(position: Position): void {
    if (!this.region) return;
    this.centre = project(position);
    this.zoomLevel = Math.max(this.zoomLevel, 4);
    this.scale = this.fitScale * this.zoomLevel;
    this.paint();
  }
  destroy(): void {
    this.observer?.disconnect();
    window.removeEventListener("resize", this.onResize);
    this.root.remove();
    this.markerNodes.clear();
  }
  private resize(): void {
    const w = this.container.clientWidth;
    if (w < 1 || Math.abs(w - this.measuredWidth) < 0.5) return;
    this.measuredWidth = w;
    this.width = Math.max(280, w);
    this.height = w < 600 ? 420 : 510;
    this.svg.setAttribute("viewBox", `0 0 ${this.width} ${this.height}`);
    this.svg.style.height = this.height + "px";
    if (this.region) this.fit();
  }
  private updateTheme(): void {
    const hour = ((this.state?.absoluteSimMinutes || 0) % 1440) / 60;
    this.root.classList.toggle(
      "day",
      this.day === "day" || (this.day === "auto" && hour >= 7 && hour < 19),
    );
    this.root.classList.toggle("paused", !!this.state?.paused);
    const button = this.root.querySelector(".map-day")!;
    button.textContent =
      this.day === "auto" ? "Auto" : this.day === "day" ? "Tag" : "Nacht";
  }
  private screen(p: Position): Position {
    const x = project(p);
    return [
      (x[0] - this.centre[0]) * this.scale + this.width / 2,
      (x[1] - this.centre[1]) * this.scale + this.height / 2,
    ];
  }
  private geo(p: Position): Position {
    return inverse([
      (p[0] - this.width / 2) / this.scale + this.centre[0],
      (p[1] - this.height / 2) / this.scale + this.centre[1],
    ]);
  }
  private localPoint(e: { clientX: number; clientY: number }): Position {
    const rect = this.svg.getBoundingClientRect();
    return [
      ((e.clientX - rect.left) * this.width) / rect.width,
      ((e.clientY - rect.top) * this.height) / rect.height,
    ];
  }
  private zoom(
    factor: number,
    anchor: Position = [this.width / 2, this.height / 2],
  ): void {
    const before = this.geo(anchor);
    this.zoomLevel = Math.max(0.65, Math.min(80, this.zoomLevel * factor));
    this.scale = this.fitScale * this.zoomLevel;
    const after = this.geo(anchor),
      pa = project(before),
      pb = project(after);
    this.centre = [
      this.centre[0] + pa[0] - pb[0],
      this.centre[1] + pa[1] - pb[1],
    ];
    this.paint();
  }
  private pointerDown(e: PointerEvent): void {
    if ((e.target as Element).closest("[data-marker],[data-cluster]")) return;
    const p = this.localPoint(e);
    this.pointers.set(e.pointerId, { x: p[0], y: p[1] });
    this.svg.setPointerCapture(e.pointerId);
    this.pointerStart = {
      x: p[0],
      y: p[1],
      centre: [...this.centre],
      distance: 0,
      moved: false,
    };
    if (this.pointers.size === 2) {
      const a = [...this.pointers.values()];
      this.pointerStart.distance = Math.hypot(a[0].x - a[1].x, a[0].y - a[1].y);
    }
  }
  private pointerMove(e: PointerEvent): void {
    if (!this.pointers.has(e.pointerId) || !this.pointerStart) return;
    const p = this.localPoint(e);
    this.pointers.set(e.pointerId, { x: p[0], y: p[1] });
    const a = [...this.pointers.values()];
    if (a.length === 2) {
      const distance = Math.hypot(a[0].x - a[1].x, a[0].y - a[1].y);
      if (this.pointerStart.distance > 0)
        this.zoom(distance / this.pointerStart.distance, [
          (a[0].x + a[1].x) / 2,
          (a[0].y + a[1].y) / 2,
        ]);
      this.pointerStart.distance = distance;
      this.pointerStart.moved = true;
    } else {
      const dx = p[0] - this.pointerStart.x,
        dy = p[1] - this.pointerStart.y;
      if (Math.abs(dx) + Math.abs(dy) > 5) this.pointerStart.moved = true;
      if (this.pointerStart.moved) {
        this.centre = [
          this.pointerStart.centre[0] - dx / this.scale,
          this.pointerStart.centre[1] - dy / this.scale,
        ];
        this.paint();
      }
    }
  }
  private pointerUp(e: PointerEvent): void {
    if (
      this.placing &&
      this.pointerStart &&
      !this.pointerStart.moved &&
      this.region
    ) {
      const p = this.geo(this.localPoint(e));
      if (contains(this.region.id, p)) {
        this.setPlacement(false);
        this.onPlace?.(p);
      } else {
        (this.root.querySelector(".map-placement") as HTMLElement).textContent =
          "Dieser Punkt liegt außerhalb der Landesgrenze. Bitte einen Standort innerhalb des Bundeslandes wählen.";
      }
    }
    this.pointers.delete(e.pointerId);
    this.pointerStart = null;
  }
  private selectTarget(e: Event): void {
    const target = (e.target as Element).closest(
      "[data-marker],[data-cluster]",
    ) as SVGElement | null;
    if (!target) return;
    if (target.dataset.cluster) {
      this.selectedCluster = target.dataset.cluster;
      this.showAll = false;
      this.showMembers(
        this.clusterByKey.get(this.selectedCluster) || [],
        "Cluster öffnen",
      );
    } else if (target.dataset.marker) {
      const m = this.allVisible.find((m) => m.key === target.dataset.marker);
      if (m) this.onSelect(m.kind, m.id);
    }
  }
  private path(region: Region): string {
    const lod = this.zoomLevel < 3 ? 4 : 1,
      key = `${region.id}:${lod}`;
    let path = this.pathCache.get(key);
    if (!path) {
      path = polygons(region.boundary)
        .map((p) =>
          p
            .map((r) => {
              const pts = (
                r.length <= lod * 3 + 1
                  ? r
                  : r.filter((_, i) => i % lod === 0 || i === r.length - 1)
              ).map(project);
              return "M" + pts.map((p) => p.join(",")).join("L") + "Z";
            })
            .join(""),
        )
        .join("");
      this.pathCache.set(key, path);
    }
    return path;
  }
  private paint(): void {
    if (!this.region) return;
    this.updateTheme();
    const tx = this.width / 2 - this.centre[0] * this.scale,
      ty = this.height / 2 - this.centre[1] * this.scale;
    const transform = `translate(${tx},${ty}) scale(${this.scale})`;
    let roadPaths = "";
    if (this.region.id === "HH") {
      let roads = this.pathCache.get("HH:roads");
      if (!roads) {
        roads = hamburgRoads
          .map(
            (r) =>
              "M" +
              r.points
                .map(project)
                .map((p) => p.join(","))
                .join("L"),
          )
          .join("");
        this.pathCache.set("HH:roads", roads);
      }
      roadPaths = `<path class="map-road" vector-effect="non-scaling-stroke" d="${roads}"/>`;
    }
    this.base.innerHTML = `<g transform="${transform}">${regions
      .filter((r) => r.id !== this.region!.id)
      .map((r) => `<path class="map-neighbor" d="${this.path(r)}"/>`)
      .join(
        "",
      )}<path data-boundary="${this.region.id}" class="map-land" d="${this.path(this.region)}"/>${roadPaths}</g>`;
    const cities = this.region.cities;
    const cityGroup = document.createElementNS(NS, "g");
    const links = new Set<string>();
    // Display only a sparse, explicitly simulated intercity travel layer, never pretend these are roads.
    for (let i = 0; i < cities.length; i++) {
      const nearest = cities
        .map((c, j) => ({ j, d: distanceKm(cities[i].position, c.position) }))
        .filter((x) => x.j !== i)
        .sort((a, b) => a.d - b.d)
        .slice(0, 2);
      for (const n of nearest) {
        const k = [i, n.j].sort((a, b) => a - b).join(":");
        if (links.has(k)) continue;
        links.add(k);
        const a = this.screen(cities[i].position),
          b = this.screen(cities[n.j].position);
        cityGroup.innerHTML += `<path class="map-corridor" d="M${a}L${b}"/>`;
      }
    }
    const occupied: { x: number; y: number }[] = [];
    for (const city of cities) {
      const p = this.screen(city.position);
      if (
        p[0] < -20 ||
        p[1] < -20 ||
        p[0] > this.width + 20 ||
        p[1] > this.height + 20
      )
        continue;
      cityGroup.innerHTML += `<circle class="map-city-dot" cx="${p[0]}" cy="${p[1]}" r="2.4"/>`;
      if (
        occupied.some(
          (c) => Math.abs(c.x - p[0]) < 74 && Math.abs(c.y - p[1]) < 18,
        )
      )
        continue;
      occupied.push({ x: p[0], y: p[1] });
      cityGroup.innerHTML += `<text class="map-city" x="${p[0] + 5}" y="${p[1] - 5}">${escape(city.name)}</text>`;
    }
    if (this.region.id === "HH" && this.zoomLevel >= 3) {
      const labels = new Set<string>();
      for (const road of hamburgRoads) {
        if (labels.has(road.name) || road.name.length < 4) continue;
        const p = this.screen(road.points[Math.floor(road.points.length / 2)]);
        if (
          p[0] < 20 ||
          p[1] < 20 ||
          p[0] > this.width - 80 ||
          p[1] > this.height - 20 ||
          occupied.some(
            (c) => Math.abs(c.x - p[0]) < 100 && Math.abs(c.y - p[1]) < 30,
          )
        )
          continue;
        labels.add(road.name);
        occupied.push({ x: p[0], y: p[1] });
        cityGroup.innerHTML += `<text class="map-road-label" x="${p[0]}" y="${p[1]}">${escape(road.name)}</text>`;
        if (labels.size >= 16) break;
      }
    }
    this.base.append(cityGroup);
    this.paintMini();
    this.paintMarkers(false);
    const km = distanceKm(
      this.geo([0, this.height / 2]),
      this.geo([100, this.height / 2]),
    );
    this.root.querySelector(".map-scale")!.textContent =
      `100 px ≈ ${km < 1 ? Math.round(km * 1000) + " m" : km.toFixed(km < 10 ? 1 : 0) + " km"} · ${this.zoomLevel.toFixed(1)}×`;
  }
  private paintMini(): void {
    if (!this.region) return;
    const b = this.region.bounds,
      a = project([b[0], b[1]]),
      c = project([b[2], b[3]]),
      scale = Math.min(92 / Math.abs(c[0] - a[0]), 66 / Math.abs(c[1] - a[1])),
      cx = (a[0] + c[0]) / 2,
      cy = (a[1] + c[1]) / 2;
    const x = 54 - cx * scale,
      y = 41 - cy * scale;
    const left = (this.centre[0] - this.width / 2 / this.scale) * scale + x,
      top = (this.centre[1] - this.height / 2 / this.scale) * scale + y;
    this.mini.innerHTML = `<path transform="translate(${x},${y}) scale(${scale})" vector-effect="non-scaling-stroke" d="${this.path(this.region)}"/><rect x="${left}" y="${top}" width="${(this.width / this.scale) * scale}" height="${(this.height / this.scale) * scale}"/>`;
  }
  private collect(): MapMarker[] {
    if (!this.state) return [];
    const result: MapMarker[] = [];
    const add = (kind: Kind, a: any) => {
      if (a.regionId !== this.state!.regionId || !Array.isArray(a.position))
        return;
      const p = this.screen(a.position);
      result.push({
        id: a.id,
        key: kind + ":" + a.id,
        kind,
        name: a.name,
        position: a.position,
        type: a.type,
        status: a.training ? "training" : a.status || "free",
        priority: a.priority,
        x: p[0],
        y: p[1],
      });
    };
    this.state.units.forEach((u) => add("unit", u));
    this.state.incidents.forEach((i) => add("incident", i));
    this.state.facilities.forEach((f) => add("facility", f));
    return result;
  }
  private paintMarkers(animate = true): void {
    if (!this.state || !this.region) return;
    const all = this.collect(),
      filtered = filterMarkers(all, this.filter);
    this.allVisible = filtered;
    const within = filtered.filter(
      (m) =>
        m.x >= -24 &&
        m.x <= this.width + 24 &&
        m.y >= -24 &&
        m.y <= this.height + 24,
    );
    const clusters = clusterMarkers(within, this.width < 500 ? 42 : 38),
      desired = new Set<string>();
    this.clusterByKey.clear();
    const color = (m: MapMarker) =>
      m.kind === "incident"
        ? m.priority === 1
          ? "#ff7183"
          : m.priority === 2
            ? "#ffc66d"
            : "#9bd7ff"
        : m.kind === "facility"
          ? "#92a6be"
          : m.type === "SEK"
            ? "#bfa1ff"
            : m.type === "MEK"
              ? "#e7a8dc"
              : ["free", "ready", "available", "patrol"].includes(
                    m.status || "",
                  )
                ? "#5be1cc"
                : ["resting", "offDuty", "training"].includes(m.status || "")
                  ? "#8e9bab"
                  : "#60bce8";
    for (const cluster of clusters) {
      const single = cluster.members.length === 1,
        m = cluster.members[0],
        key = single ? m.key : "cluster:" + cluster.key;
      desired.add(key);
      let node = this.markerNodes.get(key);
      const existed = Boolean(node);
      if (!node) {
        node = document.createElementNS(NS, "g");
        node.setAttribute("role", "button");
        node.setAttribute("tabindex", "0");
        this.markers.append(node);
        this.markerNodes.set(key, node);
      }
      const label = single
        ? `${m.kind === "unit" ? m.type : m.kind === "incident" ? "P" + m.priority : "Wache"} · ${m.name} · ${m.status || ""}`
        : `${cluster.members.length} Marker: ${cluster.units} Fahrzeuge, ${cluster.incidents} Einsätze, ${cluster.facilities} Wachen`;
      node.setAttribute("aria-label", label);
      node.setAttribute(
        "class",
        `map-marker ${single ? "" : "cluster"} ${cluster.priority === 1 && cluster.incidents ? "p1" : ""}`,
      );
      node.style.transition = animate && existed ? "" : "none";
      node.setAttribute(
        "transform",
        `translate(${cluster.x.toFixed(2)},${cluster.y.toFixed(2)})`,
      );
      node.style.transform = `translate(${cluster.x.toFixed(2)}px,${cluster.y.toFixed(2)}px)`;
      if (single) {
        delete node.dataset.cluster;
        node.dataset.marker = m.key;
        const symbol =
          m.kind === "incident"
            ? "P" + m.priority
            : m.kind === "facility"
              ? "W"
              : m.type === "STREIFE"
                ? "S"
                : m.type === "SEK"
                  ? "SEK"
                  : m.type === "MEK"
                    ? "MEK"
                    : m.type === "KRIPO"
                      ? "K"
                      : m.type === "HELI"
                        ? "H"
                        : m.type?.slice(0, 1) || "F";
        node.innerHTML = `<title>${escape(label)}</title><circle r="22" fill="transparent"/>${m.kind === "incident" && m.priority === 1 ? '<circle class="map-pulse" r="18"/>' : ""}<circle class="map-marker-disc" r="${m.kind === "facility" ? 12 : 16}" fill="${color(m)}"/><text>${escape(symbol)}</text>`;
      } else {
        delete node.dataset.marker;
        node.dataset.cluster = cluster.key;
        this.clusterByKey.set(cluster.key, cluster.members);
        node.innerHTML = `<title>${escape(label)}</title><circle r="22" fill="transparent"/><circle class="map-marker-disc" r="20" fill="${cluster.incidents ? (cluster.priority === 1 ? "#963e54" : "#775a30") : "#18536d"}"/><text y="-2">${cluster.members.length}</text><text class="map-marker-category" y="11">${cluster.incidents ? "P" + cluster.priority + " · " : ""}${cluster.units ? "F" : ""}${cluster.facilities ? " W" : ""}</text>`;
      }
    }
    for (const [key, node] of this.markerNodes) {
      if (!desired.has(key)) {
        node.remove();
        this.markerNodes.delete(key);
      }
    }
    const visibleIds = new Set(
      filtered.filter((m) => m.kind === "unit").map((m) => m.id),
    );
    let routePaths = "";
    for (const unit of this.state.units) {
      if (
        unit.regionId !== this.region.id ||
        !unit.route?.points ||
        !visibleIds.has(unit.id)
      )
        continue;
      const points = (unit.route.points as Position[]).map((p) =>
        this.screen(p),
      );
      routePaths += `<path class="map-route ${unit.status === "returning" ? "returning" : ""}" d="M${points.map((p) => p.join(",")).join("L")}"/>`;
    }
    if (this.coverage) {
      for (const f of this.state.facilities.filter(
        (f) => f.regionId === this.region!.id,
      )) {
        const p = this.screen(f.position),
          r = Math.max(20, this.scale * 0.12);
        routePaths += `<circle cx="${p[0]}" cy="${p[1]}" r="${r}" fill="#4bd9d4" opacity=".06" stroke="#73d3d0" stroke-dasharray="3 6"/>`;
      }
    }
    if (this.heat) {
      for (const m of filtered.filter((m) => m.kind === "incident"))
        routePaths += `<circle class="map-heat" cx="${m.x}" cy="${m.y}" r="${m.priority === 1 ? 65 : 42}"/>`;
    }
    this.routes.innerHTML = routePaths;
    const count = (arr: MapMarker[], kind: Kind) =>
      arr.filter((m) => m.kind === kind).length;
    const moving = this.state.units.filter(
      (u) => u.regionId === this.region!.id && u.route,
    ).length;
    this.legend.innerHTML = `<span><strong>${count(filtered, "unit")} / ${count(all, "unit")}</strong> Fahrzeuge</span><span><strong>${count(filtered, "incident")} / ${count(all, "incident")}</strong> Einsätze</span><span><strong>${count(filtered, "facility")}</strong> Wachen</span><span>${moving} Anfahrten / Rückfahrten · ${clusters.length} Pins/Cluster</span><button class="map-list-all">Alle anzeigen</button>${this.region.id === "HH" ? '<button class="map-hamburg-detail">Hamburg Stadtgebiet</button>' : ""}`;
    this.legend
      .querySelector(".map-list-all")!
      .addEventListener("click", () => {
        this.showAll = !this.showAll;
        this.selectedCluster = null;
        this.showMembers(
          this.showAll ? this.allVisible : [],
          "Alle gefilterten Marker",
        );
      });
    this.legend
      .querySelector(".map-hamburg-detail")
      ?.addEventListener("click", () => this.focus([10.01, 53.56]));
    if (this.showAll) this.showMembers(filtered, "Alle gefilterten Marker");
    else if (this.selectedCluster)
      this.showMembers(
        this.clusterByKey.get(this.selectedCluster) || [],
        "Cluster öffnen",
      );
  }
  private showMembers(markers: MapMarker[], title: string): void {
    this.members.replaceChildren();
    if (!markers.length) return;
    const header = document.createElement("div");
    header.className = "map-members-head";
    const label = document.createElement("b");
    label.textContent = `${title} · ${markers.length}`;
    header.append(label);
    if (!this.showAll) {
      const zoom = document.createElement("button");
      zoom.textContent = "Zum Cluster zoomen";
      zoom.addEventListener("click", () => {
        const p: Position = [
          markers.reduce((s, m) => s + m.position[0], 0) / markers.length,
          markers.reduce((s, m) => s + m.position[1], 0) / markers.length,
        ];
        this.centre = project(p);
        this.zoomLevel = Math.min(80, this.zoomLevel * 2);
        this.scale = this.fitScale * this.zoomLevel;
        this.selectedCluster = null;
        this.members.replaceChildren();
        this.paint();
      });
      header.append(zoom);
    }
    const close = document.createElement("button");
    close.textContent = "✕";
    close.setAttribute("aria-label", "Markerliste schließen");
    close.addEventListener("click", () => {
      this.showAll = false;
      this.selectedCluster = null;
      this.members.replaceChildren();
    });
    header.append(close);
    this.members.append(header);
    for (const m of markers) {
      const button = document.createElement("button");
      button.className = "map-member";
      const name = document.createElement("span");
      name.textContent = m.name;
      const status = document.createElement("small");
      status.textContent =
        (m.kind === "incident"
          ? "P" + m.priority
          : m.kind === "facility"
            ? "Fiktive Wache"
            : m.type) +
        " · " +
        (m.status || "");
      button.append(name, status);
      button.addEventListener("click", () => this.onSelect(m.kind, m.id));
      this.members.append(button);
    }
  }
}
