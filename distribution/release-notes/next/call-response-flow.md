---
type: breaking
area: Plugins
---
- `call` in the response flow works on the response: the URL is evaluated against the response, the response's header fields are sent, `POST`, `PUT` and `PATCH` send the response body, and the endpoint's body and header fields are merged into the response. The status code of the response is kept. Before, `call` in the response flow worked on the request and left the response unchanged.
- A `call` directly under `flow` runs in both flows. Next to a `target`, it now calls the endpoint again after the target and replaces the target's response with the endpoint's. Put it under `request` to call the endpoint only before the target.
