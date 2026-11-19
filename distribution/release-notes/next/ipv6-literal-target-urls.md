---
type: fix
area: HTTP Transport and Runtime
---
- A target URL with an IPv6 literal host, e.g. `http://[2001:db8::1]:8080/`, is now called correctly. Before, the host was cut off at its first colon
