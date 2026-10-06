import { BANK_HOLIDAYS } from "./bank-holidays.ts";

/** One day's hours. Times are local UK wall-clock "HH:MM" (confirmed by Fuel Finder, 6 October 2026). */
export interface DayHours {
  open: string;
  close: string;
  is24Hours: boolean;
}
export type WeekHours = readonly DayHours[]; // Monday first, seven entries.

/** Bank-holiday calendars published by GOV.UK. */
export type Division = "england-and-wales" | "scotland" | "northern-ireland";

export interface StationHours {
  week: WeekHours;
  /** Present only when the feed gives distinct bank-holiday hours (type "bank holiday"); they replace usual hours. */
  bankHoliday: DayHours | null;
  /** Which bank-holiday calendar applies; null when the station's nation could not be determined. */
  division: Division | null;
}

export interface OpeningStatus {
  state: "open" | "closed" | "unknown";
  /** Which hours were used: usual weekly hours, or the station's bank-holiday hours. */
  basis: "usual_hours" | "bank_holiday_hours";
  is24Hours?: boolean;
  /** "HH:MM" local time when it closes (open) or next opens (closed). */
  closesAt?: string;
  opensAt?: string;
  closesInMinutes?: number;
  /** Set when the bank-holiday calendar does not cover this date, so holiday hours could not be checked. */
  calendarExpired?: boolean;
}

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const toMinutes = (t: string) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
const DIVISIONS: readonly Division[] = ["england-and-wales", "scotland", "northern-ireland"];
const HOLIDAYS = new Map(DIVISIONS.map((d) => [d, new Set<string>(BANK_HOLIDAYS[d])]));
const CALENDAR_END = `${BANK_HOLIDAYS.coversUntil.slice(0, 4)}-12-31`;

/** Local UK date, weekday (0 = Monday) and minutes since midnight at an instant. */
export function ukClock(at: Date): { date: string; day: number; minutes: number } {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London", weekday: "long", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(at);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return {
    date: `${get("year")}-${get("month")}-${get("day")}`,
    day: DAYS.indexOf(get("weekday")),
    minutes: Number(get("hour")) * 60 + Number(get("minute")),
  };
}

const previousDate = (date: string) => new Date(Date.parse(`${date}T12:00:00Z`) - 86_400_000).toISOString().slice(0, 10);
const nextDate = (date: string) => new Date(Date.parse(`${date}T12:00:00Z`) + 86_400_000).toISOString().slice(0, 10);

type Effective = { hours: DayHours; basis: OpeningStatus["basis"] } | "unknown";

/** The hours that apply on a local date: bank-holiday hours replace usual hours on that nation's bank holidays. */
function hoursOn(h: StationHours, date: string, day: number): Effective {
  const usual = { hours: h.week[day]!, basis: "usual_hours" as const };
  if (date > CALENDAR_END) return usual;
  const holiday = h.division
    ? HOLIDAYS.get(h.division)!.has(date)
    : DIVISIONS.some((d) => HOLIDAYS.get(d)!.has(date)) ? null : false;
  // Nation unknown and it is a bank holiday somewhere in the UK: we cannot tell which hours apply.
  if (holiday === null) return h.bankHoliday ? "unknown" : usual;
  if (holiday && h.bankHoliday) return { hours: h.bankHoliday, basis: "bank_holiday_hours" };
  return usual;
}

/** "00:00" to "00:00" without the 24-hour flag means closed all day (confirmed by Fuel Finder). */
const closedAllDay = (d: DayHours) => !d.is24Hours && d.open === "00:00" && d.close === "00:00";
/** Any other open == close without the flag is not described by the provider, so it stays unknown. */
const ambiguous = (d: DayHours) => !d.is24Hours && d.open === d.close && !closedAllDay(d);
const overnight = (d: DayHours) => !d.is24Hours && d.open !== d.close && toMinutes(d.close) <= toMinutes(d.open);

export function openingStatus(h: StationHours | null, at: Date): OpeningStatus {
  if (!h) return { state: "unknown", basis: "usual_hours" };
  const { date, day, minutes } = ukClock(at);
  if (day < 0) return { state: "unknown", basis: "usual_hours" };
  const expired = date > CALENDAR_END ? { calendarExpired: true } : {};

  const yDate = previousDate(date);
  const y = hoursOn(h, yDate, (day + 6) % 7);
  if (y !== "unknown" && overnight(y.hours) && minutes < toMinutes(y.hours.close)) {
    return { state: "open", basis: y.basis, closesAt: y.hours.close, closesInMinutes: toMinutes(y.hours.close) - minutes, ...expired };
  }

  const t = hoursOn(h, date, day);
  if (t === "unknown") return { state: "unknown", basis: "bank_holiday_hours" };
  const today = t.hours;
  const basis = t.basis;
  if (today.is24Hours) return { state: "open", basis, is24Hours: true, ...expired };
  if (ambiguous(today)) return { state: "unknown", basis, ...expired };
  if (closedAllDay(today)) return { state: "closed", basis, ...nextOpening(h, date, day), ...expired };

  const open = toMinutes(today.open);
  const close = toMinutes(today.close);
  if (close > open) {
    if (minutes >= open && minutes < close) return { state: "open", basis, closesAt: today.close, closesInMinutes: close - minutes, ...expired };
    if (minutes < open) return { state: "closed", basis, opensAt: today.open, ...expired };
    return { state: "closed", basis, ...nextOpening(h, date, day), ...expired };
  }
  if (minutes >= open) return { state: "open", basis, closesAt: today.close, closesInMinutes: close + 1440 - minutes, ...expired };
  return { state: "closed", basis, opensAt: today.open, ...expired };
}

function nextOpening(h: StationHours, date: string, day: number): { opensAt?: string } {
  const n = hoursOn(h, nextDate(date), (day + 1) % 7);
  if (n === "unknown") return {};
  const d = n.hours;
  return d.is24Hours ? { opensAt: "00:00" } : d.open === d.close ? {} : { opensAt: d.open };
}

const dayJson = (d: DayHours) => ({ o: d.open, c: d.close, h: d.is24Hours ? 1 : 0 });

export const serialiseHours = (h: StationHours): string =>
  JSON.stringify({ w: h.week.map(dayJson), bh: h.bankHoliday ? dayJson(h.bankHoliday) : null, d: h.division });

function parseDay(o: unknown): DayHours | null {
  const d = o as { o?: unknown; c?: unknown; h?: unknown } | null;
  if (!d || typeof d.o !== "string" || typeof d.c !== "string" || !/^\d{2}:\d{2}$/.test(d.o) || !/^\d{2}:\d{2}$/.test(d.c)) return null;
  return { open: d.o, close: d.c, is24Hours: d.h === 1 };
}

/** Reads stored hours. Accepts the earlier format (a bare seven-day array) as usual hours only. */
export function parseHours(value: unknown): StationHours | null {
  const raw = Array.isArray(value) ? { w: value, bh: null, d: null } : (value as { w?: unknown; bh?: unknown; d?: unknown } | null);
  if (!raw || !Array.isArray(raw.w) || raw.w.length !== 7) return null;
  const week = raw.w.map(parseDay);
  if (week.some((d) => d === null)) return null;
  const bankHoliday = raw.bh == null ? null : parseDay(raw.bh);
  const division = DIVISIONS.includes(raw.d as Division) ? (raw.d as Division) : null;
  return { week: week as DayHours[], bankHoliday, division };
}

const SCOTTISH_AREAS = new Set(["AB", "DD", "DG", "EH", "FK", "G", "HS", "IV", "KA", "KW", "KY", "ML", "PA", "PH", "TD", "ZE"]);

/**
 * Which bank-holiday calendar applies. The feed's country field is inconsistent ("ENGLAND", "England",
 * "UNITED KINGDOM", empty, null, "E"), so a clear country wins and otherwise the postcode area decides.
 */
export function divisionFor(country: unknown, postcode: unknown): Division | null {
  const c = typeof country === "string" ? country.trim().toUpperCase() : "";
  if (c === "ENGLAND" || c === "WALES") return "england-and-wales";
  if (c === "SCOTLAND") return "scotland";
  if (c === "NORTHERN IRELAND") return "northern-ireland";
  const pc = typeof postcode === "string" ? postcode.trim().toUpperCase() : "";
  const area = /^([A-Z]{1,2})\d/.exec(pc)?.[1];
  if (!area) return null;
  if (area === "BT") return "northern-ireland";
  if (area === "TD" && /^TD15\b/.test(pc)) return "england-and-wales"; // Berwick-upon-Tweed is in England.
  if (SCOTTISH_AREAS.has(area)) return "scotland";
  return "england-and-wales";
}
