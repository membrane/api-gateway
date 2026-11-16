---
type: improvement
area: HTTP Transport and Runtime
---
- The default `backlog` of the `transport` is now 500 instead of 50. A burst of new connections no longer overflows the accept queue of a listening port, where the operating system would drop or refuse connections. The operating system may cap the value, e.g. at 200 on Windows. Set `backlog` to restore the old value.
