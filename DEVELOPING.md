Prequisites
===========
* Java (1.6 for backwards-compatible development, above will do otherwise)
* Maven 3
* Eclipse (3.7, 4.2, ...)
	* m2e plugin
* some git client

Get Started
===========
1. Checkout source code from https://github.com/membrane/service-proxy .

2. Import "Maven projects" into Eclipse from this directory

3. Copy cli/router/conf to cli/conf . (Won't be checked in.)

4. adapt cli/conf/proxies.xml to your development setup.

5. Run cli src/main/java com/predic8/membrane/core/IDEStarter as "Java Application".
