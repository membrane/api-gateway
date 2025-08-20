# Health-Monitored Load Balancer

When a backend becomes slow or unresponsive, users run into timeouts and errors. The health checker prevents this by probing each node at regular intervals and automatically marking unhealthy nodes as DOWN so the load balancer sends traffic only to healthy backends and returns a node to service the moment it recovers. This example demonstrates the behavior end to end and works both without TLS and with TLS.

## RUNNING THE EXAMPLE

1. **Navigate to the example directory**

   ```
   cd distribution/examples/loadbalancing/7-healthMonitor
   ```

2. **Create certificates and keystores (only when using TLS)**
    * Linux/macOS:
      ```
      ./create-certificates.sh
      ```
   * Windows:
     ```
     create-certificates.cmd
     ```

   This generates certificate files used by the balancer and both backends.

3. **Start Membrane**

   * Linux/macOS:
       ```
       ./membrane.sh                         # Without TLS
       ``` 
     ```
     ./membrane.sh -c proxies-tls.xml      # With TLS
     ```
   * Windows:

     ```
     membrane.cmd                          # Without TLS
     ```
       ```
     membrane.cmd -c proxies-tls.xml       # With TLS
     ```

4. **Verify the LB works**

   In a new terminal:

    ```
   curl localhost:8000                   # Without TLS
    ```
   ```
   curl -k https://localhost:8000        # with TLS 
   ```

   Repeat a few times. You should see responses from backend 1 and backend 2.

5. **Check Node Status**
    * [Open the Admin Console → Load Balancing → Demo Balancer → Production](http://localhost:9000/admin/clusters/show?balancer=Demo+Balancer&cluster=Production)
    * Both nodes should show **UP**.

6. **Simulate a backend issue on Node 1**

   Edit [`proxies.xml`](proxies.xml) (or [`proxies-tls.xml`](proxies-tls.xml)) and change Node 1 delay:

   ```xml
   <groovy>
     <!-- Increase to trigger a read timeout -->
     Thread.sleep(3000)
   </groovy>
   ```

   Save and **restart Membrane** (don't use hot-reload here).

7. **Observe health turning DOWN**

   With `timeout="2000"` and `soTimeout="2000"`, the health probe to Node 1 will exceed the read timeout.
   Refresh the [Admin Console](http://localhost:9000/admin/clusters/show?balancer=Demo+Balancer&cluster=Production): Node 1’s status becomes **DOWN** after the next probe cycle.
   
    Logs will show messages like:

   ```
   Connection to localhost:8001 timed out.
   Calling health endpoint failed: https://localhost:8001/health, Read timed out
   ```

8. **Recovery**

   Revert the delay to `Thread.sleep(1000)` and restart. The next successful probe marks Node 1 **UP** again.

See also:

* [balancer](https://www.membrane-api.io/docs/current/balancer.html) reference
* [balancerHealthMonitor](https://www.membrane-api.io/docs/current/balancerHealthMonitor.html) reference

