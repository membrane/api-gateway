# Logging Requests and Responses into a JDBC Database

Membrane can log metadata about service invocations to any database that can be accessed via JDBC.

## Running the Example

In this example we configure Membrane to log to an embedded **H2** database.


1. [Download the H2 JDBC driver JAR](https://search.maven.org/remotecontent?filepath=com/h2database/h2/2.4.240/h2-2.4.240.jar).

2. Copy the `h2-*.jar` into the `MEMBRANE_HOME/lib` directory

3. Go to the `examples/logging/jdbc-database` directory.

4. Start Membrane using `membrane.sh` or `membrane.cmd`. 

5. Open the URL http://localhost:2000/ in your browser (trigger a request).

6. Query the database using the H2 Shell (run this from `examples/logging/jdbc-database`):
    ```shell
   java -cp ../../../lib/h2-*.jar org.h2.tools.Shell \
    -url "jdbc:h2:./membranedb;AUTO_SERVER=TRUE" \
    -user membrane -password membranemembrane
   ```

7. In the SQL prompt, list tables and query the statistic table:
    ```sql
    select * from statistic;
   ```

---
See:
- [statisticsJDBC](https://membrane-api.io/docs/current/statisticsJDBC.html) reference