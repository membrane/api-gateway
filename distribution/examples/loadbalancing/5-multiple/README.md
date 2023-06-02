### MULTIPLE LOAD BALANCERS

This example shows how to use multiple load balancers (LBs) within the same Membrane Service Proxy instance, and how to statically
configure your nodes within. You should be familiar with [1-static](../1-static) and [2-dynamic](../2-dynamic).

The LBs are distinguished by giving them different names ("balancer1" and "balancer2" in the example).

When using the URL based interface to create/remove/... nodes, simply append `"&balancer="+name` to the URL.


(In the previous examples -basic-1 and -static, an implicit name called "Default" was given to the only existing LB.)


#### RUNNING THE EXAMPLE

In this example we will use two `LoadBalancerInterceptors` to distribute requests to different nodes. 

The first LB is balancing between our first two counters.

The second LB is balancing between our third and fourth counter.


To run the example execute the following steps:

1. Go to the `examples/loadbalancer-multiple-4` directory.

2. Execute `service-proxy.bat`

3. Open the URL http://localhost:8080/service in your browser and repeatedly refresh (F5). Observe that the response alternates
   between Mock Node 1 and 2.
   
4. Open the URL http://localhost:8081/service and refresh several times. Observe that you now alternate between Mock Node 3
   and Mock Node 4.

5. Open the URL http://localhost:9000/admin/

5. Click on the "Load Balancing" tab.

6. Click on the link called "balancer1", the click on "Default".

7. Nodes are identified by host name and port. Note that both nodes have status "UP".

8. Open http://localhost:9010/clustermanager/down?balancer=balancer1&host=localhost&port=4001 in a different browser
   tab. (No content will appear.) This sets the status of node 2 ( the one on port 4001 ) to "DOWN" and effectively disables it ( in balancer 1 ).
   
9. Then go back to the admin interface and refresh. Note that the second node now has status "DOWN".

10. Open again the URL http://localhost:8080/service and repeatedly refresh. Note that you now stay on Mock Node 1.


#### NOTE:

The names of <balancer> and <cluster> elements have to be simple (e.g. no spaces or special characters)
for the web administration interface and LB client (see load-balancer-client-2) to work. 

---
See:
- [balancer](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/balancer.htm) reference
- [cluster](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/cluster.htm) reference