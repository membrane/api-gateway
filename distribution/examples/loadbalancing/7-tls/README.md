# TLS Health-Monitored Load Balancer

This example shows how to run a TLS-terminating load balancer with an active health monitor. It also demonstrates how a slow backend causes the node to be marked **DOWN** automatically.

## RUNNING THE EXAMPLE

1. **Navigate to the example directory**

   ```
   cd distribution/examples/loadbalancing/7-tls
   ```

2. **Create certificates and keystores**

   ```
   ./create-certificates.sh
   ```

   This generates PKCS#12 files used by the balancer and both backends.

3. **Start Membrane**

    * Linux/macOS:

      ```
      ./membrane.sh
      ```
    * Windows:

      ```
      membrane.cmd
      ```

4. **Verify the LB works**
   In a new terminal:

   ```
   curl -k https://localhost:8000
   ```

   Refresh a few times; you should see responses from backend 1 and backend 2.

5. **Check Node Status**
    * [Open the Admin Console → Load Balancing → Demo Balancer → Production](http://localhost:9000/admin/clusters/show?balancer=Demo+Balancer&cluster=Production)
    * Both nodes should show **UP**.

6. **Simulate a backend issue on Node 1**

   In [`proxies.xml`](proxies.xml), increase the delay on Node 1 to 3000 (Line 58):

   ```xml
   <groovy>
     <!-- Increase to trigger a read timeout -->
     Thread.sleep(3000)
   </groovy>
   ```

   Save and **restart Membrane** (don't use hot-reload in this case).

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

