### XML Schema Validation

To run this example you should install Curl from http://curl.haxx.se/download.html , if
you have not done so already. Let us assume it is in your PATH.

Execute the following steps:

1. Go to the directory `examples/validation/xml`.

2. Start `service-proxy.bat` or `service-proxy.sh`.

3. Run `curl -d @year.xml http://localhost:2000/`. Observe that you get a successful response.

4. Run `curl -d @invalid-year.xml http://localhost:2000/`. Observe that you get a validation error response.
