SSL for Unsecured Servers

Using the Membrane Router we can enable clients that do not support SSL to communicate with a SSL secured server.  




RUNNING THE EXAMPLE

In the example we will connect to the following SVN repository without using SSL.
 
 
https://predic8.com/svn/membrane/monitor/


To run the example execute the following steps:

1. Go to the examples/ssl-tunnel-to-server directory.

2. Execute router.bat

3. Open the URL http://localhost/svn/membrane/monitor/ in your browser.



ON LINUX

On Linux, you need to be root to open network ports <1024. Therefore, to start
this example, you either need to start the membrane-router as root, or specify
a port >=1024, e.g. using '<serviceProxy port="8080">', in
ssl-tunnel-to-server.proxies.xml .
