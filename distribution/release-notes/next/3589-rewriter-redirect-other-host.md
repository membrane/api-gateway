---
type: security
area: Plugins
---
- `rewriter`: a redirect could send the client to another host chosen by the request (open redirect), e.g. `from: ^/old(/.*)` with `to: $1` answered `/old//evil.example/x` with `Location: //evil.example/x`. The same happened through a group inside the configured host, as in `to: https://trusted.example$1` with `/old/@evil.example/x`, and through the part of the path in front of a `from` without `^`. A redirect now goes only to the host of the request or to the scheme and host written in `to`; other requests are answered with `400`. A `to` with a group inside the host, e.g. `https://$1.example.com/`, now answers every request with `400`
