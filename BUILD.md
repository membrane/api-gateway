# Building Membrane API Gateway

### What you need
* Java JDK 17
* Apache Maven
* git

Check the JAVA Version.

	java -version
	openjdk version "18.0.2" 2022-07-19

### Building

	git clone https://github.com/membrane/api-gateway
	cd api-gateway
	mvn install

NOTE: For a Release Checkout use

        git clone --branch v4.0.19 https://github.com/membrane/api-gateway
before checking out, or

        git checkout v4.0.19
after checking out.

After the build find the distribution and the WAR at:

	../distribution/target/membrane-api-gateway-X.X.X.zip
	../war/target/api-gateway-war-X.X.X.war
