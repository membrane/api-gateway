---
type: fix
area: Configuration
---
- A `path` that contains duplicate slashes or path parameters (`;...`) matches requests again. Since 7.7.1, duplicate slashes in the request path are merged and path parameters are removed before the `path` of an API is compared, but the configured `path` was compared as written. A `path` like `/eu/ws//resource` therefore matched no request, not even `/eu/ws//resource`. The configured `path` is now compared in the same form, so `/eu/ws//resource` matches `/eu/ws//resource` and also `/eu/ws/resource`. If two APIs differ only in duplicate slashes or path parameters, the first one receives the requests of both, and a warning is logged. The request is still forwarded unchanged. A `path` with `isRegExp: true` is compared as written.
