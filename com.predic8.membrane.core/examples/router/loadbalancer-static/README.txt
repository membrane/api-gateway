SIMPLE LOAD BALANCING

By using the LoadBalancerInterceptor you can balance requests to a number of different nodes.

The nodes can be organized in clusters. The clusters can be configured dynamically over a Web console or statically in
the proxies.xml file.  


RUNNING THE EXAMPLE

This example shows a proxies.xml configuration that sets up a LoadBalancerInterceptor to distribute requests to
3 different nodes. 

To run the example execute the following steps:

1. Go to the examples/loadbalancer-static directory.

2. Execute router.bat

3. Open the URL http://localhost:8080. 

4. Refresh your browser a few times. You will notice that requests will be redirected to Node 1, Node 2 and Node 3.







