#Building Membrane Service Proxy

###What you need
* Java JDK 1.7
* Apache Maven
* git

Check the JAVA Version.

	java -version
	java version "1.7.0_76"

###Building

	git clone https://github.com/membrane/service-proxy
	cd service-proxy
	mvn install

NOTE: For a Release Checkout use

        git clone --branch v4.0.19 https://github.com/membrane/service-proxy
before checking out, or

        git checkout v4.0.19
after checking out.

After the build find the distribution and the WAR at:

	../distribution/target/membrane-service-proxy-X.X.X.zip
	../war/target/service-proxy-war-X.X.X.war
