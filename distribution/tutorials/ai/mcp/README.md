# Membrane MCP Server Example

Start one of the example configs:

- `10-MCP-Server.yaml` exposes the MCP server directly.
- `20-MCP-Server-Protected.yaml` protects the MCP server with an API key and uses a local proxy for Claude Desktop.

Generate some traffic first:

```bash
./generate-traffic.sh 20
```
On Windows:

```shell
generate-traffic.cmd 20
```

# Setup MCP for Claude Desktop

1. Open Claude Desktop
2. Open `Settings` -> `Developer`
3. Under `Local MCP servers` click on `Edit Config`
4. Open `claude_desktop_config.json` and paste the following:

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

