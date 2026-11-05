import { api, type Instance } from "./api/client";
import { useLoad } from "./hooks";
import { href, useRoute } from "./router";
import { ApisPage } from "./pages/ApisPage";
import { CallsPage } from "./pages/CallsPage";
import { CallDetailPage } from "./pages/CallDetailPage";
import { SystemPage } from "./pages/SystemPage";
import { ErrorBox } from "./components/ErrorBox";

const TABS = [
  { id: "calls", label: "Calls" },
  { id: "apis", label: "APIs" },
  { id: "system", label: "System" },
];

export function App() {
  const route = useRoute();
  const instances = useLoad((signal) => api.get<{ self: string; instances: Instance[] }>("/instances", undefined, signal), []);
  const tab = route.segments[0] ?? "calls";

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
      return segments.length >= 3 ? <CallDetailPage instance={segments[1]} id={segments[2]} /> : <CallsPage />;
    case "apis":
      return <ApisPage />;
    case "system":
      return <SystemPage />;
    default:
      return <p>Page not found.</p>;
  }
}
