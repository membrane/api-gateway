import { useState } from "react";
import { api, ApiError, path, type Schemas, type Statistics } from "../api/client";
import { ErrorBox } from "../components/ErrorBox";
import { FlowTree, type FlowNode } from "../components/FlowTree";
import { useLoad, usePrincipal } from "../hooks";
import { href } from "../router";
import { countPlugins } from "../util/flow";
import { bytes } from "../util/format";

type ApiDetail = Schemas["ApiDetail"];

/** One API: how requests are matched, where they go, its statistics and its flow. */
export function ApiPage({ instance, apiId }: { instance: string; apiId: string }) {
  const apiPath = path("apis", instance, apiId);
  const detail = useLoad((signal) => api.get<ApiDetail>(apiPath, undefined, signal), [apiPath]);
  const flow = useLoad((signal) => api.get<{ flow: FlowNode[] }>(apiPath + "/flow", undefined, signal), [apiPath]);
  const permissions = usePrincipal();
  const [actionError, setActionError] = useState<ApiError>();
  const [removed, setRemoved] = useState(false);
  const d = detail.data;

  const run = (action: () => Promise<unknown>, onDone: () => void) =>
    action().then(
      () => {
        setActionError(undefined);
        onDone();
      },
      (e) => setActionError(e instanceof ApiError ? e : new ApiError(0, String(e))),
    );

  return (
    <section>
      <p><a href={href(["apis"])}>APIs</a> › {instance}</p>
      <h1>
        {d?.name ?? apiId}
        {d && <span className="badge">{d.type}</span>}
        {d && !d.active && <span className="badge badge-error" title={d.error}>inactive</span>}
      </h1>
      {removed ? <p className="muted">The API was removed.</p> : detail.error && <ErrorBox error={detail.error} />}
      {actionError && <ErrorBox error={actionError} />}
      {d && !removed && (
        <>
          {d.error && <div className="notice notice-error">{d.error}</div>}
          {d.description && <p>{d.description}</p>}
          <dl className="meta">
            <dt>Instance</dt><dd>{instance}</dd>
            <dt>Port</dt><dd>{d.match?.ip ? `${d.match.ip}:` : ""}{d.match?.port}{d.match?.tls ? " (TLS)" : ""}</dd>
            {d.match?.host && <><dt>Host</dt><dd><code>{d.match.host}</code></dd></>}
            {d.match?.method && <><dt>Method</dt><dd>{d.match.method}</dd></>}
            {d.match?.path && <><dt>Path</dt><dd><code>{d.match.path}</code>{d.match.pathType && d.match.pathType !== "prefix" ? ` (${d.match.pathType})` : ""}</dd></>}
            {d.match?.test && <><dt>Test</dt><dd><code>{d.match.test}</code></dd></>}
            <dt>Target</dt><dd>{target(d)}</dd>
            {d.order !== undefined && <><dt>Routing order</dt><dd>{d.order}</dd></>}
            {d.source?.file && <><dt>Defined in</dt><dd><code>{d.source.file}{d.source.line ? `:${d.source.line}` : ""}</code></dd></>}
          </dl>
          <p className="actions">
            <a className="button" href={href(["calls"], { instance, proxy: apiId })}>Calls</a>
            {!permissions.readOnly && !d.active && (
              <button className="button" onClick={() => run(() => api.post(apiPath + "/start"), detail.reload)}>Start</button>
            )}
            {!permissions.readOnly && (
              <button
                className="button"
                onClick={() => {
                  if (window.confirm(`Remove ${d.name} from ${instance}? It comes back when the configuration is loaded again.`))
                    run(() => api.delete(apiPath), () => setRemoved(true));
                }}
              >
                Remove
              </button>
            )}
          </p>
          <h2>Statistics</h2>
          <StatisticsTable totals={d.totals} byStatusCode={d.byStatusCode} />
          <h2>Flow {flow.data && <span className="muted">({countPlugins(flow.data.flow)} plugins)</span>}</h2>
          {flow.error && <ErrorBox error={flow.error} />}
          {flow.data && <FlowTree nodes={flow.data.flow} />}
          <p className="muted">
            The <a href={href(["transport"])}>transport flow</a> runs before the flow of every API.
          </p>
        </>
      )}
    </section>
  );
}

function target(d: ApiDetail): string {
  const t = d.target;
  if (!t) return "none";
  if (t.balancer) return `load balancer ${t.balancer}`;
  if (t.url) return t.url;
  if (t.host) return `${t.tls ? "https" : "http"}://${t.host}${t.port ? ":" + t.port : ""}`;
  return "none";
}

function StatisticsTable({ totals, byStatusCode }: { totals?: Statistics; byStatusCode?: Record<string, Statistics> }) {
  const ms = (v: number | null | undefined) => (v === null || v === undefined ? "" : `${Math.round(v)} ms`);
  const rows: [string, Statistics | undefined][] = [["All", totals], ...Object.entries(byStatusCode ?? {}).map(([code, s]): [string, Statistics] => [`Status ${code}`, s])];
  if (!totals?.count) return <p className="muted">No calls yet.</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th /><th className="num">Calls</th><th className="num">Errors</th><th className="num">Min</th><th className="num">Max</th>
            <th className="num">Avg</th><th className="num">Request bytes</th><th className="num">Response bytes</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([label, s], i) => (
            <tr key={label} className={i > 0 ? "sub" : undefined}>
              <td>{label}</td>
              <td className="num">{s?.count}</td>
              <td className="num">{s?.errors}</td>
              <td className="num">{ms(s?.min)}</td>
              <td className="num">{ms(s?.max)}</td>
              <td className="num">{ms(s?.avg)}</td>
              <td className="num">{bytes(s?.requestBytes)}</td>
              <td className="num">{bytes(s?.responseBytes)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** The global flow of each instance, which runs before the flow of the API. */
export function TransportFlowPage() {
  const flows = useLoad(
    (signal) => api.get<{ instances: { instance?: string; flow?: FlowNode[] }[] }>("/transport/flow", undefined, signal),
    [],
  );
  return (
    <section>
      <p><a href={href(["apis"])}>APIs</a></p>
      <h1>Transport flow</h1>
      <p className="muted">These plugins run for every request on the instance, before the flow of the API that matches it.</p>
      {flows.error && <ErrorBox error={flows.error} />}
      {flows.data?.instances.map((i) => (
        <article key={i.instance} className="card wide">
          <h2>{i.instance} <span className="muted">({countPlugins(i.flow)} plugins)</span></h2>
          <FlowTree nodes={i.flow ?? []} />
        </article>
      ))}
    </section>
  );
}
