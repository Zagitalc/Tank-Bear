/** Decodes an encoded polyline (default 6-digit precision) into [lat, lon] pairs. */
export function decodePolyline(encoded: string, precision = 6): [number, number][] {
  const factor = 10 ** precision;
  const out: [number, number][] = [];
  let index = 0;
  let lat = 0;
  let lon = 0;
  const next = (): number => {
    let result = 0;
    let shift = 0;
    let byte: number;
    do {
      if (index >= encoded.length) throw new Error("truncated polyline");
      byte = encoded.charCodeAt(index++) - 63;
      if (byte < 0 || byte > 63) throw new Error("invalid polyline character");
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    return result & 1 ? ~(result >> 1) : result >> 1;
  };
  while (index < encoded.length) {
    lat += next();
    lon += next();
    out.push([lat / factor, lon / factor]);
  }
  return out;
}

export function encodePolyline(points: readonly (readonly [number, number])[], precision = 6): string {
  const factor = 10 ** precision;
  let prevLat = 0;
  let prevLon = 0;
  let out = "";
  const put = (value: number) => {
    let v = value < 0 ? ~(value << 1) : value << 1;
    while (v >= 0x20) {
      out += String.fromCharCode((0x20 | (v & 0x1f)) + 63);
      v >>= 5;
    }
    out += String.fromCharCode(v + 63);
  };
  for (const [lat, lon] of points) {
    const la = Math.round(lat * factor);
    const lo = Math.round(lon * factor);
    put(la - prevLat);
    put(lo - prevLon);
    prevLat = la;
    prevLon = lo;
  }
  return out;
}
