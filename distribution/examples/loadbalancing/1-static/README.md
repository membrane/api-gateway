# Static API Loadbalancer - Example

Membrane can work as a software loadbalancer and distribute requests to a number of backend nodes organized in a clusters.

This sample shows a simple static configuration of nodes in the `proxies.xml` file. See the [overview](..) for more dynamic setups.



## Get Started

This example sets up a loadbalancer to distribute requests to 3 different nodes. 

Execute the following steps:

1. Go to the `examples/loadbalancing/1-static` directory.
2. Run `service-proxy.sh` or `service-proxy.bat` in a terminal.
3. Open the URL:

```
http://localhost:8080 
```

5. Refresh the browser a few times. 

You will notice that requests will be routed to node 1, 2 and 3.

---
See:
- [balancer](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/balancer.htm) reference




