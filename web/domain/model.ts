export type Position = [number, number];
export type UnitType =
  | "STREIFE"
  | "SEK"
  | "MEK"
  | "KRIPO"
  | "BEREITSCHAFT"
  | "HUND"
  | "VERKEHR"
  | "HELI";
export type IncidentStatus =
  | "open"
  | "dispatching"
  | "working"
  | "resolved"
  | "failed"
  | "handover";
export interface Officer {
  id: number;
  name: string;
  avatar: number;
  photo: string | null;
  owned: string[];
  equipped: string[];
  skill: number;
  energy: number;
  xp: number;
  qualifications: string[];
  dutyMinutes: number;
  restMinutes: number;
}
export interface Route {
  points: Position[];
  distanceKm: number;
  durationMinutes: number;
  approximate: boolean;
  progress: number;
  elapsedMinutes: number;
  departureAt: number;
}
export interface Vehicle {
  id: number;
  name: string;
  type: UnitType;
  capacity: number;
  speedKmh: number;
  condition: number;
  cost: number;
  image: string;
}
export interface Unit {
  id: number;
  name: string;
  type: UnitType;
  officers: Officer[];
  reserveOfficers: Officer[][];
  rosterIndex: number;
  training: { courseId: string; remainingDays: number } | null;
  incidentId: number | null;
  busyUntil: number | null;
  experience: number;
  regionId: string;
  position: Position;
  basePosition: Position;
  facilityId: string;
  status: string;
  route: Route | null;
  vehicle: Vehicle;
  shiftDuty: boolean;
  restUntil: number;
  readinessAt: number;
}
export interface Decision {
  id: string;
  label: string;
  durationDelta: number;
  riskDelta: number;
  cost: number;
  followUp?: { name: string; priority: number; type: string } | null;
}
export interface IncidentTemplate {
  id: number;
  name: string;
  priority: number;
  needs: string[];
  duration: number;
  description: string;
  deadlineMinutes?: number;
  reward?: number;
  regionId?: string;
  position?: Position;
  followUp?: { name: string; priority: number; type: string } | null;
  decisions?: Decision[];
  chain?: ChainNode[];
  chainStartId?: string;
}
export interface ChainNode {
  id: string;
  name: string;
  needs: string[];
  duration: number;
  priority: number;
  onSuccess?: string;
  onFailure?: string;
}
export interface Incident extends IncidentTemplate {
  status: IncidentStatus;
  created: number;
  createdAbsoluteMinute: number;
  assignedIds: number[];
  completeAt: number | null;
  region: string;
  regionId: string;
  position: Position;
  deadlineAt: number;
  decisionId?: string;
  riskDelta: number;
  history: string[];
  followUpSpawned: boolean;
  chainNode?: string;
  chainSteps: number;
  handover?: {
    fromDay: number;
    status: IncidentStatus;
    remainingDeadline: number;
    remainingWork: number | null;
  };
}
export interface Facility {
  id: string;
  name: string;
  regionId: string;
  position: Position;
  level: number;
}
export interface ShiftStats {
  total: number;
  solved: number;
  failed: number;
  transferred: number;
  income: number;
  expenses: number;
  trainingDone: string[];
  specialDispatches: number;
  rotations: number;
  wages: number;
  maintenance: number;
  trainingProgress: string[];
}
export interface ShiftReport extends ShiftStats {
  day: number;
  region: string;
  regionId: string;
  shift: string;
  durationMinutes: number;
  elapsed: number;
  startAbsoluteMinute: number;
  endAbsoluteMinute: number;
  budget: number;
  reputation: number;
  fatigue: number;
  totalHours: number;
  criticalHandoffs: number;
}
export interface GameState {
  version: 3;
  schemaVersion: 3;
  mode: "career" | "sandbox";
  stage: "planning" | "running" | "debrief";
  region: string;
  regionId: string;
  day: number;
  completedShifts: number;
  shiftType: string;
  elapsed: number;
  shiftStartAbsoluteMinute: number;
  shiftDurationMinutes: number;
  shiftElapsedMinutes: number;
  shiftEndAbsoluteMinute: number;
  absoluteSimMinutes: number;
  startHour: number;
  speed: number;
  paused: boolean;
  seq: number;
  budget: number;
  reputation: number;
  autoCalls: boolean;
  rules: {
    budgetLimited: boolean;
    trainingEnabled: boolean;
    fatigueEnabled: boolean;
    callInterval: number;
    customEnabled: boolean;
  };
  units: Unit[];
  templates: IncidentTemplate[];
  incidents: Incident[];
  facilities: Facility[];
  logs: string[];
  history: ShiftReport[];
  shiftStats: ShiftStats;
  nextCallAt: number;
  rngSeed: number;
  totalHours: number;
  migrations: string[];
  audio: {
    master: number;
    radio: number;
    sfx: number;
    ambient: number;
    mute: boolean;
  };
  slotsLabel?: string;
}
