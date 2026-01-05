# Logging Requests and Responses into a JDBC Database

Membrane can log metadata about service invocations to any database that can be accessed via JDBC.

## Running the Example

In this example we configure Membrane to log to an embedded **H2** database. For production you can use any other JDBC database like postgres or mysql.


1. [Download the H2 JDBC driver JAR](https://www.h2database.com/html/download.html).

2. Copy the `h2-*.jar` into the `MEMBRANE_HOME/lib` directory

3. Go to the `examples/logging/jdbc-database` directory.

4. Start Membrane using `membrane.sh` or `membrane.cmd`. 

5. Open the URL http://localhost:2000/ in your browser (trigger a request).

6. Stop Membrane to shut down the embedded H2 database. Otherwise, it is not possible to connect to it from the H2 Shell.

7. Query the database using the H2 Shell (run this from `examples/logging/jdbc-database`):
   ```shell
   java -cp h2-2.4.240.jar org.h2.tools.Shell -url "jdbc:h2:./membranedb" -user membrane -password secret
   ```

8. In the SQL prompt, list tables and query the statistic table:
    ```sql
    select * from statistic;
   ```

---
See:
- [statisticsJDBC](https://membrane-api.io/docs/current/statisticsJDBC.html) reference