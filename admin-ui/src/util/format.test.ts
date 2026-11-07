import { bytes, duration, statusClass, time } from "./format";

describe("format", () => {
  it("bytes", () => {
    expect(bytes(0)).toBe("0 B");
    expect(bytes(1536)).toBe("1.5 KiB");
    expect(bytes(5 * 1024 * 1024)).toBe("5.0 MiB");
    expect(bytes(undefined)).toBe("");
    expect(bytes(-1)).toBe("");
  });

  it("duration", () => {
    expect(duration(42)).toBe("42s");
    expect(duration(303)).toBe("5m 3s");
    expect(duration(7500)).toBe("2h 5m");
    expect(duration(3 * 86400 + 4 * 3600)).toBe("3d 4h");
  });

  it("time keeps unparsable input", () => {
    expect(time("not a date")).toBe("not a date");
    expect(time(undefined)).toBe("");
    expect(time("2026-11-05T10:00:00.123Z")).toMatch(/^2026-11-0[4-6] \d\d:\d\d:00\.123$/); // UTC-12 to UTC+14
  });

  it("statusClass", () => {
    expect(statusClass(204)).toBe("status-2xx");
    expect(statusClass(503)).toBe("status-5xx");
    expect(statusClass(null)).toBe("status-none");
  });
});
