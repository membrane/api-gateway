import { activeCount, apiQuery, filtersFrom, withSaved } from "./callFilters";

describe("call filters", () => {
  it("reads filters from the route and ignores unknown parameters", () => {
    const f = filtersFrom(new URLSearchParams("statusCode=4xx&foo=bar"));
    expect(f.statusCode).toBe("4xx");
    expect(f.method).toBe("");
    expect(activeCount(f)).toBe(1);
  });

  it("sends times as RFC 3339", () => {
    const f = filtersFrom(new URLSearchParams("from=2026-11-05T10:00"));
    const from = apiQuery(f).from!;
    expect(from).toMatch(/^2026-11-0[45]T\d\d:\d\d:00\.000Z$/); // offsets like +05:30 change the minutes
    expect(new Date(from).getTime()).toBe(new Date("2026-11-05T10:00").getTime());
  });

  it("drops invalid times and empty filters", () => {
    expect(apiQuery(filtersFrom(new URLSearchParams("from=nonsense&method=")))).toEqual({ from: undefined });
  });

  it("saves only set filters and replaces by name", () => {
    const f = filtersFrom(new URLSearchParams("statusCode=5xx"));
    const saved = withSaved([{ name: "errors", filters: { statusCode: "4xx" } }, { name: "a", filters: {} }], "errors", f);
    expect(saved).toEqual([
      { name: "a", filters: {} },
      { name: "errors", filters: { statusCode: "5xx" } },
    ]);
  });
});
