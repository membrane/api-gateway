---
type: feature
area: HTTP Transport and Runtime
---
- The outbound `proxy` of `httpClientConfig` takes a `noProxy` list of hosts that are called directly instead of through the proxy, with the semantics of the `NO_PROXY` environment variable: domains with their subdomains, IPv4 and IPv6 addresses and CIDR ranges, and `*` for all hosts. Host names are not resolved for the check
