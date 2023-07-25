# Throttle Plugin

This sample demonstrates how to throttle requests to an API or web app using the `throttle` plugin.


## Running the Example

Execute the following steps:

1. Set sample directory (`<membrane-root>/examples/throttle/`) as working directory.

2. Execute the `service-proxy.sh` script or its Windows batch file equivalent.

3. Run the `timing.sh` script or its Windows batch file equivalent.

4. Observe as Curl carries out five requests each, initially without any throttling and subsequently with it.

5. Check the recorded times, with throttling enabled, every request will take an additional second.


## How it is done

Let's check the elements inside the `router` component in the `proxies.xml` file:

```xml
<api port="2000">
  <throttle delay="1000" />
  <target url="https://predic8.de" />
</api>

<api port="3000">
  <target url="https://predic8.de" />
</api>
```

Observe the two api components, which respectively proxy requests from the localhost ports 2000 and 3000 to the predic8 homepage.
The former api component additionally configures the throttle plugin for a delay of 1 second.

Let's examine the commands executed by the timing script:
```sh
#!/bin/bash

echo "No throttling applied:"
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:3000
# ... (x5)
echo "With throttling enabled:"
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:2000
# ... (x5)
```

It simply executes a curl request to the two Membrane addresses, five times each, though discarding the response body and displaying the total response time instead.

---
See:
- [throttle](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/throttle.htm) reference