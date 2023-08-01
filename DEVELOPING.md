Prequisites
===========
* Java Development Kit (Membrane's source is Java 17 compatible.)
* Maven 3
* IntelliJ or Eclipse (3.7, 4.2, ...) with the m2e plugin
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

Integration Testing
===================

To run the integration tests in an isolated environment, run

    docker build .
	
if you have a Docker Engine available.


Examples Tests
==============

The example tests try to run the service-proxy.sh/bat scripts in the examples folders of a
distribution build in the target folder. Remember to build after any change otherwise old code
is tested:

Build a distribution in target/ . Then run the example tests.
```sh
mvn clean install -DskipTests
```


## Prequisites

On Mac OS the `setsid` command is needed. It can be installed with:

```sh
brew install util-linux
```

## Running

Run Testclass `ExampleTests`

## Project Site

To build the maven project site, run the `site` goal:
```shell
mvn site
```
Besides the maven project site, a set of reports is generated for:
* CVE Vulnerabilities
* Dependency Versions
* Maven Plugin Versions

## Dependency updates

After generating the version reports, Maven can automatically update the POM with the latest dependency versions:

### Apply changes to POM
To apply the latest version information to the POM, make sure to run `mvn site` to generate the reports, then run the following command:
```shell
mvn versions:use-latest-versions
```
By default, dependencies are updated to the next latest version available, be it patch, minor or major version.
If the version target should be limited, use any combination of the following properties:

* `-DallowIncrementalUpdates=false`
* `-DallowMinorUpdates=false`
* `-DallowMajorUpdates=false`

For more detailed information visit the [versions-maven-plugin](https://www.mojohaus.org/versions/versions-maven-plugin/examples/advancing-dependency-versions.html) reference.

### Reverting changes to POM
*Note: Do not rely on this mechanism, make sure to utilize an SCM system.*
```shell
mvn versions:revert
```

### Finalizing changes to POM
*Note: This will remove any backup data and permanently accept the new POM information.*
```shell
mvn versions:commit
```

## Troubleshooting

- 