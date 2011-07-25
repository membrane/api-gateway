LOAD BALANCING CLIENT

In the previous example we set up a load balancer with 3 nodes. We used a URL based interface to register the nodes with the balancer. That interface can be called from a simple client that optionally supports encrypted parameters.    


RUNNING THE EXAMPLE

In this example we will use a simple client to communicate with the URL interface. We also will encrypt the parameters that are sent.

To run the example execute the following steps:

1. Go to the examples/loadbalancer-client-2 directory.

2. Execute 

start router.bat. 

This will start 3 web apps and the load balancer.

3. Open the URL http://localhost:4000/ in your browser. 
   You will see a simple web app that counts how often it was called. There are 2 more web apps of the same kind listing at port 4001 and 4002.

4. Execute 

java -jar lbclient.jar up localhost 4000

5. Open the URL http://localhost:9000/admin/

6. Click on the LoadBalancer tab. Than click on the link called "Default".

7. You will find there the node you registered with the lbclient.

8. Register 2 additional nodes:

java -jar lbclient.jar up localhost 4001
java -jar lbclient.jar up localhost 4002

10. Open the URL http://localhost:8080. 

11. Click the refresh button in your browser a few times. You will notice that requests will be distributed to Node 1-3.

12. Execute 

java -jar lbclient.jar down localhost 4000

13. Open the URL http://localhost:8080 again. After several refreshes, you will notice that no request are directed to node 1 anymore.  

14. Stop the router by closing the command line that runs the router.

15. Execute 

start router-secured.bat

16. The parameters are not encrypted so the operation fails.

17. Open the file client.properties

18. Remove the # from the following line:

#key=b0iKZCt0D7cMUlCYeihNwA==

19. Execute:

java -jar lbclient.jar up localhost 4000

This time the parameters will be encrypted.

20. Open 

http://localhost:9000/admin/clusters/show?cluster=Default

21. Login with admin/admin and notice the node you created.