# Simple Static API Load Balancer

Membrane can serve as an software load balancer for API and distribute requests across multiple backend nodes.

This sample shows a simple static configuration. For health checks, sticky sessions, or dynamic discovery, see the other examples.

## What You Will Run

- One Membrane router as the load balancer
- Three mock services that simulate backend nodes
- Round-robin request distribution

## Run the Example

1. Go to the `examples/loadbalancing/1-static` directory.
   ```bash
   cd examples/loadbalancing/1-static
   ```
2. Start Membrane:
   - macOS/Linux: ./membrane.sh
   - Windows: membrane.cmd
3. Open in a browser:
   ```
   http://localhost:8080 
   ```
4. Refresh the browser a few times. 

By refreshing the browser or resending the request you should see responses alternating between node 1, node 2, and node 3.


---
See:
- [proxies.xml](proxies.xml)
- [balancer](https://www.membrane-api.io/docs/current/balancer.html) reference




