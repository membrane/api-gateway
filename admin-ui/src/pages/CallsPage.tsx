import { useEffect, useRef, useState } from "react";
import { api, ApiError, type ExchangeSummary, type Page } from "../api/client";
import { ErrorBox, PartialResult } from "../components/ErrorBox";
import { href, replaceParams, useRoute } from "../router";
import { statusClass, time } from "../util/format";

type ExchangePage = Page & { exchanges: ExchangeSummary[]; lastModified: Record<string, number> };

const PAGE_SIZE = 100;
const FILTERS = ["instance", "method", "statusCode", "q"] as const;

/**
 * The recorded calls of all instances, newest first. With live updates, the list long-polls the API and reloads as
 * soon as any instance records a call.
 */
export function CallsPage() {
  const route = useRoute();
  const filters = Object.fromEntries(FILTERS.map((f) => [f, route.params.get(f) ?? ""])) as Record<(typeof FILTERS)[number], string>;
  const [live, setLive] = useState(true);
  const [page, setPage] = useState<ExchangePage>();
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<ApiError>();
  const lastModified = useRef<Record<string, number> | undefined>(undefined);
  const key = JSON.stringify(filters);

  useEffect(() => {
    const controller = new AbortController();
    lastModified.current = undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;

    // Live updates only on the first page: older pages would shift under the reader.
    const following = live && offset === 0;
    const poll = async () => {
      try {
        const wait = following && lastModified.current ? { waitForModification: JSON.stringify(lastModified.current), timeout: 30 } : {};
        const result = await api.get<ExchangePage>("/exchanges", { ...filters, offset, limit: PAGE_SIZE, ...wait }, controller.signal);
        lastModified.current = result.lastModified;
        setPage(result);
        setError(undefined);
      } catch (e) {
        if (controller.signal.aborted) return;
        const error = e instanceof ApiError ? e : new ApiError(0, String(e));
        // A rejected filter: do not show the results of the previous filter below the error.
        if (error.status >= 400 && error.status < 500) setPage(undefined);
        setError(error);
      }
      if (following && !controller.signal.aborted) timer = setTimeout(poll, 1000);
    };
    poll();
    return () => {
      controller.abort();
      if (timer) clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, live, offset]);

  const setFilter = (name: string, value: string) => {
    setOffset(0);
    replaceParams(route, { ...filters, [name]: value || undefined });
  };

  return (
    <section>
      <h1>Calls</h1>
      <div className="filters">
        <label>
          Instance <input value={filters.instance} onChange={(e) => setFilter("instance", e.target.value)} placeholder="all" />
        </label>
        <label>
          Method
          <select value={filters.method} onChange={(e) => setFilter("method", e.target.value)}>
            <option value="">all</option>
            {["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"].map((m) => <option key={m}>{m}</option>)}
          </select>
        </label>
        <label>
          Status
          <select value={filters.statusCode} onChange={(e) => setFilter("statusCode", e.target.value)}>
            <option value="">all</option>
            {["2xx", "3xx", "4xx", "5xx"].map((s) => <option key={s}>{s}</option>)}
          </select>
        </label>
        <label>
          Search <input type="search" value={filters.q} onChange={(e) => setFilter("q", e.target.value)} placeholder="path, client, server" />
        </label>
        <label className="toggle">
          <input type="checkbox" checked={live} onChange={(e) => setLive(e.target.checked)} /> Live
        </label>
      </div>
      {error && <ErrorBox error={error} />}
      <PartialResult instances={page?.instances} />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Time</th><th>Instance</th><th>Status</th><th>Method</th><th>Path</th><th>API</th><th>Client</th><th className="num">Duration</th>
            </tr>
          </thead>
          <tbody>
            {page?.exchanges.map((e) => (
              <tr key={e.instance + "/" + e.id}>
                <td><a href={href(["calls", e.instance, e.id])}>{time(e.time)}</a></td>
                <td>{e.instance}</td>
                <td><span className={"status " + statusClass(e.statusCode)}>{e.statusCode ?? "…"}</span></td>
                <td>{e.method}</td>
                <td className="path"><code>{e.path}</code></td>
                <td>{e.proxy}</td>
                <td>{e.client}</td>
                <td className="num">{e.completed ? `${e.duration} ms` : "…"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {page && (
        <div className="pager">
          <button className="button" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}>Newer</button>
          <span className="muted">
            {page.exchanges.length === 0 ? 0 : offset + 1}–{offset + page.exchanges.length} of {page.total} calls
          </span>
          <button className="button" disabled={offset + PAGE_SIZE >= page.total} onClick={() => setOffset(offset + PAGE_SIZE)}>Older</button>
        </div>
      )}
    </section>
  );
}
