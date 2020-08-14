NOTICE: THIS IS A DRAFT

Service discovery with etcd

The publisher publishes endpoint details for services to "the cloud". The configurator reads those details from "the cloud" and dynamically forwards to those services
 
 
 
RUNNING THE EXAMPLE

In this example we will start a self-publishing service that responds with "hello" that can be called from another service without knowledge of the endpoint

1. Download etcd: go to "https://github.com/coreos/etcd/releases/", scroll down to the "Downloads" section and pick the version for your operating system
2. Extract etcd in any directory and start etcd
3. In the membrane folder go to "examples/service-discovery-with-etcd/"
4. Execute "configurator/start.bat"
5. Open your favorite browser and go to "localhost:9001", this opens the admin console of the configurator
6. Notice that "Console" is the only available service, "Console" is the admin console
7. Execute the publisher/start.bat 
8. Open another tab in your browser and go to "localhost:8081/myService" and receive "hello" as response, this is a service from the publisher
9. Go back to the admin console window and refresh the page, another service should appear
10. Open another tab in your browser and go to "localhost:8080/myService" and receive "hello" as response, this is a service from the configurator
11. Exit the publisher.bat and wait 20 seconds.
12. Go back to the admin console window and refresh the page, the second service should disappear



HOW IT IS DONE

This section describes the example in detail.  

First take a look at the proxies.xml file in the publisher folder.

<etcdPublisher baseUrl="http://localhost:4001" baseKey="/example" ttl="20"/>
	<router>
		<serviceProxy name="helloResponse" port="8081">
			<path>/myService</path>
			<groovy>				
			exc.response = Response.ok("Hello").build()
			RETURN
			</groovy>
		</serviceProxy>
	</router>

You will see that there is a service proxy that responds with "hello" when going to "http://localhost:8081" in you browser. On top of that you can see the etcdPublisher that is responsible for publishing the endpoint details to an etcd.

The etcdPublisher element has 3 values that you can set and by default it is set to publish to a locally started etcd on "http://localhost:4001" every 5 minutes.
baseUrl is the url the etcd is residing on
baseKey is the directory where the endpoint details are saved
ttl is how long in seconds the data in the etcd should be valid

Now take a look at the proxies.xml file in the configurator folder.

<etcdBasedConfigurator baseUrl="http://localhost:4001" baseKey="/example" port="8080"/>
	<router>
	<transport/>
		<serviceProxy name="Console" port="9001">
			<adminConsole />
		</serviceProxy>
	</router>

You will see a service proxy that provides the admin console on port 9001. Additionally on top you can see the etcdBasedConfigurator that reads from an etcd and creates service proxies for those services dynamically.
The etcdBasedConfigurator has 3 values that you can set and by default it is set to read from an etcd on "http://localhost:4001" and provides its service on port 8080.
baseUrl is the url the etcd is residing on
baseKey is the directory where the call details are saved
port is the port the etcdBasedConfigurator provides its service on

