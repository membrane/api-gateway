# Building Membrane API Gateway

### What you need
* Java JDK 21
* Apache Maven

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

After building, you'll find the distribution here:

```sh
../distribution/target/membrane-api-gateway-X.X.X(-SNAPSHOT).zip
```

### Running the distribution

After extracting the distribution ZIP, start the gateway using the startup script for your platform:

```sh
# Linux/macOS
./membrane.sh

# Windows
membrane.cmd
```

In the `conf` folder of the extracted distribution, you will find `apis.yaml`, which is the main configuration file. The `tutorials` folder contains runnable samples that demonstrate how to configure and use Membrane.
