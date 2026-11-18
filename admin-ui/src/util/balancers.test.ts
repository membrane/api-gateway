import { describe, expect, it } from "vitest";
import { health, nodeId, percent, statusLabel } from "./balancers";

describe("balancers", () => {
  it("identifies a node by host and port", () => {
    expect(nodeId({ host: "backend.example.com", port: 8080 })).toBe("backend.example.com:8080");
  });

  it("summarizes the node states of a cluster", () => {
    expect(health([])).toBe("no nodes");
    expect(health(undefined)).toBe("no nodes");
    expect(health([{ status: "UP" }, { status: "UP" }, { status: "DOWN" }])).toBe("2 up / 1 down");
    expect(health([{ status: "UP" }, { status: "TAKEOUT" }])).toBe("1 up / 0 down / 1 take out");
  });

  it("formats error rates", () => {
    expect(percent(0.125)).toBe("12.5 %");
    expect(percent(0)).toBe("0.0 %");
    expect(percent(undefined)).toBe("");
    expect(percent(NaN)).toBe("");
  });

  it("labels node states", () => {
    expect(statusLabel("UP")).toBe("up");
    expect(statusLabel("TAKEOUT")).toBe("take out");
    expect(statusLabel(undefined)).toBe("");
  });
});
