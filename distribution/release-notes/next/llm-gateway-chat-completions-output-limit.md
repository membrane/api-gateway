---
type: fix
area: Plugins
---
- `llmGateway`: `maxOutputTokens` limits both `max_tokens` and `max_completion_tokens` of Chat Completions requests. Before, only one of them was lowered, and a larger value in the other one was passed on.
