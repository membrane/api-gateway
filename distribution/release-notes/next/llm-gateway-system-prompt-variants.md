---
type: fix
area: Plugins
---
- `llmGateway`: `systemPrompt` with `OVERWRITE` or `REMOVE` also removes `system` and `developer` messages from `input` in OpenAI Responses API requests.
- `llmGateway`: Gemini requests that use the proto field names `system_instruction`, `generation_config` or `max_output_tokens` get the same `systemPrompt` and `maxOutputTokens` handling as those with `systemInstruction`, `generationConfig` and `maxOutputTokens`. A request that sends both names of one field is answered with `400`.
