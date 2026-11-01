import type { GameState } from "./model";
export const SHIFT_OPTIONS: Record<
  string,
  { name: string; start: number; duration: number; label: string }
> = {
  early: {
    name: "Frühschicht",
    start: 360,
    duration: 480,
    label: "06–14 Uhr · 8h",
  },
  late: {
    name: "Spätschicht",
    start: 840,
    duration: 480,
    label: "14–22 Uhr · 8h",
  },
  night: {
    name: "Nachtschicht",
    start: 1320,
    duration: 480,
    label: "22–06 Uhr · 8h",
  },
  day12: {
    name: "Tagschicht",
    start: 360,
    duration: 720,
    label: "06–18 Uhr · 12h",
  },
  night12: {
    name: "Nachtdienst",
    start: 1080,
    duration: 720,
    label: "18–06 Uhr · 12h",
  },
  full24: {
    name: "Ganztag",
    start: 360,
    duration: 1440,
    label: "06–06 Uhr · 24h",
  },
};
export function timeLabel(minute: number) {
  const t = ((Math.floor(minute) % 1440) + 1440) % 1440;
  return `${String(Math.floor(t / 60)).padStart(2, "0")}:${String(t % 60).padStart(2, "0")}`;
}
export function planShift(s: GameState, preset: string, startHour?: number) {
  if (s.stage !== "planning") throw Error("Schicht läuft bereits.");
  const p = SHIFT_OPTIONS[preset];
  if (!p) throw Error("Unbekannte Schicht.");
  if (
    startHour !== undefined &&
    (!Number.isInteger(startHour) || startHour < 0 || startHour > 23)
  )
    throw Error("Startstunde: 0–23.");
  s.shiftType = preset;
  s.startHour = startHour ?? p.start / 60;
  s.shiftDurationMinutes = p.duration;
  s.shiftStartAbsoluteMinute = nextStart(s);
  s.shiftEndAbsoluteMinute =
    s.shiftStartAbsoluteMinute + s.shiftDurationMinutes;
}
export function nextStart(s: GameState) {
  let start = Math.floor(s.absoluteSimMinutes / 1440) * 1440 + s.startHour * 60;
  if (start < s.absoluteSimMinutes) start += 1440;
  return start;
}
