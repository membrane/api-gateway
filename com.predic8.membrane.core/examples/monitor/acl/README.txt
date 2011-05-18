ACCESS CONTROL INTERCEPTOR

With the AccessControlInterceptor you can enable access to services from desired clients only.





RUNNING THE EXAMPLE

In this example we will call a SOAP Web Service by a simple HTTP GET request. We will use the BLZService. It's a SOAP Web Service that identifies the name, zip code and region of a bank by its banking code. You can take a look at the wsdl at 

http://www.thomas-bayer.com/axis2/services/BLZService?wsdl


To run example with ROUTER execute the following steps:
. 
1. Go to examples/acl directory.
2. Run router executable file.
3. Open the URL http://localhost:2000/ in your browser.

To run example with MEMBRANE MONITOR execute the following steps:

1. Start the Membrane Monitor.

HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the rules.xml file.


<configuration>
  <rules>
    <forwarding-rule name="predic8.de (localhost only)" port="2000">
      <targetport>80</targetport>
      <targethost>predic8.de</targethost>
      <interceptors>
        <interceptor id="accessControlInterceptor" />
      </interceptors>
    </forwarding-rule>
  </rules>
</configuration>


You will see that there is a rule that directs calls to the port 2000 to predic8.de:80. Additionally the AccessControlInterceptor is set for the rule. The interceptor will be called during the processing of each request.


Now take a look at the bean configuration of the interceptor in the examples/acl/acl-beans.xml file.

<bean id="accessControlInterceptor" class="com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor">
    <property name="displayName" value="Access Control List Interceptor" />
    <property name="aclFilename" value="acl.xml" />
</bean>


The property 


