# Internal Proxy

Route requests to internal proxies for reusable functionality across multiple APIs and content-based routing.

## Running the Example

***Note:*** *You can test these requests using the provided HTTP file or cURL snippets.*

1. **Navigate** to the `examples/routing-traffic/internalproxy` directory.
2. **Start** Membrane by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
3. **Execute the following requests** (alternatively, use the `requests.http` file):
- **Normal Processing**:
  ```bash
  curl http://localhost:2000
  ```
  Response: `Normal processing!`


- **Express Processing**:
  ```bash
  curl -X POST -d @express.xml http://localhost:2000
  ```
  Response: `Express processing!`

## How it works

The main API endpoint configuration uses a `<switch>` element to change the url of `<target>` conditionally.
In this instance, we use internal proxies to encapsulate our plugins in separate routines,
this makes them reusable and cleans up our APIs:

```xml
<api port="2000">
  <request>
    <if test="//order[@express='yes']" language="xpath">
      <destination url="internal://express"/>
    </if>
  </request>
  <target url="internal://normal" />
</api>
```

***Note:*** InternalProxies can only be called from other proxies and APIs, not from external sources.

Two internal proxies handle different processing paths:

```xml
<internalProxy name="express">
    <static>Express processing!</static>
    <return/>
</internalProxy>

<internalProxy name="normal">
    <static>Normal processing!</static>
    <return/>
</internalProxy>
```
