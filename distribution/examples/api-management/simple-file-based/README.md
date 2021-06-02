### SIMPLE, FILE BASED API MANAGEMENT

The API Management defines keys and policies to control access to an API. Policies and keys are defined in a yaml file.
This is the simplest way to use API Management with Membrane.


#### RUNNING THE EXAMPLE

In this example we will send requests to a service with and without an API key. Complete the following steps:

1. Download and install cURL ( https://curl.haxx.se/download.html )

2. Run the `service-proxy.bat` or `service-proxy.sh`

3. Open terminal and run below command

```
curl -s -i -D - -o /dev/null localhost:8080
```

4. Observe an `HTTP 401` unauthorized response

5. Run below command

```
curl -s -i -D - -o /dev/null -H "Authorization: abcdefg" localhost:8080
```

6. Observe an `HTTP 200` response

Please follow to http://membrane-soa.org/FILL_SOMETHING_USEFUL_IN_HERE_WHEN_AVAILABLE/ for a detailed explanation.