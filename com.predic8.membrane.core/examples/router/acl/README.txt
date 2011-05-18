ACCESS CONTROL INTERCEPTOR

With the AccessControlInterceptor you can enable access to services from desired clients -and desired resources only.


RUNNING THE EXAMPLE

In this example we will call a SOAP Web Service by a simple HTTP GET request. We will use the BLZService. It's a SOAP Web Service that identifies the name, zip code and region of a bank by its banking code. You can take a look at the wsdl at 

http://www.thomas-bayer.com/axis2/services/BLZService?wsdl

To run example execute the following steps:

1. Go to examples/acl directory.
2. Run router executable file.
3. Open the URL http://localhost:2000/ in your browser.


HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the examples/acl/rules.xml file.

<configuration>
  <rules>
    <forwarding-rule name="predic8.de (localhost only)" port="2000">
      <targetport>80</targetport>
      <targethost>predic8.de</targethost>
    </forwarding-rule>
  </rules>
</configuration>


The rule directs calls to the port 2000 to predic8.de:80. 

Now take a look at the bean configuration of the interceptor in the examples/acl/acl-beans.xml file.

<bean id="accessControlInterceptor" class="com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor">
    <property name="displayName" value="Access Control List Interceptor" />
    <property name="aclFilename" value="acl.xml" />
</bean>


The value of the property 'aclFilename' is access control list XML file. Before processing the first request file is read and access control component is initialized. 


Now take a look at access control list file examples/acl/acl.xml:

<accessControl>
	<resource uri="*">
		<clients>
			<hostname>localhost</hostname>
		</clients>
	</resource>
</accessControl>   


For each resource a list of authorized clients can be specified. Clients can be referred by host name or IP address, therefore you can use <hostname> and <ip> XML elements respectively.



  





