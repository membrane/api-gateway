import { useState, type FormEvent } from "react";
import { api, ApiError, path, type Balancer, type BalancerNode, type InstanceResult, type NodeDetail } from "../api/client";
import { ErrorBox, PartialResult } from "../components/ErrorBox";
import { useLoad, usePrincipal } from "../hooks";
import { href } from "../router";
import { health, nodeId, percent, statusLabel } from "../util/balancers";
import { time } from "../util/format";

type Permissions = ReturnType<typeof usePrincipal>;

/** Runs a change and reports its error, if any. */
function useAction(onDone: () => void) {
  const [error, setError] = useState<ApiError>();
  const run = (action: () => Promise<unknown>) =>
    action().then(
      () => {
        setError(undefined);
        onDone();
      },
      (e) => setError(e instanceof ApiError ? e : new ApiError(0, String(e))),
    );
  return { error, run };
}

/** The load balancers of all instances with their clusters and nodes. */
export function BalancersPage() {
  const balancers = useLoad((signal) => api.get<{ balancers: Balancer[]; instances?: InstanceResult[] }>("/balancers", undefined, signal), []);
  const permissions = usePrincipal();
  const action = useAction(balancers.reload);

  return (
    <section>
      <h1>Load balancers</h1>
      <div className="filters">
        <button className="button" onClick={balancers.reload}>Reload</button>
      </div>
      {balancers.error && <ErrorBox error={balancers.error} />}
      {action.error && <ErrorBox error={action.error} />}
      <PartialResult instances={balancers.data?.instances} />
      {balancers.data && balancers.data.balancers.length === 0 && <p className="muted">No load balancer is configured.</p>}
      {balancers.data?.balancers.map((b) => (
        <article key={b.instance + "/" + b.name} className="card wide">
          <h2>
            {b.name} <span className="muted">on {b.instance}{b.failover ? ", failover" : ""}</span>
          </h2>
          {b.clusters?.map((c) => (
            <ClusterTable key={c.name} balancer={b} cluster={c} permissions={permissions} run={action.run} />
          ))}
          {!permissions.readOnly && (
            <AddForm
              label="Add cluster"
              fields={[{ name: "name", label: "Name" }]}
              onSubmit={(v) => action.run(() => api.post(path("balancers", b.instance!, b.name!, "clusters"), { name: v.name }))}
            />
          )}
        </article>
      ))}
    </section>
  );
}

type Cluster = NonNullable<Balancer["clusters"]>[number];

function ClusterTable({ balancer, cluster, permissions, run }: {
  balancer: Balancer;
  cluster: Cluster;
  permissions: Permissions;
  run: (action: () => Promise<unknown>) => void;
}) {
  const nodesPath = path("balancers", balancer.instance!, balancer.name!, "clusters", cluster.name!, "nodes");
  return (
    <>
      <h3>
        Cluster {cluster.name} <span className="muted">{health(cluster.nodes)}</span>
      </h3>
      {(cluster.nodes?.length ?? 0) > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Node</th><th>Status</th><th className="num">Calls</th><th className="num">Errors</th><th>Last up</th>
                <th className="num">Sessions</th><th className="num">Threads</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {cluster.nodes!.map((n) => (
                <tr key={nodeId(n)}>
                  <td><a href={href(["balancers", balancer.instance!, balancer.name!, cluster.name!, nodeId(n)])}>{nodeId(n)}</a></td>
                  <td><NodeStatus node={n} /></td>
                  <td className="num">{n.count}</td>
                  <td className="num">{percent(n.errorRate)}</td>
                  <td>{time(n.lastUp)}</td>
                  <td className="num">{n.sessions}</td>
                  <td className="num">{n.threads}</td>
                  <td><NodeActions nodePath={nodesPath + path(nodeId(n))} node={n} permissions={permissions} run={run} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {!permissions.readOnly && (
        <AddForm
          label="Add node"
          fields={[{ name: "host", label: "Host" }, { name: "port", label: "Port", type: "number" }]}
          onSubmit={(v) => run(() => api.post(nodesPath, { host: v.host, port: Number(v.port) }))}
        />
      )}
    </>
  );
}

function NodeStatus({ node }: { node: BalancerNode }) {
  const css = node.status === "UP" ? "status-2xx" : node.status === "TAKEOUT" ? "status-3xx" : "status-5xx";
  return <span className={"status " + css}>{statusLabel(node.status)}</span>;
}

/**
 * Changing the state of a node needs the nodeOperator role, independent of readOnly; removing it needs write access.
 */
function NodeActions({ nodePath, node, permissions, run, onDeleted }: {
  nodePath: string;
  node: BalancerNode;
  permissions: Permissions;
  run: (action: () => Promise<unknown>) => void;
  onDeleted?: () => void;
}) {
  const state = (action: string) => run(() => api.post(nodePath + path(action)));
  return (
    <span className="actions">
      {permissions.canControlNodes && (
        <>
          {node.status !== "UP" && <button className="button" onClick={() => state("up")}>Up</button>}
          {node.status !== "DOWN" && <button className="button" onClick={() => state("down")}>Down</button>}
          {node.status === "UP" && <button className="button" title="No new sessions" onClick={() => state("takeout")}>Take out</button>}
          <button className="button" onClick={() => state("reset")}>Reset counter</button>
        </>
      )}
      {!permissions.readOnly && (
        <button
          className="button"
          onClick={() => {
            if (window.confirm(`Remove node ${nodeId(node)}?`)) run(() => api.delete(nodePath).then(() => onDeleted?.()));
          }}
        >
          Remove
        </button>
      )}
    </span>
  );
}

interface Field {
  name: string;
  label: string;
  type?: string;
}

function AddForm({ label, fields, onSubmit }: { label: string; fields: Field[]; onSubmit: (values: Record<string, string>) => void }) {
  const [values, setValues] = useState<Record<string, string>>({});
  const submit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit(values);
    setValues({});
  };
  return (
    <form className="filters" onSubmit={submit}>
      {fields.map((f) => (
        <label key={f.name}>
          {f.label}{" "}
          <input
            type={f.type ?? "text"}
            required
            min={f.type === "number" ? 1 : undefined}
            max={f.type === "number" ? 65535 : undefined}
            value={values[f.name] ?? ""}
            onChange={(e) => setValues({ ...values, [f.name]: e.target.value })}
          />
        </label>
      ))}
      <button className="button" type="submit">{label}</button>
    </form>
  );
}

/** One node with its calls by status code and its sessions. */
export function NodePage({ instance, balancer, cluster, node }: { instance: string; balancer: string; cluster: string; node: string }) {
  const nodePath = path("balancers", instance, balancer, "clusters", cluster, "nodes", node);
  const detail = useLoad((signal) => api.get<NodeDetail>(nodePath, undefined, signal), [nodePath]);
  const permissions = usePrincipal();
  const action = useAction(detail.reload);
  const [deleted, setDeleted] = useState(false);
  const d = detail.data;

  return (
    <section>
      <p><a href={href(["balancers"])}>Load balancers</a> › {balancer} › {cluster}</p>
      <h1>Node {node}</h1>
      {deleted ? <p className="muted">The node was removed.</p> : detail.error && <ErrorBox error={detail.error} />}
      {action.error && <ErrorBox error={action.error} />}
      {d && !deleted && (
        <>
          <dl className="meta">
            <dt>Instance</dt><dd>{instance}</dd>
            <dt>Status</dt><dd><NodeStatus node={d} /></dd>
            <dt>Calls</dt><dd>{d.count}</dd>
            <dt>Errors</dt><dd>{percent(d.errorRate)}</dd>
            <dt>Last up</dt><dd>{time(d.lastUp)}</dd>
            <dt>Threads</dt><dd>{d.threads}</dd>
          </dl>
          <p>
            <NodeActions nodePath={nodePath} node={d} permissions={permissions} run={action.run} onDeleted={() => setDeleted(true)} />
          </p>
          <h2>Calls by status code</h2>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Status code</th><th className="num">Calls</th></tr></thead>
              <tbody>
                {Object.entries(d.byStatusCode ?? {}).map(([code, count]) => (
                  <tr key={code}><td>{code}</td><td className="num">{count}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
          <h2>Sessions</h2>
          {(d.sessionList?.length ?? 0) === 0 ? (
            <p className="muted">No sessions.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Session</th><th>Last used</th></tr></thead>
                <tbody>
                  {d.sessionList!.map((s) => <tr key={s.id}><td><code>{s.id}</code></td><td>{time(s.lastUsed)}</td></tr>)}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}
