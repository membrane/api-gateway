import { lineClass, withMessage } from "./DiffPage";

describe("diff page", () => {
  it("switches between request and response", () => {
    expect(withMessage("node-a/1/request", "response")).toBe("node-a/1/response");
    expect(withMessage("node-a/1", "request")).toBe("node-a/1/request");
  });

  it("classifies unified diff lines", () => {
    expect(lineClass("--- a")).toBe("diff-file");
    expect(lineClass("@@ -1,2 +1,2 @@")).toBe("diff-hunk");
    expect(lineClass("+x")).toBe("diff-add");
    expect(lineClass("-x")).toBe("diff-del");
    expect(lineClass(" x")).toBeUndefined();
  });
});
