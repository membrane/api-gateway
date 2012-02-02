LOAD BALANCING CLIENT

In the previous example we set up a load balancer with 3 nodes. We used a URL based interface to register the nodes with
the balancer. That interface can be called from a simple client that optionally supports encrypted parameters.    


RUNNING THE EXAMPLE

In this example we will use a simple client to communicate with the URL interface. We also will encrypt the parameters
that are sent.

To run the example execute the following steps:

1. Go to the examples/loadbalancer-client-2 directory.

2. Execute 

   router.bat 

   This will start 3 web apps and the load balancer.

3. Open the URL http://localhost:4000/ in your browser. 
   You will see a simple web app that counts how often it was called. There are 2 more web apps of the same kind listing
   at port 4001 and 4002.

4. Execute 

   lbclient.bat up localhost 4000

5. Open the URL http://localhost:9000/admin/

6. Click on the "Load Balancing" tab. Then click on the link called "Default". On the next page, again click on "Default".

7. You will find there the node you registered with the lbclient.

8. Register 2 additional nodes:

   lbclient.bat up localhost 4001
   lbclient.bat up localhost 4002

10. Open the URL http://localhost:8080/service. 

11. Click the refresh button in your browser a few times. You will notice that requests will be distributed to Node 1-3.

12. Execute 

    lbclient.bat down localhost 4000

13. Open the URL http://localhost:8080/service again. After several refreshes, you will notice that no request are directed to
    node 1 anymore.  

14. Stop the router by closing the command line that runs the router.

15. Execute 

    router-secured.bat

16. Execute 

    lbclient.bat up localhost 4000

    The parameters are not encrypted so the operation fails.

17. Open the file client.properties

18. Remove the # from the following line:

    #key=6f488a642b740fb70c5250987a284dc0

19. Execute:

    lbclient.bat up localhost 4000

    This time the parameters will be encrypted.

20. Open 

    http://localhost:9000/admin/clusters/show?cluster=Default

21. Login with admin/admin and notice the node you created.