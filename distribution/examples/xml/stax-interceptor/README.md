# Custom StAX XML Interceptor Sample

This example demonstrates how to install and configure a custom interceptor, `StaxConverterInterceptor`, which transforms XML tag names from `<foo>` to `<bar>` using the Java Streaming API for XML (StAX). This approach allows for efficient and lightweight streaming XML transformations directly within the API Gateway.

## Running the Example

Follow the steps below to build and deploy the custom StAX interceptor:

### Step 1: Compile the Interceptor
1. Navigate to the `examples/xml/stax-interceptor/` directory:
   ```sh
   cd <membrane-root>/examples/stax-interceptor
   ```
2. Compile and package the interceptor using Maven:
   ```sh
   mvn package
   ```

### Step 2: Make the Interceptor Available to Membrane
Upon successful packaging, the interceptor `.jar` file needs to be copied to Membrane's `lib` directory. This is automated using the `maven-resources-plugin` in the `pom.xml` file. During the `package` phase, the plugin copies the compiled `.jar` to the `lib` directory, ensuring it is available at runtime.

```xml
<plugin>
    <artifactId>maven-resources-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>copy-resource-stax</id>
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
                            <include>stax-converter-1.0-SNAPSHOT.jar</include>
                        </includes>
                    </resource>
                </resources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Step 3: Start Membrane API Gateway
Run the Membrane service proxy:
```sh
# For Unix/Mac
./membrane.sh

# For Windows
membrane.cmd
```

### Step 4: Test the Transformation
1. Open a second terminal.
2. Review the `request.xml` file, which contains the following XML:
   ```xml
   <bar xmlns="predic8.de/sample">
     <foo>42</foo>
   </bar>
   ```
3. Send the XML to the API:
   ```sh
   curl -d @request.xml http://localhost:2000 -H "Content-Type: application/xml"
   ```
4. Observe the output, where the `<foo>` element has been replaced by `<bar>`:
   ```xml
   <bar xmlns="predic8.de/sample">
     <bar>42</bar>
   </bar>
   ```

## How It Works

### Building and Deploying the Interceptor
- The interceptor is packaged as a `.jar` file using Maven.
- The compiled `.jar` is automatically copied to the `lib` directory of Membrane API Gateway during the `package` phase.

### Configuring the Interceptor in `proxies.xml`

To make the interceptor active, register it in `proxies.xml` as a Spring bean:
```xml
<spring:bean id="staxInterceptor" class="com.predic8.myInterceptor.StaxConverterInterceptor" />
```

Apply the interceptor to API requests by referencing the bean inside the `<request>` block:
```xml
<api name="stax-api" port="2000">
  <request>
    <interceptor refid="staxInterceptor"/>
  </request>
  <beautifier />
  <echo/>
</api>
```

### Interceptor Logic
- **Request Interception:** When a request is received, the interceptor inspects the XML payload.
- **Tag Transformation:** The interceptor uses the StAX API to parse and modify the XML, replacing `<foo>` with `<bar>`.
- **Response Echoing:** The `echo` interceptor returns the modified request, allowing users to verify the transformation in the response.

By leveraging the StAX API, this interceptor performs efficient XML transformations, ensuring minimal performance overhead while maintaining flexibility in request processing.

