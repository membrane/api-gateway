---
type: fix
area: HTTP Transport and Runtime
---
- Reading the content of a message body that was discarded, e.g. a request body the gateway did not need to answer, now fails in the same way as reading a body that was streamed. Before, it returned an empty body, or only the part read before the discard. With `memoryExchangeStore`, the admin console now reports an error for such a body instead of showing it as empty.
