import type { Schemas } from "../api/client";
import { docLink, flowLabel, plainText } from "../util/flow";

export type FlowNode = Schemas["FlowNode"];

const BRANCHES = ["else", "case", "otherwise"];

/** The plugins of a flow in the order they run, with nested flows (request, response, if, choose) indented. */
export function FlowTree({ nodes }: { nodes: FlowNode[] }) {
  if (nodes.length === 0) return <p className="muted">No plugins.</p>;
  return (
    <ol className="flow">
      {nodes.map((n, i) => <FlowItem key={i} node={n} />)}
    </ol>
  );
}

function FlowItem({ node }: { node: FlowNode }) {
  const children = node.children ?? [];
  const branch = BRANCHES.includes(node.name);
  const doc = docLink(node.docUrl);
  const label = flowLabel(node.flow);
  const description = plainText(node.shortDescription);
  return (
    <li className={branch ? "flow-node flow-branch" : "flow-node"}>
      <div className="flow-head">
        {doc ? <a className="flow-name" href={doc} target="_blank" rel="noreferrer noopener">{node.name}</a> : <span className="flow-name">{node.name}</span>}
        {node.displayName && node.displayName !== node.name && <span className="muted">{node.displayName}</span>}
        {node.condition && <code className="flow-condition">{node.condition}</code>}
        {label && <span className="badge">{label}</span>}
      </div>
      {description && <div className="flow-desc muted" title={plainText(node.longDescription) || undefined}>{description}</div>}
      {children.length > 0 && <FlowTree nodes={children} />}
    </li>
  );
}
