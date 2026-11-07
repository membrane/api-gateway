import type { ApiError, InstanceResult } from "../api/client";

export function ErrorBox({ error }: { error: ApiError }) {
  return (
    <div className="notice notice-error" role="alert">
      <strong>{error.title}</strong>
      {error.detail && error.detail !== error.title && <> {error.detail}</>}
    </div>
  );
}

/** Warns when some instances did not answer a list request, so the list is incomplete. */
export function PartialResult({ instances }: { instances?: InstanceResult[] }) {
  const missing = (instances ?? []).filter((i) => i.status !== "ok");
  if (missing.length === 0) return null;
  return (
    <div className="notice notice-warn" role="status">
      Incomplete: {missing.map((i) => `${i.instance} (${i.status}${i.error ? ": " + i.error : ""})`).join(", ")}
    </div>
  );
}
