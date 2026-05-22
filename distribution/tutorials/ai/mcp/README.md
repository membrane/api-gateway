# Membrane API Gateway Tutorial - MCP

This tutorial shows how to expose Membrane API Gateway as an MCP server for AI clients.
It covers:

- running a local MCP server
- inspecting recent API traffic through MCP

To begin, open [10-MCP-Server.yaml](10-MCP-Server.yaml) and follow the instructions in the file.

If you want to protect the MCP endpoint, put the `membraneMCPServer` behind an `apiKey` interceptor and expose it only through the network path you actually want clients to use. If the client should keep talking to a simple local URL, you can also place a small local proxy in front of the protected MCP endpoint and let that proxy add the required authentication details.

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
