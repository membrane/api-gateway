# JSON 2 JSON Message Transformation with Javascript

Transforming JSON into documents with a different format. Using Javascript you can set up simple but also very powerful transformations. 


## Running the Samples

Execute the following steps in the `$MEMBRANE_HOME/examples/message-transformation/json2json-with-javascript` folder:

1. Run `service-proxy.sh` or  `service-proxy.bat` to start the API Gateway.

2. Execute the following curls:

```
curl http://localhost:2000/flight -H 'Content-Type: application/json' -d '{"from": "Berlin","to": "London"}'
```

```
curl "localhost:2000/search?limit=10&page=2" -v
```

```
curl http://localhost:2000/orders -H 'Content-Type: application/json' -d @order.json
```

3. Compare the input documents with the output.
4. Have a look at the APIs in the `proxies.xml` file.
5. Modify the samples to realize your own transformation.

#### NOTES

To see the implementations of the samples take a look at the `proxies.xml`.

The underlying Javascript engine is `Rhino`. For further reference see http://mozilla.github.io/rhino/.

You can exchange Rhino with the newer Javascript engine from the GraalVM. See https://membrane-api.io/plugins/javascript.html

---
See:
- [javascript](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/javascript.htm) reference