Prequisites
===========
* Java Development Kit (Membrane's source is Java 1.6 compatible. But it requires annotation processing features not supported by the Sun JDK 1.6 compiler: Use the Oracle JDK 1.7 instead, setting the target to 1.6, if necessary.)
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


Building in Eclipse
===================

1. Checkout the source code from https://github.com/membrane/service-proxy .

2. Now go to Files->import and select "Existing Maven projects".

3. In the next section you will have to set the service-proxy\cli directory as root-directory. Hit Finish. Eclipse is now building the Workspace, this might take some time. Some build errors will remain until you have completed the next steps.

4. The project service-proxy-annot should be error-free. Right click it and run as "Maven install".

5. Enable annotation processing on service-proxy-core and service-proxy-war: For both projects,
   a. Right-click the project.
   b. Choose "Properties".
   c. Expand "Java Compiler".
   d. Select "Annotation Processing".
   e. Check both
      [X] Enable project specific settings
      [X] Enable annotation processing

(Skip to the next section, if you don't need a distribution build.)

6. Right click the service-proxy-core and run as "Maven install".

7. Right click membrane-service-proxy and run as "Maven install".

8. After doing so membrane-service-proxy-x.x.x.zip should be in the \cli\target folder.


Running Service Proxy in Eclipse
================================

1. To run cli, navigate src/main/java/com.predic8.membrane.core/ and run IDEStarter as "Java Application".
