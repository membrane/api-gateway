---
type: fix
area: HTTP Transport and Runtime
---
- Outgoing TLS connections to an IP address no longer send the address as SNI server name, which RFC 6066 forbids. For an IPv6 address this made the connection fail. A server name set explicitly is still sent
