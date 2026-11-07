/**
 * Filters of the calls list. They live in the URL (#/calls?statusCode=4xx), so a filtered list can be bookmarked and
 * shared, and are translated to query parameters of GET /exchanges.
 */
export const FILTERS = ["instance", "method", "statusCode", "proxy", "client", "q", "from", "to", "minDuration", "header", "body"] as const;

export type Filter = (typeof FILTERS)[number];
export type Filters = Record<Filter, string>;

export function filtersFrom(params: URLSearchParams): Filters {
  return Object.fromEntries(FILTERS.map((f) => [f, params.get(f) ?? ""])) as Filters;
}

/**
 * Query parameters for the API. Times from datetime-local inputs are local times and are sent as RFC 3339.
 */
export function apiQuery(filters: Filters): Record<string, string | undefined> {
  const query: Record<string, string | undefined> = {};
  for (const f of FILTERS) {
    const value = filters[f];
    if (!value) continue;
    query[f] = f === "from" || f === "to" ? toRfc3339(value) : value;
  }
  return query;
}

export function toRfc3339(local: string): string | undefined {
  const date = new Date(local);
  return isNaN(date.getTime()) ? undefined : date.toISOString();
}

export function activeCount(filters: Filters): number {
  return FILTERS.filter((f) => filters[f]).length;
}

export interface SavedFilter {
  name: string;
  filters: Partial<Filters>;
}

/** Adds or replaces a saved filter by name; keeps only the set values. */
export function withSaved(saved: SavedFilter[], name: string, filters: Filters): SavedFilter[] {
  const kept = Object.fromEntries(Object.entries(filters).filter(([, v]) => v)) as Partial<Filters>;
  return [...saved.filter((s) => s.name !== name), { name, filters: kept }].sort((a, b) => a.name.localeCompare(b.name));
}
