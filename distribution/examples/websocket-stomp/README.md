### WEBSOCKET STOMP EXAMPLE

In this example we are going to send STOMP over WebSocket, all handled by
Membrane Service Proxy.

STOMP is an abbreviation for Streaming Text Oriented Messaging Protocol. It's a
text based protocol for messaging, supported by ActiveMQ. Because of this, we
can connect ActiveMQ with our browser (utilizing a STOMP JavaScript library).

WebSocket is another protocol which allows full-duplex communication with a
single TCP connection. One can "upgrade" an HTTP connection by sending a header
specific HTTP header. More on this is covered at the end of this guide, after
the Running instructions.


#### PREPARATIONS


1.	Download ActiveMQ and unpack it.
	ActiveMQ DL Site:
		http://activemq.apache.org/download.html
	(The latest stable is probably a good idea.)
	  
2.	Start ActiveMQ by executing `bin/activemq start` in the ActiveMQ directory.
	  
3.	Go to the `examples/websocket-stomp directory`.
	  
4.	Execute `service-proxy.sh` / `service-proxy.bat`
	  
5.	Open the URL http://localhost:8161/admin/queues.jsp in your browser.

6.	Login with the username `admin` and the password `admin`.
	  
7.	Create a queue named `foo`.



#### RUNNING THE EXAMPLE


Let's route some WebSocket Traffic over Membrane.

To run the example execute the following steps:

1.	Open the URL
		http://localhost:9000
	in your browser.
	  
2.	Click on the "Stream Pumps" tab.

3.	Open another tab in your browser with the URL
		https://localhost:4443
	You will probably get a certificate warning when this example runs with
	Membrane's standard certificate. For local testing, this can be ignored.
	  
4.	If your browser has a JavaScript console, open it (F12 in Chrome).
	You should see the following output:
```
connecting...
connected.
  ```
5.	Press F5 on the Membrane Admin Console.
	You should now see two brand new Streamp Pumps.

6.	In the ActiveMQ admin overview you can now click on "Send To"
	for the queue foo you created earlier.
	Type in a message and click on Send.
	  
7.	The message should instantly appear in the tab where it says "Messages:".
	  
8.	If you refresh the Admin Console again, you can see that the
	"Transferred Bytes" for one of the stream pumps went up a little.

	  
#### HOW IT IS DONE

The following part describes the example in detail.

First, take a look at the `proxies.xml` file.


The service proxy on port `9000` is just an admin console to watch the stream pumps.

```
<serviceProxy name="Console" port="9000">
	<basicAuthentication>
		<user name="admin" password="membrane" />
	</basicAuthentication>
	<adminConsole />
</serviceProxy>
```


Then there is a serviceProxy (port `4443`) that contains a webSocket element:

```
<webSocket url="http://localhost:61614/" />
```

The webSocket element redirects your call to the local port `61614`, which is
ActiveMQ's preconfigured standard port for the webSocket protocol.

A webSocket element contains an if element which tests for a specific HTTP
header value:

```
<if test="exc.request.header.getFirstValue('Upgrade') == 'websocket'">
```

The if element matches this specific HTTP header

```
Upgrade: websocket
```

If the condition succeeded, the webSocket element redirects your call to the
given URL, which is http://localhost:61614/ in our case.

This redirects to the local port `61614`, which is ActiveMQ's preconfigured
standard port for the webSocket protocol.

