/**
 * Plugin descriptions can contain HTML markup (e.g. <br/>, <b>). They are shown as plain text, never as HTML.
 */
export function plainText(html: string | null | undefined): string {
  if (!html) return "";
  return html
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ")
    .trim();
}

/** A link target only for documentation on the web, so a value from the API cannot become a javascript: link. */
export function docLink(url: string | null | undefined): string | undefined {
  return url && /^https:\/\//i.test(url) ? url : undefined;
}

const FLOW_LABELS: Record<string, string> = {
  request: "request only",
  response: "response only",
  abort: "abort only",
};

/** A badge for plugins that do not run in both directions; null for both or unknown. */
export function flowLabel(flow: string | null | undefined): string | null {
  return (flow && FLOW_LABELS[flow]) ?? null;
}

/** Counts the plugins of a flow, including nested ones, but not the structural else/case/otherwise nodes. */
export function countPlugins(nodes: { name: string; children?: unknown[] | null }[] | null | undefined): number {
  let n = 0;
  for (const node of nodes ?? []) {
    if (!["else", "case", "otherwise"].includes(node.name)) n++;
    n += countPlugins(node.children as { name: string; children?: unknown[] | null }[] | undefined);
  }
  return n;
}
