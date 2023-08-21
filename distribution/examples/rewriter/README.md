# REGEX URL Rewriter

With the plugin you can rewrite URLs by regular expressions. 


## Running the example

In this example we will rewrite a simple URL. Take a look at the URL:

https://api.predic8.de/shop/v2/products/

We want to access this API with the path `store/products/`. To do this we have to replace the part `/store/` from the context path with `/shop/v2`. We can achieve this by using the rewriter plugin as follows:

1. Go to the `examples/rewriter` directory.

2. Execute `service-proxy.bat`

3. Open the URL http://localhost:2000/store/products/ in your browser.


---
See:
- [rewriter](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/rewriter.htm) reference