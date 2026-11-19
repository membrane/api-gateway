---
type: feature
area: Admin Console
---
- `adminConsole`, new UI under `/console/`: pages for load balancers (clusters and nodes; add clusters and nodes, remove nodes, set nodes up, down or take out, reset their counters; a node's calls by status code and its sessions), statistics per API (also per status code) and per client, and active stream pumps. Clients and APIs link to their calls. Actions the caller may not perform are not shown: changing node states needs the `nodeOperator` role, adding and removing needs write access
- `adminConsole`, new UI under `/console/`: a page per API with its matching rules (port, host, method, path, `test`), target, source file, statistics per status code and its flow as a tree, including nested `request`/`response` blocks and the branches of `if` and `choose`, each plugin linked to its reference on membrane-api.io. Inactive APIs can be started again and APIs removed, given write access. A separate page shows the transport flow of each instance
