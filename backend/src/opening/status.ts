/** One day's usual hours. Times are local UK wall-clock "HH:MM". */
export interface DayHours {
  open: string;
  close: string;
  is24Hours: boolean;
}
export type WeekHours = readonly DayHours[]; // Monday first, seven entries.

export interface OpeningStatus {
  /** Based on the station's usual weekly hours; bank holidays are not applied. */
  state: "open" | "closed" | "unknown";
  basis: "usual_hours";
  is24Hours?: boolean;
  /** "HH:MM" local time when it closes (open) or next opens (closed). */
  closesAt?: string;
  opensAt?: string;
  closesInMinutes?: number;
}

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const toMinutes = (t: string) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
const unknown: OpeningStatus = { state: "unknown", basis: "usual_hours" };

/** Local UK weekday (0 = Monday) and minutes since midnight at an instant. */
export function ukClock(at: Date): { day: number; minutes: number } {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London", weekday: "long", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(at);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return { day: DAYS.indexOf(get("weekday")), minutes: Number(get("hour")) * 60 + Number(get("minute")) };
}

export function parseWeek(value: unknown): WeekHours | null {
  if (!Array.isArray(value) || value.length !== 7) return null;
  const week: DayHours[] = [];
  for (const d of value) {
    const o = d as { o?: unknown; c?: unknown; h?: unknown } | null;
    if (!o || typeof o.o !== "string" || typeof o.c !== "string" || !/^\d{2}:\d{2}$/.test(o.o) || !/^\d{2}:\d{2}$/.test(o.c)) return null;
    week.push({ open: o.o, close: o.c, is24Hours: o.h === 1 });
  }
  return week;
}

export const serialiseWeek = (week: WeekHours): string => JSON.stringify(week.map((d) => ({ o: d.open, c: d.close, h: d.is24Hours ? 1 : 0 })));

/**
 * Open, closed or unknown at an instant from usual weekly hours. A day with open == close that is not
 * flagged 24 hours is "unknown": the feed uses the same shape for missing data. Close at or before
 * open (including 00:00) means closing after midnight.
 */
export function openingStatus(week: WeekHours | null, at: Date): OpeningStatus {
  if (!week) return unknown;
  const { day, minutes } = ukClock(at);
  if (day < 0) return unknown;
  const today = week[day]!;
  const yesterday = week[(day + 6) % 7]!;

  // Hours that began yesterday and run past midnight.
  if (!yesterday.is24Hours && yesterday.open !== yesterday.close && toMinutes(yesterday.close) <= toMinutes(yesterday.open)) {
    const close = toMinutes(yesterday.close);
    if (minutes < close) return { state: "open", basis: "usual_hours", closesAt: yesterday.close, closesInMinutes: close - minutes };
  }
  if (today.is24Hours) return { state: "open", basis: "usual_hours", is24Hours: true };
  if (today.open === today.close) return unknown;

  const open = toMinutes(today.open);
  const close = toMinutes(today.close);
  if (close > open) {
    if (minutes >= open && minutes < close) return { state: "open", basis: "usual_hours", closesAt: today.close, closesInMinutes: close - minutes };
    if (minutes < open) return { state: "closed", basis: "usual_hours", opensAt: today.open };
    return { state: "closed", basis: "usual_hours", ...nextOpening(week, day) };
  }
  // Closes after midnight.
  if (minutes >= open) return { state: "open", basis: "usual_hours", closesAt: today.close, closesInMinutes: close + 1440 - minutes };
  return { state: "closed", basis: "usual_hours", opensAt: today.open };
}

function nextOpening(week: WeekHours, day: number): { opensAt?: string } {
  const tomorrow = week[(day + 1) % 7]!;
  return tomorrow.is24Hours || tomorrow.open === tomorrow.close ? {} : { opensAt: tomorrow.open };
}
