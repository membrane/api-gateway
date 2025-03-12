# Prerequisites
- Java 21+ (JDK)
- Maven 3
- IntelliJ IDEA (Community or Ultimate)
- Git client

# Setup in IntelliJ
1. Clone the repo: `git clone https://github.com/membrane/api-gateway`
2. Open IntelliJ, go to **File -> Open**, select `api-gateway`, and import as a Maven project.
3. Open **View -> Tool Windows -> Maven**, wait for indexing.

# Build Instructions
1. Run **Maven Install** on `service-proxy-annot` (Right-click -> Run Maven -> Install).
2. Run **Maven Install** on `service-proxy-core` and `membrane-api-gateway` (if distribution build is needed).
   - Output: `cli/target/membrane-api-gateway-x.x.x.zip`

# Running the Api Gateway in IntelliJ
1. Create a `proxies.xml` in the `distribution/conf` directory
2. Open `src/main/java/com/predic8/membrane/core/IDEStarter.java`.
3. Right-click -> Run as Java Application.
4. **Note:** Ensure the working directory is set to /distribution.

# Integration & Example Tests
- If using Docker: `docker build .`
- Run Maven build before tests: `mvn clean package -DskipTests`
- Run **ExampleTests** in IntelliJ.

# Updating Dependencies
1. Run: `mvn site`
2. Update dependencies:
   ```sh
   mvn versions:use-latest-versions
   mvn versions:update-properties
   ```
   By default, dependencies are updated to the next latest version available, be it patch, minor or major version.
   If the version target should be limited, use any combination of the following properties:

   * `-DallowIncrementalUpdates=false`
   * `-DallowMinorUpdates=false`
   * `-DallowMajorUpdates=false`

   For more detailed information visit the [versions-maven-plugin](https://www.mojohaus.org/versions/versions-maven-plugin/examples/advancing-dependency-versions.html) reference.
3. To revert changes: `mvn versions:revert`
4. To finalize changes: `mvn versions:commit`
