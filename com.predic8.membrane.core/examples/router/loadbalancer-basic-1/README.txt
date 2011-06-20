LOAD BALANCING INTERCEPTOR

Using the LoadBalancerInterceptor you can balance requests to a number of different nodes.

The nodes can be organized in clusters. A Web based administration interface allows you to create and remove nodes and clusters. In addition a simple URL based interface can be used to add clusters and nodes. 


RUNNING THE EXAMPLE

In this example we will use the LoadBalancerInterceptor to redirect requests to 3 nodes. 

To run the example execute the following steps:

1. Go to the examples/lb directory.

2. Execute router.bat

3. Open the URL http://localhost:4000/ in your browser. 
   You will see a simple web app that counts how often it was opened. There are 2 more web apps of the same kind located at port 4001 and 4002.

4. Open the URL http://localhost:9000/admin/

5. Click on the LoadBalancer tab.

6. Click on the link called "Default".

7. Nodes are identified by host name and port. Fill in the formular with "localhost" as host and 4000 as port and press "Add Node".

8. Create another node with host name "localhost" and port 4001.

9. Open the URL http://localhost:8080. 

10. Click the refresh button in your browser a few times. You will notice that requests will be redirected to Node 1 and Node 2.

11. Open the URL 

http://localhost:8001/clustermanager/up?host=localhost&port=4002

This will create another node.

12. Open the URL 

http://localhost:9000/admin/clusters/show?cluster=Default

Notice the third node you have just created. 	

13. Open the URL http://localhost:8080 again. When you use the refresh button, you will notice that the request will be distributed between Node 1-3.  







