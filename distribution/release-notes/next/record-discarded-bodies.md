---
type: fix
area: HTTP Transport and Runtime
---
- Exchange stores now record the body of a request that the gateway answered without reading it, e.g. with `template` or `return`. Before, such bodies were recorded as empty.
