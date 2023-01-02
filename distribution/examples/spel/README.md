# Configuration using Spring Expression Language

Using the Spring Expression language as a part of router configuration.
Documentation of Spring Expression language could be found at 
http://docs.spring.io/spring/docs/3.1.x/spring-framework-reference/html/expressions.html


## Running the example

In this example we will visit a website and take a look at the logs in the console. 

To run the example execute the following steps:

1. Go to the `examples/spel` directory.

2. Look at file `proxies.xml` ( maybe change some properties )

3. Execute `service-proxy.bat`

4. Open the URL [http://localhost:${LISTEN_PORT}/](http://localhost:${LISTEN_PORT}/) in your browser. Where LISTEN_PORT is value defined in `proxies.xml`


### Troubleshooting:

If Membrane does not generate any output after loading a URL, it is possible that your browser has already cached the resource. 

Shortcuts for the different browsers:

| Browser       | Shortcut       | 
| ------------- |:-------------:| 
| Safari     | ALT + click reload or CMD + SHIFT + R while using a mac|
| Firefox      | SHIFT + click reload      | 
| Chrome | SHIFT + F5 or CONTROL + F5      |
| IE | CONTROL + F5      |
| Edge | CONTROL + click reload |
