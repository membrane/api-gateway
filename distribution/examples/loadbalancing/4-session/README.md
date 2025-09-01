### LOAD BALANCING WITH STICKY SESSIONS

The `LoadBalancerInterceptor` can be configured to look for session ids in requests and responses. When the `LoadBalancerInterceptor` finds a new session id, the balancer will associate the session with the node that has received or sent the message. If the session id is detected again in another message the message will be forwarded to the associated node.

#### RUNNING THE EXAMPLE

In this example we will set up a load balancer with three nodes. The communication between the node and client will be session based.

To run the example execute the following steps:

1. Go to the `examples/loadbalancer/4-xml-session` directory.

2. Execute

   `membrane.sh` (Linux) or `membrane.cmd` (Windows)

   This will initialize the `LoadBalancingInterceptor` and associate it with a rule. In addition, three nodes are started up which are configured as targets for load balancing.

3. Go to the command line and run the following cURL command (install cURL if missing):
```sh
   curl -X POST http://localhost:8080 \
    -H "Content-Type: application/json" \
    -d '{"id":"1"}'
```
4. Observe sticky session behavior: the server response includes an increasing count for that session. Repeating the same request with the same id keeps incrementing the count on the same backend node. If you change the id (e.g., to "2"), the balancer routes you to another node. You will then stay on that new node and its count increases there until you change the id again.

---
See:
- balancer reference: https://www.membrane-soa.org/api-gateway-doc/current/configuration/reference/balancer.htm