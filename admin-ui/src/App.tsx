import { useEffect } from "react";
import { api, type Instance } from "./api/client";
import { useLoad } from "./hooks";
import { href, useRoute } from "./router";
import { ApisPage } from "./pages/ApisPage";
import { ApiPage, TransportFlowPage } from "./pages/ApiPage";
import { CallsPage } from "./pages/CallsPage";
import { CallDetailPage } from "./pages/CallDetailPage";
import { SystemPage } from "./pages/SystemPage";
import { BalancersPage, NodePage } from "./pages/BalancersPage";
import { StatisticsPage } from "./pages/StatisticsPage";
import { ErrorBox } from "./components/ErrorBox";

const INSTANCES_REFRESH_MS = 15_000;

const TABS = [
  { id: "calls", label: "Calls" },
  { id: "apis", label: "APIs" },
  { id: "statistics", label: "Statistics" },
  { id: "balancers", label: "Load balancers" },
  { id: "system", label: "System" },
];

export function App() {
  const route = useRoute();
  const instances = useLoad((signal) => api.get<{ self: string; instances: Instance[] }>("/instances", undefined, signal), []);
  // The transport flow belongs to the APIs section.
  const tab = route.segments[0] === "transport" ? "apis" : (route.segments[0] ?? "calls");

  // Peers go down and come back: refresh their state, starting the next request only after the previous one ended.
  const reloadInstances = instances.reload;
  useEffect(() => {
    if (instances.loading) return;
    const timer = setTimeout(reloadInstances, INSTANCES_REFRESH_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instances.loading]);

  return (
    <div className="app">
      <header className="topbar">
        <a className="brand" href={href(["calls"])}>Membrane</a>
        <nav className="tabs" aria-label="Sections">
          {TABS.map((t) => (
            <a key={t.id} href={href([t.id])} className={tab === t.id ? "tab active" : "tab"} aria-current={tab === t.id ? "page" : undefined}>
              {t.label}
            </a>
          ))}
        </nav>
        <ul className="instances" aria-label="Instances">
          {instances.data?.instances.map((i) => (
            <li key={i.name} className={`instance instance-${i.status}`} title={i.error ?? i.url ?? "this instance"}>
              <span className="dot" aria-hidden="true" />
              {i.name}
              <span className="sr-only"> ({i.status})</span>
            </li>
          ))}
        </ul>
      </header>
      <main className="content">
        {instances.error && <ErrorBox error={instances.error} />}
        {page(route.segments)}
      </main>
    </div>
  );
}

function page(segments: string[]) {
  switch (segments[0] ?? "calls") {
    case "calls":
      // Keyed by call: switching calls must not show the previous call's data while the new one loads.
      return segments.length >= 3
        ? <CallDetailPage key={segments[1] + "/" + segments[2]} instance={segments[1]} id={segments[2]} />
        : <CallsPage />;
    case "apis":
      // #/apis/{instance}/{apiId}
      return segments.length >= 3
        ? <ApiPage key={segments[1] + "/" + segments[2]} instance={segments[1]} apiId={segments[2]} />
        : <ApisPage />;
    case "transport":
      return <TransportFlowPage />;
    case "statistics":
      return <StatisticsPage view={segments[1]} />;
    case "balancers":
      // #/balancers/{instance}/{balancer}/{cluster}/{host:port}
      return segments.length >= 5
        ? <NodePage key={segments.join("/")} instance={segments[1]} balancer={segments[2]} cluster={segments[3]} node={segments[4]} />
        : <BalancersPage />;
    case "system":
      return <SystemPage />;
    default:
      return <p>Page not found.</p>;
  }
}
