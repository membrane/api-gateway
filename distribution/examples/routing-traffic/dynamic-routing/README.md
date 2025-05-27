# Dynamic Routing using Target Expressions

Using expressions in target URLs allows you to dynamically route requests based on path parameters, headers, or other request attributes.

## Running the Example

In this example we will route three XML messages based on their content to three different destinations.

To run the example execute the following steps:

1. Go to the folder `examples/routing-traffic/dynamic-routing`

2. Start Membrane:

`membrane.sh` or 
`membrane.cmd`

3. Access the Fruit Shop API through the alternate URL:

```sh
curl localhost:2000/market/products
```

4. Or try accessing different API endpoints:

```sh
curl localhost:2000/market/vendors
curl localhost:2000/market/customers
curl localhost:2000/market/orders
```

Each request will be dynamically routed to the corresponding endpoint at `https://api.predic8.de/shop/v2/[endpoint]` based on the path parameter you provide.

## Configuration

```xml
<api port="2000" name="Router">
  <path>/market/{page}</path>
  <target url="https://api.predic8.de/shop/v2/${pathParams.page}" />
</api>
```

This configuration captures the `page` path parameter and dynamically inserts it into the target URL using the expression `${pathParams.page}`.