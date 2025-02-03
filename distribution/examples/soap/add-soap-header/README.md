# Add a Custom Header to SOAP Messages 

This example demonstrates how to manipulate SOAP messages using a custom interceptor in Membrane API Gateway. A custom interceptor allows reading and modifying incoming and outgoing requests. In this case, a `Security` header containing a `UsernameToken` is dynamically added to SOAP requests.

## Background
Interceptors provide a powerful mechanism to enforce security, modify requests, or add additional headers without changing the core service logic. This approach helps implement WS-Security features or perform logging and validation at the gateway level.

## Running the Example

### Step 1: Review the Interceptor Code
Have a look into the `src` folder and review the `AddSoapHeaderInterceptor` class. This class contains the logic to add the SOAP `Security` header.

### Step 2: Compile the Interceptor
1. Change to the example directory:
   ```sh
   cd <membrane-root>/examples/soap11/add-soap-header
   ```
2. Compile the interceptor with Maven:
   ```sh
   mvn package
   ```

### Step 3: Make the Interceptor Available in the Classpath
After packaging the interceptor, it needs to be copied to Membrane's `lib` directory to be available in the classpath. This is achieved using the `maven-resources-plugin` in the `pom.xml` file. During the `package` phase, the plugin copies the compiled `.jar` file from the `target` directory to the `lib` directory of Membrane.

```xml
<plugin>
    <artifactId>maven-resources-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>copy-resource-one</id>
            <phase>package</phase>
            <goals>
                <goal>copy-resources</goal>
            </goals>
            <configuration>
                <outputDirectory>../../../lib</outputDirectory>
                <resources>
                    <resource>
                        <directory>./target/</directory>
                        <includes>
                            <include>soap-header-adder-1.0-SNAPSHOT.jar</include>
                        </includes>
                    </resource>
                </resources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

This ensures the interceptor `.jar` is automatically copied to Membrane's `lib` folder, making it available to the runtime environment.

### Step 4: Start Membrane API Gateway
Run the appropriate command based on your operating system:
   ```sh
   # For Unix/Mac
   ./service-proxy.sh

   # For Windows
   service-proxy.ps1
   ```

### Step 5: Test the Service
1. Open a second terminal.
2. Review the SOAP request without the `Security` header in `soap-message-without-header.xml`:
   ```xml
   <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/" s11:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
     <s11:Body>
       <p8:getArticle xmlns:p8="http://predic8.de/membrane/">
         <id>112358</id>
       </p8:getArticle>
     </s11:Body>
   </s11:Envelope>
   ```
3. Send the SOAP request to Membrane:
   ```sh
   curl -d @soap-message-without-header.xml http://localhost:2000 -H "Content-Type: application/xml"
   ```
4. Observe the output containing the added `Security` SOAP header:
   ```xml
   <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/"
                 s11:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
     <s11:Header xmlns:s11="http://schemas.xmlsoap.org/soap/envelope">
       <wss:Security xmlns:wss="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
         <UsernameToken>
           <Username>root</Username>
           <Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">cHJlZGljOCBSb2NrcyEK</Password>
         </UsernameToken>
       </wss:Security>
     </s11:Header>
     <s11:Body>
       <p8:getArticle xmlns:p8="http://predic8.de/membrane/">
         <id>112358</id>
       </p8:getArticle>
     </s11:Body>
   </s11:Envelope>
   ```

## How It Works

### Building and Deploying the Interceptor
1. The interceptor is compiled into a `.jar` file using Maven.
2. The compiled `.jar` is placed into the `libs` directory of Membrane API Gateway.

### Configuring the Interceptor in `proxies.xml`
The following configuration in `proxies.xml` registers the interceptor as a `Spring Bean`:
```xml
<spring:bean id="headerAddInterceptor" class="com.predic8.AddSoapHeaderInterceptor" />
```

Next, the interceptor is applied to an API by referencing the bean name:
```xml
<api name="echo" port="2000">
  <interceptor refid="headerAddInterceptor"/>
  <beautifier />
  <echo/>
</api>
```

### Interceptor Logic
- **Request Inspection:** The interceptor checks if the request content is XML.
- **Header Modification:** If no `Header` element exists, the interceptor adds a `Security` header containing a `UsernameToken`.

### Notes
- The `echo` interceptor simply returns the request for demonstration purposes. In a real deployment, the `<target>` element would typically forward requests to a backend service.


