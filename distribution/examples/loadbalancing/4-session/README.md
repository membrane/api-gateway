# Load balancing with sticky sessions

The `LoadBalancerInterceptor` can be configured to detect session IDs in both requests and responses. When it encounters a new session ID, it binds that session to the node that handled the message. If the same session ID appears in a subsequent message, the interceptor forwards that message to the previously associated node.

## Running the example

In this example we will set up a load balancer with three nodes. The communication between the node and client will be session based.

To run the example execute the following steps:

1. `cd` into the `distribution/examples/loadbalancing/4-session` directory.

2. Start Membrane.

  - macOS/Linux: `./membrane.sh`
  - Windows: `membrane.cmd`

3. Go to the command line and run one of the following cURL commands (install cURL if missing):

```sh
curl -X POST http://localhost:8080 \
-H "Content-Type: application/json" \
-d '{"id":"1"}'
```

4. Observe sticky session behavior: the server response includes an increasing count for that session. Repeating the same request with the same id keeps incrementing the count on the same backend node. If you change the id (e.g., to "2"), the balancer routes you to another node. You will then stay on that new node and its count increases there until you change the id again.

---
See:
- balancer reference: https://www.membrane-soa.org/api-gateway-doc/current/configuration/reference/balancer.htm