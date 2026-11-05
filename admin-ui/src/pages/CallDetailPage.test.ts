import { parseHeaders } from "./CallDetailPage";

describe("parseHeaders", () => {
  it("parses name: value lines and keeps colons in values", () => {
    expect(parseHeaders("Host: a.example.com:8080\r\nX-A:  1 \n\nno colon\n: no name")).toEqual([
      { name: "Host", value: "a.example.com:8080" },
      { name: "X-A", value: "1" },
    ]);
  });
});
