import { apiBase, path, queryString } from "./client";

describe("apiBase", () => {
  it("is relative to the console page", () => {
    expect(apiBase("http://gw.example.com:9000/console/index.html")).toBe("http://gw.example.com:9000/admin/api/v1");
    expect(apiBase("http://gw.example.com/prefix/console/")).toBe("http://gw.example.com/prefix/admin/api/v1");
  });
});

describe("queryString", () => {
  it("leaves out empty values", () => {
    expect(queryString({ a: "", b: undefined, c: null, d: [] })).toBe("");
  });

  it("joins lists with commas and encodes values", () => {
    expect(queryString({ type: ["api", "soapProxy"], q: "a b&c" })).toBe("?type=api%2CsoapProxy&q=a+b%26c");
  });

  it("keeps zero and false", () => {
    expect(queryString({ offset: 0, live: false })).toBe("?offset=0&live=false");
  });
});

describe("path", () => {
  it("encodes every segment", () => {
    expect(path("apis", "node-a", "shop:2000")).toBe("/apis/node-a/shop%3A2000");
    expect(path("exchanges", "a/b", 42)).toBe("/exchanges/a%2Fb/42");
  });
});
