# Distribution Module

The distribution module builds the final Membrane API Gateway release package. It assembles all required artifacts, scripts, examples, tutorials, and metadata into a standalone ZIP archive that can be downloaded and executed without Maven.

The module uses the Maven Assembly Plugin together with src/assembly/distribution.xml to control the contents of the distribution.

## Responsibilities

- Build the Membrane API Gateway distribution ZIP archive
- Providing static content for the distribution
- Example tests
- Filtering out some proxies.xml example files

## Building the Distribution

mvn clean package

or for development:

mvn clean install -DskipTests 

This produces:

distribution/target/membrane-api-gateway-<version>.zip

## See

pom.xml
src/assembly/distribution.xml