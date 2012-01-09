Schematron Validation

For this example to run you should install Curl from http://curl.haxx.se/download.html , if
you have not done so already.

Execute the following steps:

1. Start "router.bat" in examples/validation/schematron.

2. Go to the directory examples/validation/schematron.

3. Look at car-schematron.xml, read http://xml.ascc.net/resource/schematron/Schematron2000.html and 
   compare the schema to car.xml and illegal-car.xml .

4. Run "curl -d @car.xml http://localhost:2000/". Observe that you get a successful response.

5. Run "curl -d @illegal-car.xml http://localhost:2000/". Observe that you get a validation error response.
