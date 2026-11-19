import type { Schemas } from "../api/client";
import { docLink, flowLabel, isClassName, plainText } from "../util/flow";

export type FlowNode = Schemas["FlowNode"];

const BRANCHES = ["else", "case", "otherwise"];

/** The plugins of a flow in the order they run, with nested flows (request, response, if, choose) indented. */
export function FlowTree({ nodes, parentFlow }: { nodes: FlowNode[]; parentFlow?: string | null }) {
  if (nodes.length === 0) return <p className="muted">No plugins.</p>;
  return (
    <ol className="flow">
      {nodes.map((n, i) => <FlowItem key={i} node={n} parentFlow={parentFlow} />)}
    </ol>
  );
}

function FlowItem({ node, parentFlow }: { node: FlowNode; parentFlow?: string | null }) {
  const children = node.children ?? [];
  const branch = BRANCHES.includes(node.name);
  const doc = docLink(node.docUrl);
  // Within a request or response block, its direction is not repeated on every plugin.
  const label = node.flow === parentFlow ? null : flowLabel(node.flow);
  // A container's description and name only summarize the nested plugins shown below it.
  const description = children.length > 0 ? "" : plainText(node.shortDescription);
  const displayName = children.length > 0 ? null : displayNameOf(node);
  return (
    <li className={branch ? "flow-node flow-branch" : "flow-node"}>
      <div className="flow-head">
        {doc ? <a className="flow-name" href={doc} target="_blank" rel="noreferrer noopener">{node.name}</a> : <span className="flow-name">{node.name}</span>}
        {displayName && <span className="muted">{displayName}</span>}
        {node.condition && <code className="flow-condition">{node.condition}</code>}
        {label && <span className="badge">{label}</span>}
      </div>
      {description && <div className="flow-desc muted" title={plainText(node.longDescription) || undefined}>{description}</div>}
      {children.length > 0 && <FlowTree nodes={children} parentFlow={node.flow === "both" ? parentFlow : node.flow} />}
    </li>
  );
}

function displayNameOf(node: FlowNode): string | null {
  const name = node.displayName;
  // Plugins without a display name report their Java class name, which says nothing more than the element name.
  if (!name || name === node.name || isClassName(name)) return null;
  return name;
}
