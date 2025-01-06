# Building Membrane API Gateway

### What you need
* Java JDK 21
* Apache Maven
* git

### Building

You can skip the tests for speed.

```sh
git clone https://github.com/membrane/service-proxy
cd api-gateway
mvn install -DskipTests
```

NOTE: For a Release Checkout use

git clone --branch v5.X.X https://github.com/membrane/api-gateway

```sh
After the build find the distribution and the WAR at:
```

After building you'll find the distribution here:

```sh
../distribution/target/membrane-service-proxy-X.X.X.zip
../war/target/service-proxy-war-X.X.X.war
```