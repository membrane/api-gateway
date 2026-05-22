# Membrane API Gateway Tutorial - MCP

This tutorial shows how to expose Membrane API Gateway as an MCP server for AI clients.
It covers:

- running a local MCP server
- inspecting recent API traffic through MCP
- protecting the MCP endpoint with an API key
- forwarding Claude Desktop traffic through a local proxy

To begin, open [10-MCP-Server.yaml](10-MCP-Server.yaml) and follow the instructions in the file.
Then continue with [20-MCP-Server-Protected.yaml](20-MCP-Server-Protected.yaml).

If you want to inspect API traffic from Claude Desktop, generate a few sample requests first:

Linux/macOS:

```bash
./generate-traffic.sh 20
```

Windows:

```shell
generate-traffic.cmd 20
```

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
