/** The fields of a load balancer node that the helpers below need. */
export interface NodeLike {
  host?: string;
  port?: number;
  status?: string;
}

/** The node's ID in API paths: host:port. */
export function nodeId(node: NodeLike): string {
  return `${node.host}:${node.port}`;
}

/** Summarizes the states of a cluster's nodes, e.g. "2 up / 1 down / 1 take out", or "no nodes". */
export function health(nodes: NodeLike[] | undefined): string {
  if (!nodes || nodes.length === 0) return "no nodes";
  const up = nodes.filter((n) => n.status === "UP").length;
  const takeout = nodes.filter((n) => n.status === "TAKEOUT").length;
  const parts = [`${up} up`, `${nodes.length - up - takeout} down`];
  if (takeout > 0) parts.push(`${takeout} take out`);
  return parts.join(" / ");
}

/** Formats an error rate between 0 and 1 as a percentage with one decimal, e.g. 0.125 -> "12.5 %". */
export function percent(rate: number | null | undefined): string {
  if (rate === null || rate === undefined || isNaN(rate)) return "";
  return `${(rate * 100).toFixed(1)} %`;
}

/** The label of a node state, as the old console showed it. */
export function statusLabel(status: string | undefined): string {
  return status === "TAKEOUT" ? "take out" : (status ?? "").toLowerCase();
}
