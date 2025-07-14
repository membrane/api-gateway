# Configuring with Properties and Environment Variables

Membrane is built on Spring, so you can use standard Spring mechanisms like properties and environment variables for configuration. This example shows how to configure routes dynamically using SpEL (Spring Expression Language).

## How to Run This Example

1. **Navigate to the example directory**

   ```bash
   cd examples/extending-membrane/configuration-properties/
   ```

2. **(Optional) Set the `TARGET` environment variable**

    * **Linux/macOS**

      ```bash
      export TARGET=https://www.membrane-api.io/
      ```
    * **Windows**

      ```cmd
      set TARGET=https://www.membrane-api.io/
      ```

3. **Start Membrane**

   ```bash
   ./membrane.sh
   ```

4. **Send test requests**

   ```bash
   curl http://localhost:2000
   curl http://localhost:2001
   ```

## How It Works

In `proxies.xml`, two APIs are configured:

* Port **2000** uses static values from a `<util:properties>` block:

  ```xml
  <target host="#{my.HOST}" port="#{my.PORT}" />
  ```

* Port **2001** uses an environment variable or a fallback default:

  ```xml
  <target url="#{systemEnvironment['TARGET'] ?: 'https://api.predic8.de'}" />
  ```

### What Is `#{...}`?

The `#{...}` syntax is **Spring Expression Language (SpEL)**. It allows you to inject values into the configuration at runtime:

* `#{my.HOST}` gets a property from the `util:properties` block.
* `#{systemEnvironment['TARGET']}` accesses the `TARGET` environment variable.
* `?:` provides a fallback if the environment variable is not set.

To enable access to environment variables, the following element must be present in `proxies.xml`:

```xml
<context:property-placeholder />
```

## Troubleshooting

If `${systemEnvironment['TARGET']}` is not resolved:

* Ensure the environment variable is set **before** running Membrane.
* Confirm with `echo $TARGET` (Linux/macOS) or `echo %TARGET%` (Windows).
* Check Membrane’s logs for output like:

  ```
  Target: https://www.membrane-api.io/
  ```