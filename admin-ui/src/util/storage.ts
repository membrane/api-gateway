/**
 * Per-browser preferences (columns, saved filters). Storage can be unavailable (private windows, blocked site data),
 * so every access falls back to the default.
 */
export function load<T>(key: string, fallback: T): T {
  try {
    const raw = window.localStorage.getItem("membrane.console." + key);
    return raw === null ? fallback : (JSON.parse(raw) as T);
  } catch {
    return fallback;
  }
}

export function save(key: string, value: unknown): void {
  try {
    window.localStorage.setItem("membrane.console." + key, JSON.stringify(value));
  } catch {
    // not persisted
  }
}
