FILE EXCHANGE STORE

Membrane ESB uses exchange stores to save requests and responses. Using the ExchangeStoreInterceptor you can plugin different exchange stores. Each exchange store implements a different storing strategy. The FileExchangeStore saves exchanges into the file system.
     
RUNNING THE EXAMPLE

In this example we will configure a ExchangeStoreInterceptor with a FileExchangeStore. 

To run the example execute the following steps:

1. Execute examples/file-exchangestore/router.bat

2. Open http://localhost:2000/

3. Take a look at the directory examples/file-exchangestore/exchanges it contains the exchanges of the previous call.
