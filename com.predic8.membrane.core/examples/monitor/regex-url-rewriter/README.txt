REGEX URL REWRITE INTERCEPTOR

With the RegExURLRewriteInterceptor you can rewrite URLs by regular expressions. 



RUNNING THE EXAMPLE

In this example we will rewrite a simple URL. Take a look at the WSDL located at

http://www.thomas-bayer.com/axis2/services/BLZService?wsdl

We want to hide that this service is hosted by axis2. To do this we have to replace the part axis2 from the context path. We can achieve this by using the RegExURLRewriteInterceptor as follows: 

1. Start the Membrane Monitor.
2. Click on "File/Load Configuration" and open the file examples/regex-url-rewriter/rules.xml.
3. Open the URL http://localhost:2000/bank/services/BLZService?wsdl in your browser.



HOW IT IS DONE

This section describes the example in detail.  

First take a look at the rules.xml file.

<configuration>
  <rules>
    <forwarding-rule name="BLZ without AXIS2"
                      port="2000">
      <targetport>80</targetport>
      <targethost>www.thomas-bayer.com</targethost>
      <interceptors>
        <interceptor id="regExUrlRewriter"
                      name="RegEx URL Rewriter" />
      </interceptors>
    </forwarding-rule>
  </rules>
</configuration>

You will see that there is a rule that directs calls to the port 2000 to www.thomas-bayer.com:80. Additionally the RegExURLRewriteInterceptor is set for the rule. The interceptor will be called during the processing of each request and response.

Now take a look at the bean configuration of the interceptor in the configuration/monitor-beans.xml file.

<bean id="regExUrlRewriter" class="com.predic8.membrane.core.interceptor.rewrite.RegExURLRewriteInterceptor">
    <property name="displayName" value="RegEx URL Rewriter" />
    <property name="mapping">
      <map>
        <entry>
          <key>
            <value>/bank/(.*)</value>
          </key>
          <value>/axis2/$1</value>
        </entry>
      </map>
	</property>
</bean>

The interceptor is configured with one regular expression /bank/(.*) . This will match the following URIs:

/bank/services/BLZService?wsdl
/bank/services/BLZService

In the regular expression we use a group that we can reference when replacing the URI. The value to replace the URI is set to /axis/$1 . The $1 is the reference to the group and will contain the value matched by the group. So the above URIs will be replaced by the following:

/axis2/services/BLZService?wsdl
/axis2/services/BLZService
 
