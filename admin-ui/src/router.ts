import { useEffect, useState } from "react";

/**
 * Hash based routing (#/calls/node-a/42), so deep links work without a server side fallback to index.html.
 */
export interface Route {
  /** Path segments, decoded. */
  segments: string[];
  /** Query parameters after the path, e.g. #/calls?statusCode=4xx */
  params: URLSearchParams;
}

export function parseHash(hash: string): Route {
  const raw = hash.replace(/^#\/?/, "");
  const [p, q = ""] = raw.split("?", 2);
  const segments = p.split("/").filter((s) => s !== "").map(decodeURIComponent);
  return { segments, params: new URLSearchParams(q) };
}

export function href(segments: (string | number)[], params?: Record<string, string | undefined>): string {
  const p = segments.map((s) => encodeURIComponent(String(s))).join("/");
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params ?? {})) if (v) q.set(k, v);
  const qs = q.toString();
  return "#/" + p + (qs ? "?" + qs : "");
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseHash(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseHash(window.location.hash));
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return route;
}

/** Replaces the query of the current hash route without adding a history entry. */
export function replaceParams(route: Route, params: Record<string, string | undefined>): void {
  window.history.replaceState(null, "", href(route.segments, params));
  window.dispatchEvent(new HashChangeEvent("hashchange"));
}
