POST /axis2/services/BLZService HTTP/1.1
Content-Type: application/soap+xml; charset=UTF-8; action="http://thomas-bayer.com/blz/BLZServicePortType/getBankRequest"
User-Agent: Axis2
Host: localhost:7000
Transfer-Encoding: chunked

ff
<?xml version='1.0' encoding='UTF-8'?><soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope"><soapenv:Body><ns1:getBank xmlns:ns1="http://thomas-bayer.com/blz/"><ns1:blz>66762332</ns1:blz></ns1:getBank></soapenv:Body></soapenv:Envelope>
0

