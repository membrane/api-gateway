Schematron Validation

For this example to run you should install Curl from http://curl.haxx.se/download.html , if
you have not done so already.

Execute the following steps:

1. Go to examples/validation/schematron and start "router.bat".

2. Look at car-schematron.xml and compare the schema to car.xml and invalid-car.xml .

3. Run "curl -d @car.xml http://localhost:2000/" from the command line. Observe that you get a successful response.

4. Run "curl -d @invalid-car.xml http://localhost:2000/". Observe that you get a validation error response.





Resources:
  http://xml.ascc.net/resource/schematron/Schematron2000.html