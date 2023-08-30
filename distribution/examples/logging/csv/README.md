# CSV Statistics 

The `statisticsCSV` plugin logs access data into a file. The fields will be separated by semicolon `;`, so that you can import the data e.g. into Excel.


## Running the example 

In this example we will visit a website and take a look at the logs that have been created. 

To run the example execute the following steps:

1. Go to the `examples/logging-csv` directory.

2. Execute `service-proxy.sh` or service-proxy.bat`

3. Open the URL http://localhost:2000 in your browser. 

4. Look into the file `log.csv` that is created in the `logging-csv` directory. You should see something like:
```
200;2023-08-30 14:51:25.760;:2000;GET;/shop/v2/;ip6-localhost;null;;unknown;application/json;unknown;613;
```
---
See:
- [statisticsCSV](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/statisticsCSV.htm) reference