BASIC AUTHENTICATION INTERCEPTOR

With the BasicAuthentictionInterceptor you can secure your services or web pages by HTTP Basic Authentication.



RUNNING THE EXAMPLE

At the following URL you will get a REST representation of a customer resource.

http://www.thomas-bayer.com/sqlrest/CUSTOMER/-20/ 

In this example we will secure this resource with HTTP Basic Authentication.

To run the example execute the following steps: 

Execute the following steps:

1. Start Membrane Monitor.
2. Click on "File/Load Configuration" and open the file examples/basic-auth/rules.xml.
3. Open the URL http://localhost:2000/sqlrest/CUSTOMER/-20/ in your browser.
4. Login with the username membrane and the password membrane.



HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the rules.xml file.


<configuration>
  <rules>
    <forwarding-rule name="Basic Authentication" port="2000">
      <targetport>80</targetport>
      <targethost>www.thomas-bayer.com</targethost>
      <interceptors>
        <interceptor id="basicAuthenticationInterceptor"
                      name="Basic Authentication Interceptor" />
      </interceptors>
    </forwarding-rule>
  </rules>
</configuration>


You will see that there is a rule that directs calls to the port 2000 to www.thomas-bayer.com:80. Additionally the BasicAuthentictionInterceptor is set for the rule. The interceptor will be called during the processing of each request and response.

Now take a look at the bean configuration of the interceptor in the configuration/monitor-beans.xml file.


  <bean id="basicAuthenticationInterceptor" class="com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor">
    <property name="displayName" value="Basic Authentication Interceptor" />
    <property name="users">
    	<map>
    		<entry key="membrane" value="membrane"/>
    	</map>
    </property>
  </bean>


The interceptor can be configured with a map of users. Each username and password is given as a key-value pair. Use the key for the username and the value for the password. In our example there is one user with the username membrane who has got the password membrane. 

When you open the URL http://localhost:2000/sqlrest/CUSTOMER/-20/ in your browser the monitor will response with a 401 Not Authorized message.


TTP/1.1 401 Unauthorized
Content-Type: text/html;charset=utf-8
Date: Tue, 24 May 2011 10:00:02 GMT
Server: Membrane-Monitor 2.0.0
WWW-Authenticate: Basic realm="Membrane Authentication"
Connection: close

<HTML><HEAD><TITLE>Error</TITLE><META HTTP-EQUIV='Content-Type' CONTENT='text/html; charset=utf-8'></HEAD><BODY><H1>401 Unauthorized.</H1></BODY></HTML>


The response will have the WWW-Authenticate Header set. First the browser will ask you for your username and password. Than it will send the following request:


GET /sqlrest/CUSTOMER/-20/ HTTP/1.1
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
    <CITY>Omaha</CITY>
</CUSTOMER>
