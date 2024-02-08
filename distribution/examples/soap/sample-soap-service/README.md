# Sample Soap Service

This guide demonstrates how to use the Sample SOAP Service.

## Running the Sample 

1. Go to the `examples/soap/sampleSoapService` directory.
2. Run `service-proxy.sh` or `service-proxy.bat to start the API Gateway.
3. You can now access the service at http://localhost:2000.

## Using the Service 
### Get the WSDL
To obtain the Web Service Description Language (WSDL) file for this service, use the following command:
`curl --request GET 'http://localhost:2000?wsdl'`

### Get City Information
You can retrieve information about a city by making a POST request to the service. Use the following curl command as an example:
```
curl --location --request POST 'http://localhost:2000' \
--header 'Content-Type: application/xml' \
--data-raw '
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