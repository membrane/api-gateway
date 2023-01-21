# Logging Requests and Responses to CSV Files 

Membrane can log the access to APIs to a CSV file. The fields will be separated by semicolon `;`, so that you can import the data into Excel.


## Running the Example

To run the example execute the following steps:

1. Go to the `examples/logging-csv` directory.
2. Run `service-proxy.sh` or `service-proxy.bat`
4. Route a request through Membrane:

    ```
    curl localhost:2000
    ```

5. Open the newly created `log.csv` file and take a look at the logged data.

    ```
    Status Code;Time;Rule;Method;Path;Client;Server;Request Content-Type;Request Content Length;Response Content-Type;Response Content Length;Duration;
    200;2022-12-21 11:00:34.882;:2000;GET;/;localhost;api.predic8.de;;unknown;application/json;336;32;
    ```

6. Hava a look at the `proxies.xml` file:

    ```
    <serviceProxy port="2000">
        <statisticsCSV file="./log.csv" />
        <target host="api.predic8.de">
            <ssl/>
        </target>
    </serviceProxy>
    ```

