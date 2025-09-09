# Building Membrane API Gateway

### What you need
* Java JDK 21
* Apache Maven
* git

### Building

You can skip the tests for speed.

```sh
git clone https://github.com/membrane/api-gateway
cd api-gateway
mvn install -DskipTests
```

NOTE: For a Release Checkout use

```sh
git clone --branch v6.X.X https://github.com/membrane/api-gateway
```

```sh
After the build find the distribution and the WAR at:
```

After building you'll find the distribution here:

```sh
../distribution/target/membrane-api-gateway-X.X.X(-SNAPSHOT).zip
../war/target/service-proxy-war-X.X.X(-SNAPSHOT).war
```

### Running the distribution

After extracting the distribution ZIP, start the gateway using the startup script for your platform:

```sh
# Linux/macOS
./membrane.sh

# Windows
membrane.cmd
```

In the `conf` folder of the extracted distribution, you will find `proxies.xml`, which is the main configuration file for routing, security, and plugins. The `examples` folder contains runnable samples that demonstrate how to configure and use Membrane features.

### Deploying the WAR

The `.war` artifact is intended for deployment to a Java web server/servlet container (e.g., Tomcat, Jetty). Use this if you prefer running Membrane inside an application server rather than as a standalone distribution.
