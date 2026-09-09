# SOAP/REST Converter Tutorial

Transform requests and responses between REST/JSON and SOAP/XML by hand, using templates and
Groovy scripts. For automatic WSDL-driven conversion instead, see the
[WSDL to OpenAPI tutorial](../wsdl-to-openapi).

Each step is explained directly in the configuration file, which is also the Membrane config you
run. If possible, use an editor with YAML support such as Visual Studio Code or IntelliJ IDEA.

The tutorials build on each other, from simple to advanced:

1. [10-REST-GET-to-SOAP.yaml](10-REST-GET-to-SOAP.yaml) — expose a SOAP web service as a REST
   style GET endpoint; a path parameter is inserted into the SOAP request and the response is
   mapped back to JSON.
2. [20-JSON-Array-to-SOAP.yaml](20-JSON-Array-to-SOAP.yaml) — render a JSON array as a repeated
   XML element in a SOAP response.
3. [30-SOAP-Array-to-JSON.yaml](30-SOAP-Array-to-JSON.yaml) — the reverse: turn a SOAP body with
   a list of elements into a JSON array, using XPath in a template.
4. [40-SOAP-to-REST-Groovy.yaml](40-SOAP-to-REST-Groovy.yaml) — the same SOAP-to-JSON conversion,
   this time with a Groovy script instead of a template.
5. [50-SOAP-Fault-Groovy.yaml](50-SOAP-Fault-Groovy.yaml) — a full round trip (JSON to SOAP to
   JSON) with external Groovy scripts, including SOAP fault handling.

## Next Steps

Start with [10-REST-GET-to-SOAP.yaml](10-REST-GET-to-SOAP.yaml) and follow the
instructions in the file.
