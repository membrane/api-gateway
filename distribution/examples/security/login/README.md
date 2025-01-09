### LOGIN INTERCEPTOR

Using the `LoginInterceptor`, Membrane API Gateway can log requests and responses.


#### RUNNING THE EXAMPLE

In this example we will visit a website and take a look at the logs in the console. 

To run the example execute the following steps:

1. Go to the `examples/login` directory.

2. Execute `service-proxy.bat`

3. Open the URL http://localhost:2000/ in your browser.

4. Enter `john` as username and `password` as password.

5. Install the "Google Authenticator" app on your Android phone. Launch the app.

6. Select "Set up account" from the menu. Choose "Enter provided key".

7. Enter `test@local` as name and `abcdefghijklmnop` as key (the same key specified
   for John in the examples/login/proxies.xml). Select "Time based" authentication and click "Add".
   
8. Enter the numeric token shown by the app into the web form and click "Verify". Be
   quick, as the token frequently changes as indicated by the animation within the app.

9. You are now "logged in" and Membrane forwards your requests to the server specified
   in `examples/login/proxies.xml` .
   
10. Open http://localhost:2000/login/logout in your browser.

11. You are logged out again.

--- 
See
- [login](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/login.htm) reference