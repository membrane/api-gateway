---
type: breaking
area: Plugins
---
- `rateLimiter`: `trustedProxyList` now has to contain the address of the proxy that connects to Membrane as well. The client address is the first address that is not in the list, searched from that proxy backwards through `X-Forwarded-For`. Requests from other addresses are counted by their own address and their `X-Forwarded-For` is ignored. Before, the list was compared with the end of `X-Forwarded-For` without the connecting proxy, so a list with a single load balancer never took effect. See the migration guide.
