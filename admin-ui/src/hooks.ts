import { useEffect, useState } from "react";
import { ApiError } from "./api/client";

export interface Loaded<T> {
  data?: T;
  error?: ApiError;
  loading: boolean;
}

/**
 * Loads data and reloads it whenever one of the dependencies changes. Outdated responses are dropped.
 */
export function useLoad<T>(load: (signal: AbortSignal) => Promise<T>, deps: unknown[]): Loaded<T> & { reload: () => void } {
  const [state, setState] = useState<Loaded<T>>({ loading: true });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState((s) => ({ ...s, loading: true }));
    load(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ data, loading: false });
      })
      .catch((e) => {
        if (controller.signal.aborted) return;
        setState({ error: e instanceof ApiError ? e : new ApiError(0, String(e)), loading: false });
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { ...state, reload: () => setTick((t) => t + 1) };
}
