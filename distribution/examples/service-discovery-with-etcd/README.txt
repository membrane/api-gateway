NOTICE: THIS IS A DRAFT

DYNAMIC FORWARDING TO SERVICE PROXIES 

The publisher publishes call details for services to "the cloud". The configurator reads those details from "the cloud" and dynamically forwards to those services
 
 
 
RUNNING THE EXAMPLE

In this example we will start a self-publishing service that responds with "hello" that is forwarded to from a different service. 

x. Download etcd: go to "https://github.com/coreos/etcd/releases/", scroll down to the "Downloads" section and pick the version for your operating system
x. Extract etcd in any directory and start etcd
x. Execute the Configurator/configurator.bat
x. Open your favorite browser and go to "localhost:9001", this opens the admin console of the configurator
x. Notice that "Console" is the only available service, "Console" is the admin console
x. Execute the Publisher/publisher.bat 
x. Open another tab in your browser and go to "localhost:8081/helloResponseService", this is a service from the publisher
x. Go back to the admin console window and refresh the page, another service should appear
x. Open another tab in your browser and go to "localhost:8080/helloResponseService", this is a service from the configurator
x. Exit the publisher.bat
x. Go back to the admin console window and refresh the page, the second service should disappear



HOW IT IS DONE

This section describes the example in detail.  

First take a look at the proxies.xml file in the Publisher folder.

<etcdPublisher baseUrl="http://localhost:4001" baseKey="/example"/>
	<router>
		<serviceProxy name="helloResponse" port="8081">
			<path>/helloResponseService</path>
			<groovy>				
			exc.response = Response.ok("Hello").build()
			RETURN
			</groovy>
		</serviceProxy>
	</router>

You will see that there is a service proxy that responds with "Hello" when going to "http://localhost:8081" in you browser. On top of that you can see the etcdPublisher that is responsible for publishing the call details to an etcd.

The etcdPublisher element has 2 values that you can set and by default it is set to publish to a local started etcd on "http://localhost:4001".
baseUrl is the url the etcd is residing on
baseKey is the directory where the call details are saved

Now take a look at the proxies.xml file in the Configurator folder.

<etcdBasedConfigurator baseUrl="http://localhost:4001" baseKey="/example" port="8080"/>
	<router>
	<transport/>
		<serviceProxy name="Console" port="9001">
			<adminConsole />
		</serviceProxy>
	</router>

You will see a service proxy that provides the admin console on port 9001. Additionally on top you can see the etcdBasedConfigurator that reads from an etcd and creates forwarding services dynamically.
The etcdBasedConfigurator has 2 values that you can set and by default it is set to read from an etcd on "http://localhost:4001".
baseUrl is the url the etcd is residing on
baseKey is the directory where the call details are saved
port is the port the etcdBasedConfigurator provides its service on

