// Android 8's system WebView can predate structuredClone and Array.at.
if (!window.structuredClone)
  window.structuredClone = <T>(value: T): T =>
    JSON.parse(JSON.stringify(value));
if (!Array.prototype.at)
  Object.defineProperty(Array.prototype, "at", {
    value: function (index: number) {
      return this[index < 0 ? this.length + index : index];
    },
    configurable: true,
  });
if (!Object.fromEntries)
  Object.fromEntries = function (
    entries: Iterable<readonly [PropertyKey, unknown]>,
  ) {
    const result: Record<PropertyKey, unknown> = {};
    for (const [k, v] of entries)
      Object.defineProperty(result, k, {
        value: v,
        enumerable: true,
        writable: true,
        configurable: true,
      });
    return result;
  };
if (typeof globalThis === "undefined")
  (window as unknown as { globalThis: Window }).globalThis = window;
if (!Array.prototype.flat)
  Object.defineProperty(Array.prototype, "flat", {
    value: function (depth = 1): unknown[] {
      return depth > 0
        ? this.reduce(
            (out: unknown[], v: unknown) =>
              out.concat(Array.isArray(v) ? v.flat(depth - 1) : v),
            [],
          )
        : this.slice();
    },
  });
if (!Array.prototype.flatMap)
  Object.defineProperty(Array.prototype, "flatMap", {
    value: function (
      fn: (v: unknown, i: number, a: unknown[]) => unknown,
      ctx?: unknown,
    ) {
      return this.map(fn, ctx).flat();
    },
  });
if (!Element.prototype.replaceChildren)
  Element.prototype.replaceChildren = function (...nodes: (Node | string)[]) {
    while (this.firstChild) this.removeChild(this.firstChild);
    for (const n of nodes)
      this.appendChild(typeof n === "string" ? document.createTextNode(n) : n);
  };
