import { api } from "../api/client";
import { useLoad } from "../hooks";
import { ErrorBox } from "../components/ErrorBox";
import { href, replaceParams, useRoute } from "../router";

interface Diff {
  left: string;
  right: string;
  startLine?: { left?: string; right?: string };
  headers: { name: string; left?: string | null; right?: string | null }[];
  body: { format?: string; identical?: boolean; unifiedDiff?: string };
}

/**
 * Compares two messages: #/diff?left=node-a/1/request&right=node-b/7/request
 */
export function DiffPage() {
  const route = useRoute();
  const left = route.params.get("left") ?? "";
  const right = route.params.get("right") ?? "";
  const diff = useLoad((signal) => api.get<Diff>("/exchanges/diff", { left, right }, signal), [left, right]);

  const switchMessage = (message: "request" | "response") =>
    replaceParams(route, { left: withMessage(left, message), right: withMessage(right, message) });
  const showing = left.endsWith("/response") ? "response" : "request";

  return (
    <section>
      <p><a href={href(["calls"])}>← Calls</a></p>
      <h1>Compare</h1>
      <div className="tabs secondary" role="tablist">
        {(["request", "response"] as const).map((m) => (
          <button key={m} role="tab" aria-selected={showing === m} className={showing === m ? "tab active" : "tab"} onClick={() => switchMessage(m)}>
            {m === "request" ? "Requests" : "Responses"}
          </button>
        ))}
      </div>
      {diff.error && <ErrorBox error={diff.error} />}
      {diff.data && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th /><th><Link ref_={diff.data.left} /></th><th><Link ref_={diff.data.right} /></th></tr>
              </thead>
              <tbody>
                <tr>
                  <th scope="row">Start line</th>
                  <td><code>{diff.data.startLine?.left}</code></td>
                  <td><code>{diff.data.startLine?.right}</code></td>
                </tr>
                {diff.data.headers.map((h) => (
                  <tr key={h.name}>
                    <th scope="row">{h.name}</th>
                    <td className={h.left == null ? "absent" : undefined}><code>{h.left ?? "absent"}</code></td>
                    <td className={h.right == null ? "absent" : undefined}><code>{h.right ?? "absent"}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {diff.data.headers.length === 0 && <p className="muted">The header fields are the same.</p>}
          <h2>Body</h2>
          {diff.data.body.identical ? (
            <p className="muted">The bodies are the same ({diff.data.body.format}).</p>
          ) : (
            <pre className="body diff">
              {(diff.data.body.unifiedDiff ?? "").split("\n").map((line, i) => (
                <span key={i} className={lineClass(line)}>{line + "\n"}</span>
              ))}
            </pre>
          )}
        </>
      )}
    </section>
  );
}

function Link({ ref_ }: { ref_: string }) {
  const [instance, id] = ref_.split("/");
  return <a href={href(["calls", instance, id])}>{ref_}</a>;
}

export function withMessage(ref: string, message: "request" | "response"): string {
  return ref.replace(/\/(request|response)$/, "") + "/" + message;
}

export function lineClass(line: string): string | undefined {
  if (line.startsWith("+++") || line.startsWith("---")) return "diff-file";
  if (line.startsWith("@@")) return "diff-hunk";
  if (line.startsWith("+")) return "diff-add";
  if (line.startsWith("-")) return "diff-del";
  return undefined;
}
