LOAD BALANCING INTERCEPTOR WITH SESSIONS

Using the LoadBalancerInterceptor you can balance requests to a number of different nodes.

The nodes can be organized in clusters. A Web based administration interface allows you to create and remove nodes and clusters. In addition a simple URL based interface can be used to add clusters and nodes. 

The LoadBalancerInterceptor can be configured to look for session ids in requests and responses. When the LoadBalancerInterceptor finds a new session id it will be associated with the the node that has received or send the message. If the session id is detected again in another message the message will be redirected to the associated node.
 
 
RUNNING THE EXAMPLE

In this example we will set up a load balancer, two nodes and one client. The communication between the node and client will be session based. The client will send eleven requests. With the first request the client receives a session id that will be used for the following 10 requests. 

To run the example execute the following steps:

1. Go to the examples/lb-session directory.

2. First we start the balancer with the following command:

start router.bat

3. Now start two nodes:

start ant run-node1
start ant run-node2

4. Open the URL http://localhost:9000/admin/

5. Click on the LoadBalancer tab.

6. Create a cluster by typing "Default" into the input box and pressing "Add Cluster".

7. Click on the link called "Default".

8. Nodes are identified by host name and port. Fill in the formular with "localhost" as host and 4000 as port and press "Add Node".

9. Create another node with host name "localhost" and port 4001.

10. Go to the command line and run the following command:

start ant run-client -Dlogin=jim

11. Take a look at the output of node 1 and node 2. You will notice that only node 1 received requests. Thats because of the session id. Membrane sends messages with the same session id to the same node.  

12. Run the client again. You will notice that this time all requests will be send to node 2. 
     
     







