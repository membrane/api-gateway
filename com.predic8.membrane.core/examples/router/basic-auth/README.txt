BASIC AUTHENTICATION INTERCEPTOR

With the BasicAuthentictionInterceptor you can secure your services or web pages by HTTP Basic Authentication.



RUNNING THE EXAMPLE

At the following URL you will get a REST representation of a customer resource.


http://www.thomas-bayer.com/sqlrest/CUSTOMER/2/ 


In this example we will secure this resource with HTTP Basic Authentication.

To run the example execute the following steps: 

Execute the following steps:

1. Go to the examples/basic-auth directory.

2. Execute router.bat

3. Open the URL http://localhost:2000/sqlrest/CUSTOMER/2/ in your browser.

4. Login with the username alice and the password membrane.



HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the basic-auth.proxies.xml file.


<proxies>
	<serviceProxy port="2000">
		<basicAuthentication>
			<user name="alice" password="membrane" />
		</basicAuthentication>
		<target host="www.thomas-bayer.com" port="80" />
	</serviceProxy>
</proxies>


You will see that there is a serviceProxy that directs calls to the port 2000 to www.thomas-bayer.com:80. Additionally the BasicAuthentictionInterceptor is set for the rule. The interceptor will be called during the processing of each request and response.

Now take a closer look at the basicAuthentication element:


<basicAuthentication>
	<user name="membrane" password="membrane" />
</basicAuthentication>


The basicAuthentication elements sets up a BasicAuthenticationInterceptor. You can add users by using nested user elements. The user name and the password are given by the attributes name and password of the user element. In our example there is one user with the username membrane who has got the password membrane. 

When you open the URL http://localhost:2000/sqlrest/CUSTOMER/2/ in your browser the monitor will response with a 401 Not Authorized message.


TTP/1.1 401 Unauthorized
Content-Type: text/html;charset=utf-8
Date: Tue, 24 May 2011 10:00:02 GMT
Server: Membrane-Monitor 2.0.1
WWW-Authenticate: Basic realm="Membrane Authentication"
Connection: close

<HTML><HEAD><TITLE>Error</TITLE><META HTTP-EQUIV='Content-Type' CONTENT='text/html; charset=utf-8'></HEAD><BODY><H1>401 Unauthorized.</H1></BODY></HTML>


The response will have the WWW-Authenticate Header set. First the browser will ask you for your username and password. Than it will send the following request:


GET /sqlrest/CUSTOMER/2/ HTTP/1.1
Host: localhost:2000
User-Agent: Mozilla/5.0 (Windows NT 6.1; rv:2.0.1) Gecko/20100101 Firefox/4.0.1
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
Accept-Language: de-de,de;q=0.8,en-us;q=0.5,en;q=0.3
Accept-Encoding: gzip, deflate
Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7
Keep-Alive: 115
Connection: keep-alive
Authorization: Basic bWVtYnJhbmU6bWVtYnJhbmU=
X-Forwarded-For: 0:0:0:0:0:0:0:1


Notice how the Authorization header is set with the hash of your username and password. If the user is valid, membrane will let the request pass and the target host will respond with the following:


<CUSTOMER>
    <ID>-20</ID>
    <FIRSTNAME>Rick</FIRSTNAME>
    <LASTNAME>Cortés Ribotta</LASTNAME>
    <STREET>Calle Pública "B" 5240 Casa 121</STREET>
    <CITY>Sydney100</CITY>
</CUSTOMER>
