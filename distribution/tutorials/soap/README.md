# Membrane API Gateway Tutorial - SOAP Web Services

This tutorial shows how to use the Membrane Service Proxy to integrate with legacy SOAP web services and how to expose APIs as SOAP-based services.

Start by looking at [10-Sample-SOAP-Service.yaml](10-Sample-SOAP-Service.yaml).

For exposing a SOAP/WSDL service as a REST/OpenAPI API, see
[95-WSDL-to-OpenAPI.yaml](95-WSDL-to-OpenAPI.yaml) (automatic conversion) and
[96-WSDL-to-OpenAPI-REST.yaml](96-WSDL-to-OpenAPI-REST.yaml) (manual per-operation REST mapping).

[97-WSDL-XSD-Features.yaml](97-WSDL-XSD-Features.yaml) is a reference: one operation per XSD
construct the converter maps, showing what each becomes in the OpenAPI and in the JSON.

[98-WSDL-Faults.yaml](98-WSDL-Faults.yaml) shows what a SOAP fault becomes for a JSON client:
a problem details document carrying the declared fault's content.

