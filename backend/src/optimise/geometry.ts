import type { Coordinates } from "../domain/types.ts";

const R = 6_371_000;
const rad = Math.PI / 180;

export interface Box {
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
}

/** Local flat projection (metres) around a reference latitude: accurate to well under a metre per km at UK scale. */
function project(p: Coordinates, lat0: number, lon0: number): [number, number] {
  return [(p.lon - lon0) * rad * Math.cos(lat0 * rad) * R, (p.lat - lat0) * rad * R];
}

export interface RouteLine {
  points: Coordinates[];
  /** Cumulative metres at each point. */
  cumulative: number[];
  totalMetres: number;
}

export function buildLine(points: readonly [number, number][]): RouteLine {
  const pts = points.map(([lat, lon]) => ({ lat, lon }));
  const cumulative = [0];
  for (let i = 1; i < pts.length; i++) {
    const [x, y] = project(pts[i]!, pts[i - 1]!.lat, pts[i - 1]!.lon);
    cumulative.push(cumulative[i - 1]! + Math.hypot(x, y));
  }
  return { points: pts, cumulative, totalMetres: cumulative[cumulative.length - 1] ?? 0 };
}

/** Nearest approach of a point to the line, and how far along the line that is (0 to 1). */
export function locateOnLine(line: RouteLine, p: Coordinates): { offsetMetres: number; fraction: number } {
  let best = { offsetMetres: Number.POSITIVE_INFINITY, along: 0 };
  for (let i = 0; i < line.points.length - 1; i++) {
    const a = line.points[i]!;
    const b = line.points[i + 1]!;
    const [bx, by] = project(b, a.lat, a.lon);
    const [px, py] = project(p, a.lat, a.lon);
    const len2 = bx * bx + by * by;
    const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, (px * bx + py * by) / len2));
    const offset = Math.hypot(px - t * bx, py - t * by);
    if (offset < best.offsetMetres) best = { offsetMetres: offset, along: line.cumulative[i]! + t * Math.sqrt(len2) };
  }
  return { offsetMetres: best.offsetMetres, fraction: line.totalMetres === 0 ? 0 : best.along / line.totalMetres };
}

/** Boxes covering the line in chunks of about `chunkMetres`, each grown by `bufferMetres`. */
export function corridorBoxes(line: RouteLine, bufferMetres: number, chunkMetres = 20_000): Box[] {
  const boxes: Box[] = [];
  let start = 0;
  const grow = (pts: Coordinates[]): Box => {
    const lats = pts.map((p) => p.lat);
    const lons = pts.map((p) => p.lon);
    const mid = (Math.min(...lats) + Math.max(...lats)) / 2;
    const dLat = bufferMetres / (R * rad);
    const dLon = dLat / Math.max(Math.cos(mid * rad), 0.01);
    return { minLat: Math.min(...lats) - dLat, maxLat: Math.max(...lats) + dLat, minLon: Math.min(...lons) - dLon, maxLon: Math.max(...lons) + dLon };
  };
  for (let i = 1; i < line.points.length; i++) {
    if (line.cumulative[i]! - line.cumulative[start]! >= chunkMetres || i === line.points.length - 1) {
      boxes.push(grow(line.points.slice(start, i + 1)));
      start = i;
    }
  }
  return boxes.length ? boxes : [grow(line.points)];
}
