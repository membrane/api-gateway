Prequisites
===========
* Java (1.6 for backwards-compatible development, above will do otherwise)
* Maven 3
* Eclipse (3.7, 4.2, ...)
	* m2e plugin
* some git client

Meeting the Requirements
========================

1. To import the Project into your Eclipse, you have to install the Maven plugin for Eclipse(m2e plugin). 

2. To install the plugin start Eclipse and select Help->Install New Software.

3. Select http://download.eclipse.org/releases/juno in the Drop down Menu "Work with:".

4. Then search for "maven" within the listed plugins. Fastest option is to use the Filter.

5. Select the m2e plugin named "m2e - Maven Integration for Eclipse" and hit next.

6. Now you'll have to finish this installation by accepting the terms of agreement. Eclipse should be installing the plugin now.


Building
========

1. Checkout the source code from https://github.com/membrane/service-proxy .

2. Now go to Files->import and select "Existing Maven projects".

3. In the next step you will have to set the service-proxy\cli directory as root-directory. Hit Finish. Eclipse is now building the Workspace, this might take some time.

4. Copy cli/router/conf to cli/conf . (Won't be checked in.)

5. Right click the membrane-esb-core and run as "Maven install".

6. Right click membrane-esb-cli and run as "Maven install".

7. After doing so membrane-esb-cli-x.x.x.zip should be in the \cli\target folder.


Running Service Proxy in Eclipse
================================

1. To run cli, navigate src/main/java/com.predic8.membrane.core/ and run IDEStarter as "Run Configurations...".

2. Go to the Enviroment-tab and set the variable "MEMBRANE_HOME" to the \service-proxy\cli directory

3. Hit run.
