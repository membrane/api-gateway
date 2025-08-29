# Load Balancer: Health Monitoring of Backend Nodes

When a backend becomes slow or unresponsive, users hit timeouts and errors. The health monitor probes each node at intervals and marks unhealthy nodes DOWN so traffic goes only to healthy backends. As soon as a node recovers, it is returned to service. 

## What You Will Run

- One Membrane router as the load balancer
- Two mock backends (backend 1 and backend 2)
- A health monitor that marks nodes UP or DOWN based on probe results

## Run the example

### Start the Load Balancer

1. Go to the example directory.

   ```bash
   cd distribution/examples/loadbalancing/6-health-monitor
   ```

2. Start Membrane.

   - macOS/Linux: `./membrane.sh`
   - Windows: `membrane.cmd`

3. Verify the load balancer.

   ```bash
   curl localhost:8000                   
   ```

   Repeat a few times. You should see responses alternating between backend 1 and backend 2.

4. Check node status in the Admin Console.

   - Open:
     [Load Balancing → Default → Production](http://localhost:9000/admin/clusters/show?balancer=Default&cluster=Production)
   - Both nodes should show **UP**.


### Simulate A Backend Issue

1. Make backend 2 return 500.

   * Edit `proxies.xml` and change the status code of backend 2 from 200 to 500:

     ```xml
     <return statusCode="500"/>
     ```
   * Save and **restart Membrane**.

2. Observe health turning **DOWN**.

   * Refresh the Admin Console: backend 2 becomes **DOWN** after the next probe cycle.
   * Example log lines:

     ```
     Node localhost:8002 health check failed with HTTP 500 status code
     ```

   * Send a few requests again to the balancer. All responses should now come from backend 1.

3. Change the status code back to 200 and observe recovery.


### Simulate A Slow Backend

1. Make backend 1 slow to trigger a timeout.

   * Edit `proxies.xml` and increase the delay for backend 1:

     ```xml
     <groovy>
       Thread.sleep(3000)
     </groovy>
     ```
   * Save and **restart Membrane**.

2. Observe health turning **DOWN**.

   * With `timeout="2000"` and `soTimeout="2000"`, the health probe exceeds the read timeout.
   * Refresh the Admin Console: backend 1 becomes **DOWN** after the next probe cycle.
   * Send a few requests again to the balancer. All responses should now come from backend 2.


## Tips

* Quick loop to watch distribution:

  ```bash
  for i in {1..10}; do curl -s localhost:8000; echo; done
  ```

* [balancer](https://www.membrane-api.io/docs/current/balancer.html) reference
* [balancerHealthMonitor](https://www.membrane-api.io/docs/current/balancerHealthMonitor.html) reference