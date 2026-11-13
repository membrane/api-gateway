---
type: breaking
area: Plugins
---
- `redis` with `useSentinels: true` now honors `ssl: true`: the connections to the Sentinels and to the master use TLS. Before, `ssl` was ignored and both were plain text. A setup with `ssl: true` whose Sentinels or master don't speak TLS now fails to connect; remove `ssl: true` there. The master's certificate must name the address the Sentinels announce for it, which is an IP address unless Sentinel is configured with `sentinel resolve-hostnames yes` and `sentinel announce-hostnames yes`. (#3781)
