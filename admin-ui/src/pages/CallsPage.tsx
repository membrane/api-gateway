import { useEffect, useRef, useState, type ReactNode } from "react";
import { api, ApiError, url, type ExchangeSummary, type Page } from "../api/client";
import { ErrorBox, PartialResult } from "../components/ErrorBox";
import { href, replaceParams, useRoute } from "../router";
import { bytes, statusClass, time } from "../util/format";
import { load, save } from "../util/storage";
import { activeCount, apiQuery, filtersFrom, withSaved, type Filters, type SavedFilter } from "./callFilters";

type ExchangePage = Page & { exchanges: ExchangeSummary[]; lastModified: Record<string, number> };

const PAGE_SIZE = 100;

interface Column {
  id: string;
  label: string;
  num?: boolean;
  cell: (e: ExchangeSummary) => ReactNode;
}

const COLUMNS: Column[] = [
  { id: "time", label: "Time", cell: (e) => <a href={href(["calls", e.instance, e.id])}>{time(e.time)}</a> },
  { id: "instance", label: "Instance", cell: (e) => e.instance },
  { id: "statusCode", label: "Status", cell: (e) => <span className={"status " + statusClass(e.statusCode)}>{e.statusCode ?? "…"}</span> },
  { id: "method", label: "Method", cell: (e) => e.method },
  { id: "path", label: "Path", cell: (e) => <code>{e.path}</code> },
  { id: "proxy", label: "API", cell: (e) => e.proxy },
  { id: "client", label: "Client", cell: (e) => e.client },
  { id: "server", label: "Server", cell: (e) => e.server },
  { id: "protocol", label: "Protocol", cell: (e) => e.protocol },
  { id: "reqContentType", label: "Request type", cell: (e) => e.reqContentType },
  { id: "reqContentLength", label: "Request size", num: true, cell: (e) => bytes(e.reqContentLength) },
  { id: "respContentType", label: "Response type", cell: (e) => e.respContentType },
  { id: "respContentLength", label: "Response size", num: true, cell: (e) => bytes(e.respContentLength) },
  { id: "duration", label: "Duration", num: true, cell: (e) => (e.completed ? `${e.duration} ms` : "…") },
];

const DEFAULT_COLUMNS = ["time", "instance", "statusCode", "method", "path", "proxy", "client", "duration"];

const key = (e: ExchangeSummary) => `${e.instance}/${e.id}`;

/**
 * The recorded calls of all instances, newest first. With live updates, the list long-polls the API and reloads as
 * soon as any instance records a call.
 */
export function CallsPage() {
  const route = useRoute();
  const filters = filtersFrom(route.params);
  const [live, setLive] = useState(true);
  const [page, setPage] = useState<ExchangePage>();
  const [error, setError] = useState<ApiError>();
  const [columns, setColumns] = useState<string[]>(() => load("calls.columns", DEFAULT_COLUMNS));
  const [selected, setSelected] = useState<string[]>([]);
  const [moreFilters, setMoreFilters] = useState(() => activeCount(filters) > 0 && !!(filters.from || filters.to || filters.minDuration || filters.header || filters.body || filters.proxy || filters.client));
  const lastModified = useRef<Record<string, number> | undefined>(undefined);
  const filterKey = JSON.stringify(filters);

  useEffect(() => {
    const controller = new AbortController();
    lastModified.current = undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const poll = async () => {
      try {
        const wait = live && lastModified.current ? { waitForModification: JSON.stringify(lastModified.current), timeout: 30 } : {};
        const result = await api.get<ExchangePage>("/exchanges", { ...apiQuery(filters), limit: PAGE_SIZE, ...wait }, controller.signal);
        lastModified.current = result.lastModified;
        setPage(result);
        setError(undefined);
      } catch (e) {
        if (controller.signal.aborted) return;
        setError(e instanceof ApiError ? e : new ApiError(0, String(e)));
        if (e instanceof ApiError && e.status >= 400 && e.status < 500) return; // a bad filter does not get better by polling
      }
      if (live && !controller.signal.aborted) timer = setTimeout(poll, 1000);
    };
    poll();
    return () => {
      controller.abort();
      if (timer) clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterKey, live]);

  const setFilters = (next: Partial<Filters>) => {
    const merged = { ...filters, ...next };
    replaceParams(route, Object.fromEntries(Object.entries(merged).map(([k, v]) => [k, v || undefined])));
  };
  const setFilter = (name: keyof Filters, value: string) => setFilters({ [name]: value });

  const toggleColumn = (id: string) => {
    const next = columns.includes(id) ? columns.filter((c) => c !== id) : COLUMNS.map((c) => c.id).filter((c) => c === id || columns.includes(c));
    setColumns(next);
    save("calls.columns", next);
  };

  const toggleSelected = (k: string) => setSelected((s) => (s.includes(k) ? s.filter((x) => x !== k) : [...s, k]));
  const visible = COLUMNS.filter((c) => columns.includes(c.id));

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
        <button className="button" onClick={() => setMoreFilters((m) => !m)} aria-expanded={moreFilters}>
          {moreFilters ? "Fewer filters" : "More filters"}
        </button>
        <label className="toggle">
          <input type="checkbox" checked={live} onChange={(e) => setLive(e.target.checked)} /> Live
        </label>
      </div>
      {moreFilters && (
        <div className="filters">
          <label>From <input type="datetime-local" step="1" value={filters.from} onChange={(e) => setFilter("from", e.target.value)} /></label>
          <label>To <input type="datetime-local" step="1" value={filters.to} onChange={(e) => setFilter("to", e.target.value)} /></label>
          <label>Min. duration (ms) <input type="number" min="0" value={filters.minDuration} onChange={(e) => setFilter("minDuration", e.target.value)} /></label>
          <label>API <input value={filters.proxy} onChange={(e) => setFilter("proxy", e.target.value)} /></label>
          <label>Client <input value={filters.client} onChange={(e) => setFilter("client", e.target.value)} /></label>
          <label>Header <input value={filters.header} onChange={(e) => setFilter("header", e.target.value)} placeholder="name:value" /></label>
          <label>Body contains <input value={filters.body} onChange={(e) => setFilter("body", e.target.value)} /></label>
        </div>
      )}
      <Toolbar
        filters={filters}
        onApply={(f) => setFilters(f)}
        columns={columns}
        onToggleColumn={toggleColumn}
        selected={selected}
        onClearSelection={() => setSelected([])}
      />
      {error && <ErrorBox error={error} />}
      <PartialResult instances={page?.instances} />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th aria-label="Select" />
              {visible.map((c) => <th key={c.id} className={c.num ? "num" : undefined}>{c.label}</th>)}
            </tr>
          </thead>
          <tbody>
            {page?.exchanges.map((e) => (
              <tr key={key(e)} className={selected.includes(key(e)) ? "selected" : undefined}>
                <td>
                  <input type="checkbox" aria-label={`Select call ${e.id} of ${e.instance}`} checked={selected.includes(key(e))} onChange={() => toggleSelected(key(e))} />
                </td>
                {visible.map((c) => <td key={c.id} className={c.num ? "num" : c.id === "path" ? "path" : undefined}>{c.cell(e)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {page && <p className="muted">{page.exchanges.length} of {page.total} calls</p>}
    </section>
  );
}

function Toolbar(props: {
  filters: Filters;
  onApply: (f: Partial<Filters>) => void;
  columns: string[];
  onToggleColumn: (id: string) => void;
  selected: string[];
  onClearSelection: () => void;
}) {
  const [saved, setSaved] = useState<SavedFilter[]>(() => load("calls.savedFilters", []));
  const store = (next: SavedFilter[]) => {
    setSaved(next);
    save("calls.savedFilters", next);
  };
  const { selected } = props;
  const exportUrl = url("/exchanges/export") + "?" + selected.map((s) => "id=" + encodeURIComponent(s)).join("&");
  const [left, right] = selected;

  return (
    <div className="toolbar">
      <details className="menu">
        <summary className="button">Saved filters</summary>
        <div className="menu-body">
          {saved.length === 0 && <p className="muted">None yet.</p>}
          <ul>
            {saved.map((s) => (
              <li key={s.name}>
                <button className="link" onClick={() => props.onApply(Object.fromEntries(Object.keys(props.filters).map((k) => [k, (s.filters as Record<string, string>)[k] ?? ""])))}>
                  {s.name}
                </button>{" "}
                <button className="link muted" aria-label={`Delete ${s.name}`} onClick={() => store(saved.filter((x) => x.name !== s.name))}>×</button>
              </li>
            ))}
          </ul>
          <button
            className="button"
            disabled={activeCount(props.filters) === 0}
            onClick={() => {
              const name = window.prompt("Name of the filter");
              if (name) store(withSaved(saved, name, props.filters));
            }}
          >
            Save current filter
          </button>
        </div>
      </details>
      <details className="menu">
        <summary className="button">Columns</summary>
        <div className="menu-body">
          {COLUMNS.map((c) => (
            <label key={c.id} className="toggle">
              <input type="checkbox" checked={props.columns.includes(c.id)} onChange={() => props.onToggleColumn(c.id)} /> {c.label}
            </label>
          ))}
        </div>
      </details>
      <span className="spacer" />
      {selected.length > 0 && <span className="muted">{selected.length} selected</span>}
      <a className={"button" + (selected.length === 0 ? " disabled" : "")} aria-disabled={selected.length === 0} href={selected.length ? exportUrl : undefined} download>
        Export HAR
      </a>
      <a
        className={"button" + (selected.length !== 2 ? " disabled" : "")}
        aria-disabled={selected.length !== 2}
        title="Select two calls to compare their requests"
        href={selected.length === 2 ? href(["diff"], { left: left + "/request", right: right + "/request" }) : undefined}
      >
        Compare
      </a>
      {selected.length > 0 && <button className="link" onClick={props.onClearSelection}>Clear</button>}
    </div>
  );
}
