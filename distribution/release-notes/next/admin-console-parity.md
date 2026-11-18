---
type: feature
area: Admin Console
---
- `adminConsole`, new UI under `/console/`: pages for load balancers (clusters and nodes; add clusters and nodes, remove nodes, set nodes up, down or take out, reset their counters; a node's calls by status code and its sessions), statistics per API (also per status code) and per client, and active stream pumps. Clients and APIs link to their calls. Actions the caller may not perform are not shown: changing node states needs the `nodeOperator` role, adding and removing needs write access
