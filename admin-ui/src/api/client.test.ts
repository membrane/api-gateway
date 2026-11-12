import { api, apiBase, fileName, getApiKey, path, queryString, setApiKey } from "./client";

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

describe("API key", () => {
  afterEach(() => {
    setApiKey(null);
    vi.unstubAllGlobals();
  });

  it("is sent with every request once set", async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response("{}", { headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await api.get("/system");
    setApiKey("secret");
    await api.post("/apis", { name: "a" });

    expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty("X-Api-Key");
    expect(fetchMock.mock.calls[1][1].headers).toEqual({ "X-Api-Key": "secret", "Content-Type": "application/json" });
  });

  it("is kept for the browser tab", () => {
    setApiKey("secret");
    expect(sessionStorage.getItem("membrane.adminApiKey")).toBe("secret");
    expect(getApiKey()).toBe("secret");
  });

  it("is rejected as a 401 ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ title: "Unauthorized.", detail: "A valid X-Api-Key header is required." }),
      { status: 401, headers: { "Content-Type": "application/problem+json" } })));

    await expect(api.get("/system")).rejects.toMatchObject({ status: 401, title: "Unauthorized." });
  });
});

describe("fileName", () => {
  it("reads the file name of a Content-Disposition header", () => {
    expect(fileName('attachment; filename="calls.har"')).toBe("calls.har");
    expect(fileName("attachment; filename=body.bin")).toBe("body.bin");
    expect(fileName("attachment")).toBeUndefined();
    expect(fileName(null)).toBeUndefined();
  });
});
