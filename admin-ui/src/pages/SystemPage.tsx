import { api, type SystemInfo } from "../api/client";
import { useLoad } from "../hooks";
import { ErrorBox } from "../components/ErrorBox";
import { bytes, duration } from "../util/format";

export function SystemPage() {
  const system = useLoad((signal) => api.get<{ instances: SystemInfo[] }>("/system", undefined, signal), []);
  return (
    <section>
      <h1>System</h1>
      {system.error && <ErrorBox error={system.error} />}
      <div className="cards">
        {system.data?.instances.map((s) => (
          <article key={s.instance} className="card">
            <h2>{s.instance}</h2>
            <dl className="meta">
              <dt>Membrane</dt><dd>{s.membraneVersion}</dd>
              <dt>Java</dt><dd>{s.javaVersion}</dd>
              <dt>OS</dt><dd>{s.os}</dd>
              <dt>Uptime</dt><dd>{duration(s.uptimeSeconds)}</dd>
              <dt>Heap</dt><dd>{bytes(s.heap?.used)} of {bytes(s.heap?.max)}</dd>
              <dt>Threads</dt><dd>{s.threads?.live}</dd>
              <dt>CPUs</dt><dd>{s.cpus}</dd>
            </dl>
          </article>
        ))}
      </div>
    </section>
  );
}
