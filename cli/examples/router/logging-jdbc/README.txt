STATISTICS JDBC INTERCEPTOR

Using the StatisticsJDBCInterceptor, Membrane Monitor and ESB can log metadata about service invocation to any database that can be accessed via JDBC.
    
RUNNING THE EXAMPLE

In this example we will configure Membrane to log to the Apache Derby database. 

To run the example execute the following steps:

1. First we need a JDBC compliant database. For this example we choose Apache Derby.

2. Download the latest Derby distribution from the following URL:

http://db.apache.org/derby/derby_downloads.html

3. Extract the zip file and copy the DERBY_HOME/lib/derbyclient.jar into the MEMBRANE_HOME/lib directory.

4. Go to the examples/logging-jdbc directory.

5. Start the network server using the following command:

start DERBY_HOME/bin/startNetworkServer.bat

6. Execute router.bat

7. Open the URL http://localhost:2000/soap-monitor-doc/csv-logging.htm in your browser.

8. Start the ij program to query the database:

DERBY_HOME/bin/ij.bat

9. Connect to the database using the the following command:

connect 'jdbc:derby://localhost:1527/membranedb';

10. Execute the following SQL query:

select * from membrane.statistic;