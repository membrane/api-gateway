# Membrane API Gateway Tutorial - MCP

This tutorial shows how to expose Membrane API Gateway as an MCP server for AI clients.
It covers:

- running a local MCP server
- inspecting recent API traffic through MCP
- protecting the MCP endpoint with an API key
- forwarding Claude Desktop traffic through a local proxy

## Claude Desktop Setup

Open `Settings` -> `Developer`, edit `claude_desktop_config.json`, and add:

```json
{
  "mcpServers": {
    "membrane": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:2000"
      ]
    }
  },
  "preferences": {
    "coworkScheduledTasksEnabled": true,
    "ccdScheduledTasksEnabled": true,
    "sidebarMode": "task",
    "coworkWebSearchEnabled": true
  }
}
```

Start membrane and restart Claude Desktop.