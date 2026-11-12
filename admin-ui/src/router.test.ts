import { href, parseHash } from "./router";

describe("router", () => {
  it("parses segments and parameters", () => {
    const route = parseHash("#/calls/node-a/42?statusCode=4xx");
    expect(route.segments).toEqual(["calls", "node-a", "42"]);
    expect(route.params.get("statusCode")).toBe("4xx");
  });

  it("parses an empty hash", () => {
    expect(parseHash("").segments).toEqual([]);
  });

  it("round-trips encoded segments", () => {
    const link = href(["calls", "node/a", 1], { q: "a b", empty: "" });
    expect(link).toBe("#/calls/node%2Fa/1?q=a+b");
    expect(parseHash(link).segments).toEqual(["calls", "node/a", "1"]);
  });
});
