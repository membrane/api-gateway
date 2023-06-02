# Access and Manipulate Messages with Javascript - Example

Javascript is a powerful tool to manipulate messages and to change the behaviour of an API. Using the `javascript` plugin you can use Javascript within API definitions.

**Hint:** These examples require Membrane version 5.1.0 or newer.

## Running the example

1. Take a look at [proxies.xml](proxies.xml). There you'll find the APIs with the `javascript`plugin.
2. Open a commandline session or a terminal.
3. Run `service-proxy.bat` or `./service-proxy.sh` in this folder
4. Open a secound terminal and run the commands:

   **Create JSON with Javascript:**

   ```json
   curl localhost:2000
   {"id":7,"city":"Berlin"}
   ```

   **Transform JSON to JSON:**

   Have a look at the [order.json](order.json) file, then send it to the API to transform the JSON into a different JSON format: 

   ```json
   curl -d @order.json -H "Content-Type: application/json"  http://localhost:2010
   {
       "id": 731,
       "date": "2023-04-07",
       "client": 17,
       "total": 38.35,
       "positions": [
           {
               "pieces": 5,
               "price": 5.9,
               "article": "Oolong"
           },
           {
               "pieces": 2,
               "price": 2.95,
               "article": "Assam"
           },
           {
               "pieces": 1,
               "price": 2.95,
               "article": "Darjeeling"
           }
       ]
   }
   ```

    **Access HTTP Headers and a Spring Bean:**

   ```shell
   ❯ curl http://localhost:2020 -v
   > GET / HTTP/1.1
   > Host: localhost:2020

   < HTTP/1.1 200 Ok
   < Content-Length: 21
   < X-Groovy: 42
 
   Greatings from Spring       
   ```

   Then take a look at the output of the `service-proxy.sh/bat` script. You should see the output from the script, printing the request header fields.


   ```
   Request headers:
   Host: localhost:2000
   User-Agent: curl/7.79.1
   Accept: */*
   X-Forwarded-For: 127.0.0.1
   X-Forwarded-Proto: http
   X-Forwarded-Host: localhost:2000
   ```

See: 
- [javascript plugin](https://www.membrane-soa.org/service-proxy-doc/current/configuration/reference/javascript.htm) reference







