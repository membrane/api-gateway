# WSDL to OpenAPI Tutorial

Expose a legacy SOAP/WSDL web service as a REST/OpenAPI API. Membrane converts the WSDL into an
OpenAPI document, transforms incoming JSON requests into SOAP XML, and converts SOAP XML
responses (and faults) back to JSON.

Each step is explained directly in the configuration file, which is also the Membrane config you
run. If possible, use an editor with YAML support such as Visual Studio Code or IntelliJ IDEA.

The tutorials build on each other, from simple to advanced:

1. [10-WSDL-to-OpenAPI.yaml](10-WSDL-to-OpenAPI.yaml) — automatic conversion: expose an entire
   WSDL as an API with a single line of config.
2. [20-WSDL-to-OpenAPI-REST.yaml](20-WSDL-to-OpenAPI-REST.yaml) — configure each WSDL operation
   individually and map it to REST endpoints such as `GET /partners/{id}`.
3. [30-WSDL-XSD-Features.yaml](30-WSDL-XSD-Features.yaml) — a reference: one operation per XSD
   construct the converter maps, showing what each becomes in the OpenAPI and in the JSON.
4. [40-WSDL-Faults.yaml](40-WSDL-Faults.yaml) — what a SOAP fault becomes for a JSON client: a
   problem details document carrying the declared fault's content.

## Next Steps

Start with [10-WSDL-to-OpenAPI.yaml](10-WSDL-to-OpenAPI.yaml) and follow the
instructions in the file.
