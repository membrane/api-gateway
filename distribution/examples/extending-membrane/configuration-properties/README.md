# Configuration with Spring Expressions and Environment Variables

This example demonstrates how to configure the Membrane API Gateway using Spring Expression Language (SpEL) and environment variables.

For reference, see the Spring Expression Language documentation:  
http://docs.spring.io/spring/docs/3.1.x/spring-framework-reference/html/expressions.html

## Running the Example

1. Navigate to the example directory:

   ```bash
   cd examples/extending-membrane/configuration-properties/
   ```

2. (Optional) Set the `TARGET` environment variable:

   ```bash
   export TARGET=https://www.membrane-api.io/
   ```

3. Start Membrane:

   ```bash
   ./membrane.sh
   ```

4. Send a request to either port:

    - Port 2000 forwards to `membrane-api.io:80`
    - Port 2001 forwards to the value of `$TARGET`, or defaults to `https://api.predic8.de`

## Important

The following line is required to access environment variables via `#{systemEnvironment[...]}` inside the configuration:

```xml
<context:property-placeholder />
```

Without this, environment variables will not be accessible and Spring expressions using `systemEnvironment` will fail or remain unresolved.

## Troubleshooting

If `${systemEnvironment['TARGET']}` is not resolved correctly:

- Ensure the environment variable is set **before** starting Membrane.
- Use `echo $TARGET` to verify the variable is available in your shell.
- Check the Membrane logs for output like:

  ```
  Target: https://www.membrane-api.io/
  ```
