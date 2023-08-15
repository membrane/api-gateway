# Loadbalancing APIs

Membrans loadbalancer supports a variety of setups from a simple static configuration up to dynamically managed nodes in the cloud.

## Examples

Each sample add a new aspect to the previous one. You can start with sample 1 and go on from there.

|Sample|Description|
|---|---|
|[Static](1-static)|Simple setup using a static configuration of backend nodes.|
|[Dynamic with UI]|Managing nodes of the cluster in the Web console.|
|[Dynamic with client]()|Managing nodes using the commandline|
|[XML Session](4-xml-session)|Sticky session with XML and Web Services|
|[Multiple](5-multiple)|Run multiple loadbalancers in one Membrane installation|

|[loadbalancer-basic-1](https://github.com/membrane/service-proxy/tree/master/distribution/examples/loadbalancer-basic-1)|By using the `LoadBalancerInterceptor` you can balance requests to a number of different nodes.|
|[loadbalancer-client-2](https://github.com/membrane/service-proxy/tree/master/distribution/examples/loadbalancer-client-2)|In the previous example we set up a load balancer with 3 nodes. We used a URL based interface to register the nodes with the balancer. That interface can be called from a simple client that supports encrypted parameters.|
|[loadbalancer-multiple-4](https://github.com/membrane/service-proxy/tree/master/distribution/examples/loadbalancer-multiple-4)|This example shows how to use multiple load balancers (LBs) within the same Membrane API Gateway instance, and how to statically configure your nodes within. You should be familiar with loadbalancer-basic-1 and loadbalancer-static.|
|[loadbalancer-session-3](https://github.com/membrane/service-proxy/tree/master/distribution/examples/loadbalancer-session-3)|The `LoadBalancerInterceptor` can be configured to look for session ids in requests and responses. When the `LoadBalancerInterceptor` finds a new session id, the balancer will associate the session with the node that has received or sent the message. If the session id is detected again in another message the message will be forwarded to the associated node.|
