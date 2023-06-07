# Loadbalancing - Node Management using an API - Example

In the previous example we set up a load balancer with 3 nodes. We used a Web interface to register nodes with the balancer. Now we will use an API and clients to register nodes.    


## Get Started 

In this example we will use a simple client to communicate with the API to manage nodes. We also encrypt the parameters
that are sent.

To run the example execute the following steps:

1. Go to the `examples/loadbalancing/3-node-api` directory.

2. Run `service-proxy.bat` or `service-proxy.sh`

   This will start three nodes, the loadbalancer and the node management API.

3. Open the URL http://localhost:4000/ in your browser.

    You will see a simple web app that counts how often it was called. There are 2 more endpoints of the same kind listening at port `4001` and `4002`. However, it is possible that it will count twice a single connection, due to the browser checking for the favicon.

4. Register a node on `localhost` on port `4000` by running the `lbclient.sh/bat` script.

    `lbclient.sh up localhost 4000`

5. Open the Admin Web console. 

    `http://localhost:9000/admin`

6. Click on the "Load Balancing" tab. Then click on the link called "Default". On the next page, again click on "Default".

7. You will find there the node you registered with the lbclient.

8. Register 2 additional nodes:

   `lbclient.sh up localhost 4001`
   
   `lbclient.sh up localhost 4002`

10. Send a request to the loadbalancer: 

    `http://localhost:8080`

11. Click the refresh button in your browser a few times. You will notice that requests will be distributed to node 1 to 3.

12. Put a node off:

    `lbclient.sh down localhost 4000`

13. Open `http://localhost:8080` again. After several refreshes, you will notice that no request are directed to node 1 anymore.  



## Secure the Management API by Encryption 

1. First stop the router by closing the command line that runs the router e.g. by using CTRL-C.

2. Execute `service-proxy-secured.bat` or `service-proxy-secured.sh`

3. Execute `lbclient.sh up localhost 4000`

    The parameters are not encrypted, so the operation fails.

4. Open the file `client.properties`

5. Remove the # from the following line:

    `#key=6f488a642b740fb70c5250987a284dc0`

6. Execute `lbclient.bat up localhost 4000`

    This time the parameters are encrypted.

7. Open http://localhost:9000/admin/clusters/show?cluster=Default

8. Login with admin/admin and notice the node you just added.


Of course, you can protect the management API like any other Membrane proxy using SSL, a password or token.

---
See:
- [balancer](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/balancer.htm) reference
