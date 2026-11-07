import { useState } from "react";
import { getApiKey, setApiKey, type ApiError, type InstanceResult } from "../api/client";

/** Shows an error. If the admin API requires a key (401), asks for it instead. */
export function ErrorBox({ error }: { error: ApiError }) {
  if (error.status === 401) return <ApiKeyForm />;
  return (
    <div className="notice notice-error" role="alert">
      <strong>{error.title}</strong>
      {error.detail && error.detail !== error.title && <> {error.detail}</>}
    </div>
  );
}

/** Asks for the admin API key, keeps it for this browser tab and reloads the console with it. */
function ApiKeyForm() {
  const [key, setKey] = useState("");
  const rejected = getApiKey() !== null;
  return (
    <form
      className="notice notice-warn"
      role="alert"
      onSubmit={(e) => {
        e.preventDefault();
        setApiKey(key.trim());
        window.location.reload();
      }}
    >
      <strong>{rejected ? "The API key was not accepted." : "This admin API requires an API key."}</strong>{" "}
      <label>
        API key <input type="password" autoComplete="off" value={key} onChange={(e) => setKey(e.target.value)} required />
      </label>{" "}
      <button type="submit">Use key</button>
    </form>
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
