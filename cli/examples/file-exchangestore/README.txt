FILE EXCHANGE STORE

Membrane Service Proxy uses exchange stores to save requests and responses. Using the ExchangeStoreInterceptor you can plugin different exchange stores. Each exchange store implements a different storing strategy. The FileExchangeStore saves exchanges into the file system.
     
RUNNING THE EXAMPLE

In this example we will configure an ExchangeStoreInterceptor with a FileExchangeStore. 

To run the example execute the following steps:

1. Have a look at file-exchangestore-beans.xml

2. Execute examples/file-exchangestore/service-proxy.bat

3. Open http://localhost:2000/ in your browser.

4. Take a look at the directory examples/file-exchangestore/exchanges it contains the exchanges of the previous call.
