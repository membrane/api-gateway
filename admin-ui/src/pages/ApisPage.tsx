import { useState } from "react";
import { api, type ApiSummary, type Page } from "../api/client";
import { useLoad } from "../hooks";
import { ErrorBox, PartialResult } from "../components/ErrorBox";

const TYPES = ["api", "serviceProxy", "soapProxy", "proxy", "internal", "stompProxy", "kafkaConsumer", "other"];

export function ApisPage() {
  const [q, setQ] = useState("");
  const [type, setType] = useState("");
  const apis = useLoad(
    (signal) => api.get<Page & { apis: ApiSummary[] }>("/apis", { q, type, limit: 1000 }, signal),
    [q, type],
  );

  return (
    <section>
      <h1>APIs</h1>
      <div className="filters">
        <label>
          Search <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="name, path, host or target" />
        </label>
        <label>
          Type
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">all</option>
            {TYPES.map((t) => <option key={t}>{t}</option>)}
          </select>
        </label>
      </div>
      {apis.error && <ErrorBox error={apis.error} />}
      <PartialResult instances={apis.data?.instances} />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Instance</th><th>Name</th><th>Type</th><th>Port</th><th>Method</th><th>Path</th><th>Target</th><th className="num">Calls</th>
            </tr>
          </thead>
          <tbody>
            {apis.data?.apis.map((a) => (
              <tr key={a.instance + "/" + a.apiId} className={a.active ? undefined : "inactive"}>
                <td>{a.instance}</td>
                <td>
                  {a.name}
                  {!a.active && <span className="badge badge-error" title={a.error}>inactive</span>}
                </td>
                <td>{a.type}</td>
                <td>{a.match?.port}</td>
                <td>{a.match?.method}</td>
                <td><code>{a.match?.path}</code></td>
                <td>{a.target?.url ?? (a.target?.host ? `${a.target.host}:${a.target.port}` : a.target?.balancer)}</td>
                <td className="num">{a.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {apis.data && <p className="muted">{apis.data.total} APIs</p>}
    </section>
  );
}
