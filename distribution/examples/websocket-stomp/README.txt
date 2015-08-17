WEBSOCKET STOMP EXAMPLE

In this example we are going to send STMOP over WebSocket, all handled by
Membrane Service Proxy.

STOMP is an abbreviation for Streaming Text Oriented Messaging Protocol. It's a
text based protocol for messaging, supported by ActiveMQ. Because of this, we
can connect ActiveMQ with our browser (utilizing a STOMP JavaScript library).

WebSocket is another protocol which allows full-duplex communication with a
single TCP connection. One can "upgrade" a HTTP connection by sending a header
specific HTTP header. More on this is covered at the end of this guide, after
the Running instructions.



PREPARATIONS

1.	Download ActiveMQ and unpack it.
	ActiveMQ DL Site: http://activemq.apache.org/download.html
	(Latest stable is probably a good idea.)
2.	Start ActiveMQ by executing "bin/activemq start" in the ActiveMQ directory.
3.	Go to the examples/websocket-stomp directory.
4.	Execute service-proxy.sh / service-proxy.bat
5.	Open the URL http://localhost:8161/admin/queues.jsp in your browser.
	Login with the username "admin" and the password "admin".
6.	Create a queue named "foo".



RUNNING THE EXAMPLE

Let's route some WebSocket Traffic over Membrane.

To run the example execute the following steps:

1.	Open the URL http://localhost:9000 in your browser.
2.	Login with the username "admin" and the password "membrane".
3.	Click on the "Stream Pumps" tab.

4.	Open another tab in your browser with the URL https://localhost:4443.
	You will probably get a certificate warning when this example runs with
	Membrane's standard certificate. For local testing, this can be ignored.
5.	If your browser has a JavaScript console, open it (F12 in Chrome).
	You should see the following output:
		connecting...
		connected.
6.	Press F5 on the Membrane Admin Console.
	You should now see two brand new Streamp Pumps.

7.	In the ActiveMQ admin overview you can now click on "Send To"
	for the queue foo you created earlier.
	Type in a message and click on Send.
8.	The message should instantly appear in the tab you opened in step 4.
9.	If you refresh the Admin Console again, you can see that the
	"Transferred Bytes" for one of the stream pumps went up a little.



HOW IT IS DONE

The following part describes the example in detail.

First, take a look at the proxies.xml file.


The service proxy on port 9000 is just an admin console to watch the stream pumps.

	<serviceProxy name="Console" port="9000">
		<basicAuthentication>
			<user name="admin" password="membrane" />
		</basicAuthentication>
		<adminConsole />
	</serviceProxy>


Then there is a serviceProxy (port 4443) that tests for a specific header value.
It does that with the if element:

	<if test="exc.request.header.getFirstValue('Upgrade') == 'websocket'">

The if element matches this specific HTTP header
	Upgrade: websocket

Now take a closer look at the groovy element:

	<groovy>
		exc.request.uri = "/"
		exc.destinations[0] = "http://localhost:61614/"
	</groovy>

The groovy element essentially redirects your call to the local port 61614,
which is ActiveMQ's preconfigured standard port for the websocket protocol.
After the groovy element, have the httpClient element
	<httpClient />
which does not return. This is where the stream pumps are working.
