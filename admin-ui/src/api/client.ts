import type { components } from "./schema";

export type Schemas = components["schemas"];
export type Instance = Schemas["Instance"];
export type InstanceResult = Schemas["InstanceResult"];
export type ApiSummary = Schemas["ApiSummary"];
export type ExchangeSummary = Schemas["ExchangeSummary"];
export type ExchangeDetail = Schemas["ExchangeDetail"];
export type Header = Schemas["Header"];
export type SystemInfo = Schemas["SystemInfo"];
export type Principal = Schemas["Principal"];

export interface Page {
  total: number;
  offset?: number;
  limit?: number;
  instances?: InstanceResult[];
}

/** An error answered by the admin API as application/problem+json, or a network failure (status 0). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail?: string,
  ) {
    super(detail ?? title);
  }
}

/** The admin API relative to the page, so the console also works when mounted below a path prefix. */
export function apiBase(location: string = window.location.href): string {
  return new URL("../admin/api/v1", location).toString();
}

export type Query = Record<string, string | number | boolean | undefined | null | string[]>;

/** Builds a query string; empty values are left out, arrays become comma separated lists. */
export function queryString(query: Query = {}): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      if (value.length > 0) params.set(key, value.join(","));
    } else {
      params.set(key, String(value));
    }
  }
  const s = params.toString();
  return s ? "?" + s : "";
}

/** Builds an API path from segments, encoding each of them. */
export function path(...segments: (string | number)[]): string {
  return "/" + segments.map((s) => encodeURIComponent(String(s))).join("/");
}

export function url(p: string, query?: Query): string {
  return apiBase() + p + queryString(query);
}

const API_KEY_STORAGE = "membrane.adminApiKey";

/** The key an admin API with an apiKey requires, kept for this browser tab only. */
export function getApiKey(): string | null {
  try {
    return sessionStorage.getItem(API_KEY_STORAGE);
  } catch {
    return null;
  }
}

export function setApiKey(key: string | null): void {
  try {
    if (key) sessionStorage.setItem(API_KEY_STORAGE, key);
    else sessionStorage.removeItem(API_KEY_STORAGE);
  } catch {
    // storage unavailable: the key is asked for again
  }
}

function authHeaders(): Record<string, string> {
  const key = getApiKey();
  return key ? { "X-Api-Key": key } : {};
}

/** The file name of a Content-Disposition header, if it has one. */
export function fileName(contentDisposition: string | null): string | undefined {
  return contentDisposition?.match(/filename="?([^";]+)"?/)?.[1];
}

/**
 * Saves a response of the admin API as a file. It is fetched rather than linked, as a link cannot send the API key.
 */
export async function download(p: string, query?: Query): Promise<void> {
  let res: Response;
  try {
    res = await fetch(url(p, query), { credentials: "same-origin", headers: authHeaders() });
  } catch {
    throw new ApiError(0, "The admin API is not reachable.");
  }
  if (!res.ok) throw await toApiError(res);
  const link = document.createElement("a");
  link.href = URL.createObjectURL(await res.blob());
  link.download = fileName(res.headers.get("Content-Disposition")) ?? "download";
  link.click();
  URL.revokeObjectURL(link.href);
}

async function toApiError(res: Response): Promise<ApiError> {
  let title = res.statusText;
  let detail: string | undefined;
  try {
    const problem = await res.json();
    title = problem.title ?? title;
    detail = problem.detail;
  } catch {
    // not a problem document
  }
  return new ApiError(res.status, title, detail);
}

async function request<T>(method: string, p: string, query?: Query, body?: unknown, signal?: AbortSignal): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url(p, query), {
      method,
      signal,
      credentials: "same-origin",
      headers: body === undefined ? authHeaders() : { ...authHeaders(), "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "The admin API is not reachable.");
  }
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;
  const type = res.headers.get("Content-Type") ?? "";
  return (type.includes("json") ? res.json() : res.text()) as Promise<T>;
}

export const api = {
  get: <T>(p: string, query?: Query, signal?: AbortSignal) => request<T>("GET", p, query, undefined, signal),
  post: <T>(p: string, body?: unknown) => request<T>("POST", p, undefined, body),
  delete: <T>(p: string) => request<T>("DELETE", p),
};
