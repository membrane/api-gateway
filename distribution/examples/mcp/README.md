# MCP Tool Blocking with Membrane

This example shows how to place a Membrane API Gateway in front of an MCP server and block a specific tool call based on the MCP request body.

The backend MCP server exposes two tools:

- `echo`
- `secretHello`

The setup is:

- backend MCP server on `http://localhost:8081/mcp`
- Membrane API Gateway on `http://localhost:2000/mcp`

The gateway forwards all MCP requests to the backend, but blocks calls to `secretHello`.

## Running the Example

1. Go to this example directory.

2. Start the backend MCP server.

   Linux / macOS:
   ```sh
   ./start-backend.sh
   ```

   Windows:
   ```bat
   start-backend.cmd
   ```

3. Start Membrane.

   Linux / macOS:
   ```sh
   ./membrane.sh
   ```

   Windows:
   ```bat
   membrane.cmd
   ```

4. Start the MCP Inspector:

   ```sh
   npx @modelcontextprotocol/inspector
   ```

5. Open the Inspector UI in your browser.

6. Create a connection to the gateway with these values:

    - **Transport:** `Streamable HTTP`
    - **URL:** `http://localhost:2000/mcp`

7. Connect.

## Testing with the Inspector

### 1. Inspect available tools

Open the **Tools** section.

You should see both backend tools:

- `echo`
- `secretHello`

This example blocks the execution of one tool at the gateway layer. It does not remove the tool from `tools/list`.

### 2. Call the allowed tool

Select the `echo` tool and use this input:

```json
{
  "text": "Hello from Inspector"
}
```

Expected result:

```json
{
  "content": [
    {
      "type": "text",
      "text": "Echo from backend: Hello from Inspector"
    }
  ],
  "isError": false
}
```

### 3. Call the blocked tool

Select the `secretHello` tool and use this input:

```json
{
  "name": "Christian"
}
```

Expected result:

```json
{
  "content": [
    {
      "type": "text",
      "text": "Blocked by Membrane gateway: tool secretHello is not allowed"
    }
  ],
  "isError": true
}
```
