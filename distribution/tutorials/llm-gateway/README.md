# Membrane API Gateway Tutorial - LLM Gateway

This tutorial shows how to put Membrane in front of an LLM provider's chat API. The `llmGateway`
interceptor can share a single provider API key among many clients, enforce usage policies (token
limits, an allow-list of models), and issue per-user API keys with their own token quotas.

The same two steps are available for three providers - pick the one you have a key for:

- [openai](openai) - OpenAI
- [claude](claude) - Anthropic Claude
- [google](google) - Google Gemini

Each provider directory has the same lesson chain:

1. `10-Basic-LLM-Gateway.yaml` - forward requests to the provider with one shared API key, and
   limit input and output tokens per request.
2. `20-Sharing-API-Keys.yaml` - give each client its own API key and token quota with
   `simpleStore`, and restrict which models clients may request.

To begin, open your provider's `10-Basic-LLM-Gateway.yaml` - for example
[openai/10-Basic-LLM-Gateway.yaml](openai/10-Basic-LLM-Gateway.yaml) - and follow the instructions
in the file. You will need an API key from the provider.
