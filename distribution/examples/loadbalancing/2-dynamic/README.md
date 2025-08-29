# Dynamic API Load Balancer - Example

Membrane API Gateway can balance requests across backend nodes that are added and removed dynamically at runtime.

Nodes can be organized in clusters. You can manage them either through the web-based administration console or via a simple URL-based API.


## What You Will Run

- One Membrane router as the load balancer
- Three sample web apps simulating backend nodes (ports 4000, 4001, 4002)
- A web-based admin console for cluster and node management
- An HTTP API for programmatic node control


## Run the Example

1. Go to the `examples/loadbalancing/2-dynamic` directory.

   ```bash
   cd examples/loadbalancing/2-dynamic
   ```

2. Start Membrane:

   - macOS/Linux: `./membrane.sh`
   - Windows: `membrane.cmd`

3. Open in a browser:

   ```
   http://localhost:4000
   ```

   You will see a simple counter app. Identical apps are also available on ports `4001` and `4002`.

4. Open the **Admin Console**:

   ```
   http://localhost:9000/admin
   ```

5. Go to the **Load Balancing** tab.

   - Click the entry `Default`.
   - Enter `Default` as the cluster name.
   - Click **Add Cluster**.

6. Add the first node:

   - Host: `localhost`
   - Port: `4000`
   - Click **Add Node**.

7. Open in a browser:

   ```
   http://localhost:8080
   ```

   Refresh a few times. All requests go to **node 4000**.

8. Add a second node (`localhost:4001`) using the same steps.

9. Open:

   ```
   http://localhost:8080
   ```

   Now requests are distributed between **node 4000** and **node 4001**.

10. Add a third node (`localhost:4002`) to see full load balancing across three backends.



## Managing Nodes with an API

Nodes can also be controlled programmatically using the Cluster Manager API. For example, to temporarily disable node `localhost:4001`:

```bash
curl "http://localhost:9010/clustermanager/down?balancer=default&host=localhost&port=4001"
```

Re-enable the node with `.../up?balancer=...`.


## Notes

* The `adminConsole` and `clusterNotification` interceptors in `proxies.xml` are optional.
  You can remove them if you only want programmatic control.
* This example defaults to round-robin balancing.


---
See:
- [proxies.xml](proxies.xml)
- [balancer](https://www.membrane-api.io/docs/current/balancer.html) reference
