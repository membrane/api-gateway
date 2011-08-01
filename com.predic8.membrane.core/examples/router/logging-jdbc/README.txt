STATISTICS JDBC INTERCEPTOR

Using the StatisticsJDBCInterceptor, Membrane Monitor and Router can log metadata about service invocation to any database that can be accessed via JDBC.
Each entry in the database will contain:

    HTTP status code
    Request time
    The rule that processed the request
    HTTP method used
    Request path as specified by rfc2616
    Client (see rule key)
    Server, the request target
    Content-Type header of the request
    Content-Length headers of the request
    Content-Type header of the response
    Content-Length headers of the response
    Request/response cycle duration
    
RUNNING THE EXAMPLE

In this example we will visit a web site and take a look at the logs that have been created. 

To run the example execute the following steps:

1. Go to the examples/logging-jdbc directory.

2. Execute router.bat

3. Notice the file log.csv that is created in the logging-csv directory.

4. Open the URL http://localhost:2000/soap-monitor-doc/csv-logging.htm in your browser.

5. Open the file log.csv and take a look at the logged data.
