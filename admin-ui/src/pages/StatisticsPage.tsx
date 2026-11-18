import { useState } from "react";
import { api, type Page, type Statistics, type StreamPump } from "../api/client";
import { ErrorBox, PartialResult } from "../components/ErrorBox";
import { useLoad } from "../hooks";
import { href } from "../router";
import { bytes, duration, time } from "../util/format";

const VIEWS = [
  { id: "apis", label: "APIs" },
  { id: "clients", label: "Clients" },
  { id: "streams", label: "Streams" },
];

/** Statistics per API and per client, and the active stream pumps. */
export function StatisticsPage({ view }: { view?: string }) {
  const current = VIEWS.some((v) => v.id === view) ? view! : "apis";
  return (
    <section>
      <h1>Statistics</h1>
      <nav className="tabs secondary" aria-label="Statistics">
        {VIEWS.map((v) => (
          <a key={v.id} href={href(["statistics", v.id])} className={current === v.id ? "tab active" : "tab"} aria-current={current === v.id ? "page" : undefined}>
            {v.label}
          </a>
        ))}
      </nav>
      {current === "apis" && <ApiStatistics />}
      {current === "clients" && <ClientStatistics />}
      {current === "streams" && <Streams />}
    </section>
  );
}

interface ApiStatistic {
  instance?: string;
  apiId?: string;
  name?: string;
  totals?: Statistics;
  byStatusCode?: Record<string, Statistics>;
  openBackendConnections?: number;
}

function ApiStatistics() {
  const stats = useLoad((signal) => api.get<{ apis: ApiStatistic[] }>("/statistics/apis", undefined, signal), []);
  return (
    <>
      <div className="filters">
        <button className="button" onClick={stats.reload}>Reload</button>
      </div>
      {stats.error && <ErrorBox error={stats.error} />}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Instance</th><th>API</th><th className="num">Calls</th><th className="num">Errors</th>
              <th className="num">Min</th><th className="num">Max</th><th className="num">Avg</th>
              <th className="num">Request bytes</th><th className="num">Response bytes</th><th className="num">Backend connections</th>
            </tr>
          </thead>
          <tbody>
            {stats.data?.apis.map((a) => (
              <ApiRow key={a.instance + "/" + a.apiId} stat={a} />
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function ApiRow({ stat }: { stat: ApiStatistic }) {
  const [open, setOpen] = useState(false);
  const codes = Object.entries(stat.byStatusCode ?? {});
  return (
    <>
      <tr>
        <td>{stat.instance}</td>
        <td>
          {codes.length > 0 && (
            <button className="link" aria-expanded={open} onClick={() => setOpen(!open)} title="Calls by status code">
              {open ? "▾" : "▸"}
            </button>
          )}{" "}
          {stat.name}{" "}
          <a href={href(["calls"], { instance: stat.instance, proxy: stat.apiId })} className="muted">calls</a>
        </td>
        <StatisticCells stats={stat.totals} />
        <td className="num">{stat.openBackendConnections ?? "N/A"}</td>
      </tr>
      {open && codes.map(([code, s]) => (
        <tr key={code} className="sub">
          <td />
          <td>Status {code}</td>
          <StatisticCells stats={s} />
          <td />
        </tr>
      ))}
    </>
  );
}

function StatisticCells({ stats }: { stats?: Statistics }) {
  const ms = (v: number | null | undefined) => (v === null || v === undefined ? "" : `${Math.round(v)} ms`);
  return (
    <>
      <td className="num">{stats?.count}</td>
      <td className="num">{stats?.errors}</td>
      <td className="num">{ms(stats?.min)}</td>
      <td className="num">{ms(stats?.max)}</td>
      <td className="num">{ms(stats?.avg)}</td>
      <td className="num">{bytes(stats?.requestBytes)}</td>
      <td className="num">{bytes(stats?.responseBytes)}</td>
    </>
  );
}

interface ClientStatistic {
  instance?: string;
  client?: string;
  count?: number;
  min?: number;
  max?: number;
  avg?: number;
}

const PAGE_SIZE = 100;
const CLIENT_COLUMNS = [
  { id: "client", label: "Client", num: false },
  { id: "count", label: "Calls", num: true },
  { id: "min", label: "Min", num: true },
  { id: "max", label: "Max", num: true },
  { id: "avg", label: "Avg", num: true },
];

function ClientStatistics() {
  const [sort, setSort] = useState("count");
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const [offset, setOffset] = useState(0);
  const clients = useLoad(
    (signal) => api.get<Page & { clients: ClientStatistic[] }>("/statistics/clients", { sort, order, offset, limit: PAGE_SIZE }, signal),
    [sort, order, offset],
  );
  const sortBy = (column: string) => {
    setOffset(0);
    if (column === sort) setOrder(order === "asc" ? "desc" : "asc");
    else {
      setSort(column);
      setOrder(column === "client" ? "asc" : "desc");
    }
  };
  const page = clients.data;
  return (
    <>
      {clients.error && <ErrorBox error={clients.error} />}
      <PartialResult instances={page?.instances} />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Instance</th>
              {CLIENT_COLUMNS.map((c) => (
                <th key={c.id} className={c.num ? "num" : undefined} aria-sort={sort === c.id ? (order === "asc" ? "ascending" : "descending") : undefined}>
                  <button className="link" onClick={() => sortBy(c.id)}>
                    {c.label}{sort === c.id ? (order === "asc" ? " ▴" : " ▾") : ""}
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {page?.clients.map((c) => (
              <tr key={c.instance + "/" + c.client}>
                <td>{c.instance}</td>
                <td><a href={href(["calls"], { instance: c.instance, client: c.client })}>{c.client}</a></td>
                <td className="num">{c.count}</td>
                <td className="num">{c.min} ms</td>
                <td className="num">{c.max} ms</td>
                <td className="num">{c.avg} ms</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {page && (
        <div className="pager">
          <button className="button" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}>Previous</button>
          <span className="muted">
            {page.clients.length === 0 ? 0 : offset + 1}–{offset + page.clients.length} of {page.total} clients
          </span>
          <button className="button" disabled={offset + PAGE_SIZE >= page.total} onClick={() => setOffset(offset + PAGE_SIZE)}>Next</button>
        </div>
      )}
    </>
  );
}

function Streams() {
  const streams = useLoad((signal) => api.get<{ streams: StreamPump[] }>("/streams", undefined, signal), []);
  return (
    <>
      <div className="filters">
        <button className="button" onClick={streams.reload}>Reload</button>
      </div>
      {streams.error && <ErrorBox error={streams.error} />}
      {streams.data && streams.data.streams.length === 0 ? (
        <p className="muted">No active streams (tunnels, WebSockets).</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Instance</th><th>Name</th><th>API</th><th>Created</th><th className="num">Active</th><th className="num">Transferred</th>
              </tr>
            </thead>
            <tbody>
              {streams.data?.streams.map((s, i) => (
                <tr key={s.instance + "/" + s.name + "/" + i}>
                  <td>{s.instance}</td>
                  <td>{s.name}</td>
                  <td>{s.proxy}</td>
                  <td>{time(s.createdAt)}</td>
                  <td className="num">{duration(s.activeSeconds)}</td>
                  <td className="num">{bytes(s.transferredBytes)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
