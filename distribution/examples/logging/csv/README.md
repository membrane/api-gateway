### STATISTICS CSV INTERCEPTOR

Using the `StatisticsCSVInterceptor`, Membrane Monitor and Service Proxy can log access data to a file. The fields will be separated by semicolon `;`, so that you can import the data into Excel.


#### RUNNING THE EXAMPLE

In this example we will visit a website and take a look at the logs that have been created. 

To run the example execute the following steps:

1. Go to the `examples/logging-csv` directory.

2. Execute `service-proxy.bat`

3. Notice the file `log.csv` that is created in the `logging-csv` directory.

4. Open the URL http://localhost:2000/soap-monitor-doc/csv-logging.htm in your browser.

5. Open the file `log.csv` and take a look at the logged data.

---
See:
- [statisticsCSV](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/statisticsCSV.htm) reference