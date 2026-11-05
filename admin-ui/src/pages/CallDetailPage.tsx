import { useEffect, useState } from "react";
import { api, ApiError, path, url, type ExchangeDetail, type Header, type Principal } from "../api/client";
import { useLoad } from "../hooks";
import { ErrorBox } from "../components/ErrorBox";
import { href } from "../router";
import { bytes, statusClass, time } from "../util/format";

type Message = "request" | "response";
type Tab = Message | "frames";

export function CallDetailPage({ instance, id }: { instance: string; id: string }) {
  const [tab, setTab] = useState<Tab>("request");
  const exchange = useLoad((signal) => api.get<ExchangeDetail>(path("exchanges", instance, id), undefined, signal), [instance, id]);
  const e = exchange.data;

  return (
    <section>
      <p><a href={href(["calls"])}>← Calls</a></p>
      <h1>
        {e ? <>{e.method} <code>{e.path}</code></> : "Call"}
        {e?.statusCode && <span className={"status " + statusClass(e.statusCode)}>{e.statusCode}</span>}
      </h1>
      {exchange.error && <ErrorBox error={exchange.error} />}
      {e && (
        <dl className="meta">
          <dt>Time</dt><dd>{time(e.time)}</dd>
          <dt>Instance</dt><dd>{e.instance}</dd>
          <dt>API</dt><dd>{e.proxy}</dd>
          <dt>Client</dt><dd>{e.client}</dd>
          <dt>Server</dt><dd>{e.server}{e.serverPort ? ":" + e.serverPort : ""}</dd>
          <dt>Duration</dt><dd>{e.completed ? `${e.duration} ms` : "not finished"}</dd>
          {e.error && <><dt>Error</dt><dd>{e.error}</dd></>}
        </dl>
      )}
      <div className="tabs secondary" role="tablist">
        {((e?.websocket ? ["request", "response", "frames"] : ["request", "response"]) as Tab[]).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? "tab active" : "tab"} onClick={() => setTab(t)}>
            {t === "request" ? "Request" : t === "response" ? "Response" : "WebSocket messages"}
          </button>
        ))}
      </div>
      {tab === "frames" ? <FramesView instance={instance} id={id} /> : <MessageView key={tab} instance={instance} id={id} message={tab} />}
      <Curl instance={instance} id={id} />
      <Replay instance={instance} id={id} exchange={e} />
    </section>
  );
}

interface ReplayResult {
  url: string;
  statusCode: number;
  statusMessage?: string | null;
  durationMillis: number;
  headers: Header[];
  body: { format: string; text: string; truncated: boolean };
}

/**
 * Sends the recorded request again, optionally changed. Only offered when the caller may change state.
 */
function Replay({ instance, id, exchange }: { instance: string; id: string; exchange?: ExchangeDetail }) {
  const me = useLoad((signal) => api.get<Principal>("/auth/me", undefined, signal), []);
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState<"gateway" | "backend">("gateway");
  const [method, setMethod] = useState("");
  const [requestPath, setRequestPath] = useState("");
  const [headers, setHeaders] = useState("");
  const [body, setBody] = useState("");
  const [result, setResult] = useState<ReplayResult>();
  const [error, setError] = useState<ApiError>();
  const [sending, setSending] = useState(false);

  if (!me.data || me.data.readOnly || !exchange) return null;

  const start = async () => {
    setOpen(true);
    setMethod(exchange.method ?? "GET");
    setRequestPath(exchange.path ?? "/");
    const h = await api.get<{ headers: Header[] }>(path("exchanges", instance, id, "request", "headers"));
    setHeaders(h.headers.map((x) => `${x.name}: ${x.value}`).join("\n"));
    const b = await api.get<{ format: string; text: string }>(path("exchanges", instance, id, "request", "body", "formatted"));
    setBody(b.format === "empty" || b.format === "binary" ? "" : b.text);
  };

  const send = async () => {
    setSending(true);
    setError(undefined);
    setResult(undefined);
    try {
      setResult(await api.post<ReplayResult>(path("exchanges", instance, id, "replay"), {
        target,
        method,
        path: requestPath,
        headers: parseHeaders(headers),
        body,
      }));
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)));
    } finally {
      setSending(false);
    }
  };

  if (!open) return <p><button className="button" onClick={start}>Replay…</button></p>;

  return (
    <div className="card replay">
      <h2>Replay</h2>
      <div className="filters">
        <label>
          Send to
          <select value={target} onChange={(e) => setTarget(e.target.value as "gateway" | "backend")}>
            <option value="gateway">gateway (runs through the API)</option>
            <option value="backend">backend (directly)</option>
          </select>
        </label>
        <label>Method <input value={method} onChange={(e) => setMethod(e.target.value)} size={8} /></label>
        <label className="grow">Path <input value={requestPath} onChange={(e) => setRequestPath(e.target.value)} /></label>
      </div>
      <label className="block">Header fields (one per line)<textarea rows={6} value={headers} onChange={(e) => setHeaders(e.target.value)} /></label>
      <label className="block">Body<textarea rows={8} value={body} onChange={(e) => setBody(e.target.value)} /></label>
      <p>
        <button className="button primary" onClick={send} disabled={sending}>{sending ? "Sending…" : "Send"}</button>{" "}
        <button className="link" onClick={() => setOpen(false)}>Close</button>
      </p>
      {error && <ErrorBox error={error} />}
      {result && (
        <div>
          <p>
            <span className={"status " + statusClass(result.statusCode)}>{result.statusCode}</span> {result.statusMessage} in {result.durationMillis} ms from <code>{result.url}</code>
          </p>
          <table className="headers">
            <tbody>
              {result.headers.map((h, i) => <tr key={i}><th scope="row">{h.name}</th><td><code>{h.value}</code></td></tr>)}
            </tbody>
          </table>
          {result.body.format !== "empty" && <pre className="body">{result.body.text}</pre>}
        </div>
      )}
    </div>
  );
}

/** Parses "Name: value" lines; lines without a colon are ignored. */
export function parseHeaders(text: string): Header[] {
  return text
    .split(/\r?\n/)
    .map((line) => {
      const colon = line.indexOf(":");
      return colon < 1 ? null : { name: line.slice(0, colon).trim(), value: line.slice(colon + 1).trim() };
    })
    .filter((h): h is Header => h !== null && h.name !== "");
}

function MessageView({ instance, id, message }: { instance: string; id: string; message: Message }) {
  const base = path("exchanges", instance, id, message);
  const headers = useLoad((signal) => api.get<{ startLine?: string; headers: Header[] }>(base + "/headers", undefined, signal), [base]);
  const body = useLoad(
    (signal) => api.get<{ format: string; text: string; truncated: boolean; originalLength?: number }>(base + "/body/formatted", undefined, signal),
    [base],
  );

  if (headers.error) return <ErrorBox error={headers.error} />;
  return (
    <div className="message">
      {headers.data && (
        <>
          <p><code>{headers.data.startLine}</code></p>
          <table className="headers">
            <tbody>
              {headers.data.headers.map((h, i) => (
                <tr key={i}><th scope="row">{h.name}</th><td><code>{h.value}</code></td></tr>
              ))}
            </tbody>
          </table>
        </>
      )}
      {body.error && <ErrorBox error={body.error} />}
      {body.data && body.data.format !== "empty" && (
        <>
          <div className="body-bar">
            <span className="muted">{body.data.format}, {bytes(body.data.originalLength)}{body.data.truncated ? " (truncated)" : ""}</span>
            <a className="button" href={url(base + "/body")} download>Download</a>
          </div>
          {body.data.format === "binary" ? <p className="muted">Binary content.</p> : <pre className="body">{body.data.text}</pre>}
        </>
      )}
    </div>
  );
}

interface Frames {
  total: number;
  dropped?: number;
  frames: { seq: number; time: string; direction: string; opcode: string; length: number; encoding: string; payload: string; truncated: boolean }[];
}

/**
 * Messages recorded by wsRecord; refreshed every two seconds while the connection may still be open.
 */
function FramesView({ instance, id }: { instance: string; id: string }) {
  const [tick, setTick] = useState(0);
  const frames = useLoad((signal) => api.get<Frames>(path("exchanges", instance, id, "frames"), { limit: 1000 }, signal), [instance, id, tick]);
  useEffect(() => {
    const timer = setInterval(() => setTick((t) => t + 1), 2000);
    return () => clearInterval(timer);
  }, []);

  if (frames.error) return <ErrorBox error={frames.error} />;
  if (!frames.data) return null;
  if (frames.data.total === 0) return <p className="muted">No messages recorded for this connection.</p>;
  return (
    <>
      {!!frames.data.dropped && <p className="muted">{frames.data.dropped} older messages were not kept.</p>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th className="num">#</th><th>Time</th><th>Direction</th><th>Type</th><th className="num">Size</th><th>Payload</th></tr>
          </thead>
          <tbody>
            {frames.data.frames.map((f) => (
              <tr key={f.seq}>
                <td className="num">{f.seq}</td>
                <td>{time(f.time)}</td>
                <td>{f.direction === "clientToServer" ? "client → server" : "server → client"}</td>
                <td>{f.opcode}</td>
                <td className="num">{bytes(f.length)}</td>
                <td className="payload">
                  <code>{f.payload}</code>
                  {f.encoding === "base64" && <span className="badge badge-muted">base64</span>}
                  {f.truncated && <span className="badge badge-muted">truncated</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function Curl({ instance, id }: { instance: string; id: string }) {
  const [shown, setShown] = useState(false);
  const curl = useLoad(
    (signal) => (shown ? api.get<string>(path("exchanges", instance, id, "curl"), undefined, signal) : Promise.resolve("")),
    [instance, id, shown],
  );
  return (
    <details onToggle={(e) => setShown((e.target as HTMLDetailsElement).open)}>
      <summary>As curl command</summary>
      {curl.error && <ErrorBox error={curl.error} />}
      {curl.data && (
        <>
          <pre className="body">{curl.data}</pre>
          <button className="button" onClick={() => navigator.clipboard?.writeText(curl.data ?? "")}>Copy</button>
        </>
      )}
    </details>
  );
}
