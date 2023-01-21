### ACCESS CONTROL INTERCEPTOR

With the AccessControlInterceptor you can restrict access to services and resources.

An ACL file allows a fine-grained configuration of permissions. Access can be restricted based on the ip address,  hostname and the URI of the requested resource.


#### RUNNING THE EXAMPLE

In this example we will make an HTTP GET request call to secured resources. 

To run the example execute the following steps:

1. Go to the `examples/acl` directory.

2. Execute `service-proxy.bat`

3. Open the URL http://localhost:2000/ in your browser. 
   The predic8.de website will be displayed in your browser.

4. Open the URL http://localhost:2000/contact/
   The predic8 contacts site will be displayed. 
    
5. Open the URL http://localhost:2000/open-source/ 
   The warning message 'Access denied: you are not authorized to access this service.' will be displayed in your browser.    

6. If you access the service from other computers, all URIs will be available except the URIs starting with `/contact/` or `/open-source/`. 	


#### HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the proxies.xml file.

```
<proxies>
	<serviceProxy name="predic8.com" port="2000">
		<accessControl file="acl.xml" />
		<target host="predic8.com" port="80" />
	</serviceProxy>
</proxies>
```
The `serviceProxy` forwards calls to the port 2000 to `predic8.com:80`. 

Further a AccessControl interceptor is added to the serviceProxy.

```
<accessControl file="acl.xml" />
```

The value of the attribute 'file' is the name of the access control list XML file. 
Before processing the first request, the ACL file is read, and the access control component is initialized. 

Next take a look at `acl.xml` file located under the `examples/acl` directory:

```
<accessControl>
	
  <resource uri="/open-source/*">
    <clients>
	  <ip>192.168.2.*</ip>
    </clients>
  </resource>
    
  <resource uri="/contact/*">
    <clients>
	  <hostname>localhost</hostname>
	</clients>
  </resource>
    
  <resource uri="*">
	<clients>
	    <any/>
	</clients>
  </resource>
    
</accessControl>   
```

For each resource a list of authorized clients can be specified.  The resource is referred by its URI and 
clients can be referred by hostname or IP address, therefore you can use <hostname> and <ip> XML elements respectively.
The element <any> can be used to grant permission to all clients.

The access permissions are scanned from top to bottom, therefore the order of the rules is significant.






  





