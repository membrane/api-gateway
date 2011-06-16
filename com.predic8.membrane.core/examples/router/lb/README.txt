LOAD BALANCING INTERCEPTOR

With the LoadBalancerInterceptor you can redirect request to different nodes.

The Nodes can be organized in clusters. An administration interface that you can open in your browser allows you to create and remove nodes and clusters. In addition a simple URL based interface can by used to configure nodes and cluster. 


RUNNING THE EXAMPLE

In this example we will use the LoadBalancerInterceptor to redirect requests to 3 nodes. 

To run the example execute the following steps:

1. Go to the examples/lb directory.

2. Execute router.bat

3. Open the URL http://localhost:4000/ in your browser. 
   You will see a dummy web app that counts how often it was opened. There are 2 more web apps of the same kind located at port 4001 and 4002.

4. Open the URL http://localhost:9000/admin/

5. Click on the LoadBalancer tab.

6. Create a cluster by tipping "Default" into the input box and pressing "Add".

7. Click on the link called "Default".

8. Nodes are identified by host name and port. Fill the formular with "localhost" as host and 4000 as port and press "Add".

9. Create another node with host name "localhost" and port 4001.

10. Open the URL http://localhost:8080. 

11. Click the refresh button in your browser a few times. You will notice that request will be redirected to Node 1 and Node 2.

12. Open the URL http://localhost:8001/clustermanager/up?host=localhost&port=4002. This will create another node.

13. Open the URL http://localhost:9000/admin/clusters/show?cluster=Default. Notice the third node you have just created. 	

14. Open the URL http://localhost:8080 again. When you use the refresh button, you will notice that the request will be distributed between Node 1-3.  

HOW IT IS DONE







