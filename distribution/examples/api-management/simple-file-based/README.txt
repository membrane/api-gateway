SIMPLE FILE BASED API MANAGEMENT

The API Management defines keys and policies to control access to an API. Policies and keys are defined in a yaml file.
This is the simplest way to use API Management with Membrane.

RUNNING THE EXAMPLE

In this example we will send requests to a service with and without an API key. Complete the following steps:

1. download and install cURL ( https://curl.haxx.se/download.html )

2. run the service-proxy.bat/.sh

3. open the command line

4. run: curl -s -i -D - -o /dev/null localhost:8080

5. observe an HTTP 401 unauthorized response

6. run: curl -s -i -D - -o /dev/null -H "Authorization: abcdefg" localhost:8080

7. observe an HTTP 200 response

Please follow to http://membrane-soa.org/FILL_SOMETHING_USEFUL_IN_HERE_WHEN_AVAILABLE/ for a detailed explanation.