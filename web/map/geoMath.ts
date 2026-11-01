export function haversineKm(a: [number, number], b: [number, number]): number {
  const rad = Math.PI / 180,
    dlat = (b[1] - a[1]) * rad,
    dlon = (b[0] - a[0]) * rad;
  const h =
    Math.sin(dlat / 2) ** 2 +
    Math.cos(a[1] * rad) * Math.cos(b[1] * rad) * Math.sin(dlon / 2) ** 2;
  return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
}
