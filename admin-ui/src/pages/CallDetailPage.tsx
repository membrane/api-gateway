import { useEffect, useState } from "react";
import { api, download, path, type ApiError, type ExchangeDetail, type Header } from "../api/client";
import { useLoad } from "../hooks";
import { ErrorBox } from "../components/ErrorBox";
import { href } from "../router";
import { bytes, statusClass, time } from "../util/format";

type Message = "request" | "response";

const IN_FLIGHT_REFRESH_MS = 2000;

export function CallDetailPage({ instance, id }: { instance: string; id: string }) {
  const [message, setMessage] = useState<Message>("request");
  const exchange = useLoad((signal) => api.get<ExchangeDetail>(path("exchanges", instance, id), undefined, signal), [instance, id]);
  const e = exchange.data;

  // A call that is still running is refreshed until it completes; the next request starts after the previous ended.
  const reload = exchange.reload;
  const running = !!e && !e.completed && !exchange.error;
  useEffect(() => {
    if (!running || exchange.loading) return;
    const timer = setTimeout(reload, IN_FLIGHT_REFRESH_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running, exchange.loading]);

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
        {(["request", "response"] as Message[]).map((m) => (
          <button key={m} role="tab" aria-selected={message === m} className={message === m ? "tab active" : "tab"} onClick={() => setMessage(m)}>
            {m === "request" ? "Request" : "Response"}
          </button>
        ))}
      </div>
      <MessageView key={message + (e?.completed ? "/completed" : "")} instance={instance} id={id} message={message} />
      <Curl instance={instance} id={id} />
    </section>
  );
}

function MessageView({ instance, id, message }: { instance: string; id: string; message: Message }) {
  const base = path("exchanges", instance, id, message);
  const headers = useLoad((signal) => api.get<{ startLine?: string; headers: Header[] }>(base + "/headers", undefined, signal), [base]);
  const body = useLoad(
    (signal) => api.get<{ format: string; text: string; truncated: boolean; originalLength?: number }>(base + "/body/formatted", undefined, signal),
    [base],
  );
  const [downloadError, setDownloadError] = useState<ApiError>();

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
            <button type="button" className="button" onClick={() => download(base + "/body").then(() => setDownloadError(undefined), setDownloadError)}>Download</button>
          </div>
          {downloadError && <ErrorBox error={downloadError} />}
          {body.data.format === "binary" ? <p className="muted">Binary content.</p> : <pre className="body">{body.data.text}</pre>}
        </>
      )}
    </div>
  );
}

function Curl({ instance, id }: { instance: string; id: string }) {
  const [shown, setShown] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);
  const copy = async () => {
    try {
      // Only available in secure contexts (HTTPS or localhost).
      if (!navigator.clipboard) throw new Error("no clipboard");
      await navigator.clipboard.writeText(curl.data ?? "");
      setCopyFailed(false);
    } catch {
      setCopyFailed(true);
    }
  };
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
          <button className="button" onClick={copy}>Copy</button>
          {copyFailed && <p className="muted" role="alert">The browser does not allow copying here. Select the command and copy it manually.</p>}
        </>
      )}
    </details>
  );
}
