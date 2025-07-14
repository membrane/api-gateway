# Configuration with Properties and Environment Variables

Membrane is built on Spring, so you can use standard Spring mechanisms like properties and environment variables for configuration.

## How to Run This Example

1. Navigate to the example directory:

   ```bash
   cd examples/extending-membrane/configuration-properties/
   ```

2. (Optional) Set the TARGET environment variable:

   **Linux:**
   ```bash
   export TARGET=https://www.membrane-api.io/
   ```
   
   **Windows:**
   ```cmd
   set TARGET=https://www.membrane-api.io/
   ```

3. Start Membrane:

   ```bash
   ./membrane.sh
   ```

4. Send test requests:

    ```
    curl http://localhost:2000
    curl http://localhost:2001
    ```

## How It Works

In `proxies.xml`, two APIs are configured:

- Port 2000: Uses static properties HOST and PORT (from <util:properties>).
- Port 2001: Uses an environment variable TARGET. If it's not set, defaults to https://api.predic8.de.

To enable use of environment variables like #{systemEnvironment['TARGET']}, the following must be included:

```xml
<context:property-placeholder />
```

## Troubleshooting

If `${systemEnvironment['TARGET']}` is not resolved:

- Ensure the environment variable is set **before** starting Membrane.
- Use `echo $TARGET` to verify the variable is available in your shell.
- Look for log output like:

  ```
  Target: https://www.membrane-api.io/
  ```
