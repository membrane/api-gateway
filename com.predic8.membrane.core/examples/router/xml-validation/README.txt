XML Validation

For this example to run you should install Curl from http://curl.haxx.se/download.html , if
you have not done so already.

Execute the following steps:

1. Start "router.bat" in examples/xml-validation.

2. Go to the directory examples/xml-validation.

3. Run "curl -d @year.xml http://localhost:2000/". Observe that you get a successful response.

4. Run "curl -d @invalid-year.xml http://localhost:2000/". Observe that you get a validation error response.