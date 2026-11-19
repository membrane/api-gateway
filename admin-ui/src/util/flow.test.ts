import { describe, expect, it } from "vitest";
import { countPlugins, docLink, flowLabel, plainText } from "./flow";

describe("flow", () => {
  it("shows descriptions as plain text", () => {
    expect(plainText("Logs the <b>headers</b>.<br/>Level: INFO")).toBe("Logs the headers . Level: INFO");
    expect(plainText("a &lt;b&gt; &amp; c")).toBe("a <b> & c");
    expect(plainText(undefined)).toBe("");
  });

  it("links only to https documentation", () => {
    expect(docLink("https://www.membrane-api.io/docs/log.html")).toBe("https://www.membrane-api.io/docs/log.html");
    expect(docLink("javascript:alert(1)")).toBeUndefined();
    expect(docLink("http://example.com")).toBeUndefined();
    expect(docLink(null)).toBeUndefined();
  });

  it("labels plugins that run in one direction only", () => {
    expect(flowLabel("request")).toBe("request only");
    expect(flowLabel("both")).toBeNull();
    expect(flowLabel(undefined)).toBeNull();
  });

  it("counts nested plugins without the branches", () => {
    expect(countPlugins([
      { name: "log" },
      { name: "if", children: [{ name: "rateLimiter" }, { name: "else", children: [{ name: "return" }] }] },
      { name: "choose", children: [{ name: "case", children: [{ name: "static" }] }, { name: "otherwise", children: [] }] },
    ])).toBe(6);
    expect(countPlugins(undefined)).toBe(0);
  });
});
