# WSDL to OpenAPI

Exposes a SOAP/WSDL service via HTTP/JSON/OpenAPI using the `wsdl2openapi` interceptor.

## How It Works

Operations from the WSDL are exposed as HTTP POST endpoints. Operation names are converted from camelCase to kebab-case paths:

```
getCity  →  POST /cities/get-city
```

JSON requests are automatically converted to SOAP envelopes and SOAP responses are converted back to JSON. An OpenAPI 3.0 spec is generated from the WSDL.

**This is not REST.** It's OpenAPI as Remote Procedure Call — operations map to POST endpoints, not REST resources.

## Run

```
./membrane.sh
```

## Try It

Open Swagger UI in the browser:
```
http://localhost:2000/cities/api-docs
```

Or fetch the raw OpenAPI spec:
```
curl http://localhost:2000/cities/api-docs/spec.yaml
```

Call an operation:
```
curl -X POST http://localhost:2000/cities/get-city \
  -H "Content-Type: application/json" \
  -d '{"name": "Berlin"}'
```

Expected response:
```json
{
  "country": "Germany",
  "population": 3520031
}
```

## Configuration

```yaml
api:
  port: 2000
  path: /cities
  flow:
    - wsdl2openapi:
        wsdl: city.wsdl
        operations:
          - operation:
              name: getCity
  target:
    url: http://localhost:2001
```

### Request Flow

1. Client sends JSON to `POST /cities/get-city`
2. Interceptor wraps it in a SOAP envelope and forwards to the backend
3. Backend returns a SOAP response
4. Interceptor extracts the body and returns it as JSON

```
POST /cities/get-city          POST /services/cities
{"name": "Berlin"}    →   soap:Envelope > soap:Body > getCity > name=Berlin
                      ←   soap:Envelope > soap:Body > getCityResponse
{"country":"Germany"} ←
```
