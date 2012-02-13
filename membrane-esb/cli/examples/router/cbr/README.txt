SWITCH INTERCEPTOR

Using the switch interceptor Membrane allows you to route XML messages based on XPath expressions.



RUNNING THE EXAMPLE

In this example we will route three XML messages based on their content to three different destinations. 

To run the example execute the following steps: 

Execute the following steps:

1. Execute examples/cbr/router.bat

   To test the router we will use the command line tool curl that can transfer data with URL syntax. You can download it form the following location:
   
     
   http://curl.haxx.se/download.html
      
   
2. Execute examples/curl -d @order.xml localhost/shop

3. Take a look at the output of the console. You should see the line:

"Normal order received."

4. Execute examples/curl -d @express.xml localhost/shop

5. This time the following line should be printed to the console:

"Express order received."

6. Execute examples/curl -d @import.xml localhost/shop

7. The final output should look like this:

"Order contains import items." 	



ON LINUX

On Linux, you need to be root to open network ports <1024. Therefore, to start
this example, you either need to start the membrane-esb as root, or specify
a port >=1024, e.g. using '<serviceProxy port="8080">' (on every occurance of
'<serviceProxy>') in cbr.proxies.xml .
