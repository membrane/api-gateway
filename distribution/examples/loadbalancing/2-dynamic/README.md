# Dynamic API Loadbalancing - Example

Membrane API Gateway can balance requests to a number of different nodes. The nodes can be added and removed dynamically at runtime.

The nodes can be organized in clusters. A Web based administration interface allows you to create and remove nodes and
clusters. In addition, a simple URL based interface can be used to add clusters and nodes. 


## Get Started

In this example we will distribute requests to 3 different nodes. 

To run the example execute the following steps:

1. Go to the `examples/loadbalancing/2-dynamic` directory.
2. Execute `service-proxy.bat` or `service-proxy.sh`
3. Open the URL http://localhost:4000 in your browser. 
   You will see a simple web app that counts how often it was called. There are 2 more web apps of the same kind
   listening at port `4001` and `4002`.
4. Open the URL:

```
http://localhost:9000/admin
```

5. Click on the `Load Balancing` tab.
6. Click on the list entry `Default`. On the next page, set `Default` as name and click on `add Cluster` just below the list.
7. Nodes are identified by host name and port. Fill in the form with `localhost` as host and `4000` as port and
   press `Add Node`.
8. Open the URL:

```
http://localhost:8080
```

9. Refresh your browser a few times. The request are all going to the same node.
10. Now add another node with host name `localhost` and port `4001` as described in step 5 till 7.
11. Open `http://localhost:8080`again. Now the requests will be distributed between to node 1 and node 2.

## Managing Nodes with an API

Nodes can also be registered, turned off and on using a simple API.

```
http://localhost:9010/clustermanager/down?balancer=default&host=localhost&port=4001
```

You find more balancer configurations at the [Loadbalancing](..) page.

Note that the `adminConsole` and the `clusterNotification` are optional, you can take them out of the `proxies.xml` if you want to.