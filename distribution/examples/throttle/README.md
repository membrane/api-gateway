# Throttle Plugin

This sample demonstrates how to throttle requests to an API or web app using the `throttle` plugin.


## Running the Example

Execute the following steps:

1. Set sample directory (`<membrane-root>/examples/throttle/`) as working directory.

2. Execute `service-proxy.sh` or the windows `.bat` equivalent.

3. Execute `timing.sh` or the windows `.bat` equivalent.

4. Watch curl perform 5 requests each, once without throttling and once with.

5. Check the recorded times, with throttling enabled every request will take an additional second.


## How it is done

Let's check the elements inside the `router` component inside the `proxies.xml` file:

```xml
<api port="2000">
  <throttle delay="1000" />
  <target url="https://predic8.de" />
</api>

<api port="3000">
  <target url="https://predic8.de" />
</api>
```

Notice two `api` components, these proxy requests from localhost port `2000` and `3000` respectively, targeting the predic8 homepage.
The upper `api` component additionally has the `throttle` plugin configured for a 1 second delay.

Now looking at what the timing script executes:
```sh
#!/bin/bash

echo "No throttling applied:"
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:3000
# ... (x5)
echo "With throttling enabled:"
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:2000
# ... (x5)
```

We simply perform a curl request to the two membrane addresses, each 5 times, but discarding the response body and instead showing the time the response took.

---
See:
- [throttle](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/throttle.htm) reference