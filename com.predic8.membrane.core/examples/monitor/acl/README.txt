ACCESS CONTROL INTERCEPTOR

With the AccessControlInterceptor you can enable access to services from desired clients only.





RUNNING THE EXAMPLE

To run example execute the following steps:

1. Start the Membrane Monitor.
2. 


HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the rules.xml file.


<configuration>
  <rules>
    <forwarding-rule name="predic8.de (localhost only)" port="2000">
      <targetport>80</targetport>
      <targethost>predic8.de</targethost>
    </forwarding-rule>
  </rules>
</configuration>


You will see that there is a rule that directs calls to the port 2000 to predic8.de:80.


Now take a look at the bean configuration of the interceptor in the examples/acl/acl-beans.xml file.

<bean id="accessControlInterceptor" class="com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor">
    <property name="displayName" value="Access Control List Interceptor" />
    <property name="aclFilename" value="acl.xml" />
</bean>


The property 


