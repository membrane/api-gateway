# Sample Soap Service: CitiesService

This plugin offers a sample SOAP service that can be used in tutorials and for testing purposes.

## Starting the Service

1. Go to the `examples/soap/sample-soap-service` directory.
2. Run `service-proxy.sh` or `service-proxy.bat` to start the API Gateway.

## Using the Service 
### Get the WSDL

Unter `http://localhost:2000?wsdl` you can retrieve the Web Service Description Language (WSDL).

`curl http://localhost:2000?wsdl`

### Get City Information
You can retrieve information about a city using the following curl:
```
curl http://localhost:2000 \
-H 'Content-Type: text/xml' \
-d '
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/city-service">
  <s:Body>
    <cs:getCity>
      <name>Bonn</name>
    </cs:getCity>
  </s:Body>
</s:Envelope>'
``` 
Please note that this is an example service, and it contains data for the following cities: 
Bonn, Bielefeld, Manila, Da Nang, London, and New York. You can replace Bonn` 
in the above request with the city of your choice to retrieve information for a different city.