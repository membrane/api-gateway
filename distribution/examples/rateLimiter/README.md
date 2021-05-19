###RATE LIMITER

The `RateLimiter` limits the number of requests in a given interval.



####RUNNING THE EXAMPLE

In this example we will send 4 requests to the service with a rate limit of 3 requests per 30 seconds. The first 3 requests will go through while the last one will be blocked with code `429`. NOTICE: The RateLimiter limits in a given interval and then resets the interval. That means you could send 3 requests, the interval is reset, you send the fourth request and the RateLimiter doesn't block because it is in a new interval. Just repeat the requests if this happens. 

1. Get curl from https://curl.se/ and install it

2. Go to the `examples/rateLimiter` directory.

3. Execute `service-proxy.bat`

4. Run the command line

5. Repeat 4 times within 30 seconds: Request the header of localhost:2000 with `curl localhost:2000 -I`



####HOW IT IS DONE

This section describes the example in detail.  

First take a look at the proxies.xml file.
```
	<router>
		<serviceProxy port="2000">
			<rateLimiter requestLimit="3" requestLimitDuration="PT30S"/>
			<target host="www.google.de" port="80" />
		</serviceProxy>
	</router>
```
You will see that there is a `serviceProxy` on port `2000`. Additionally, the `RateLimiter` is added to the proxy and configured to 3 requests per 30 seconds.

The rateLimiter element has 2 values that you can set and by default it is set to 1000 requests per hour.

`requestLimit="x"` can be any positive number. Specifies the number of requests per interval.
`requestLimitDuration="PTxS"` can be any duration in seconds. Specifies the interval in which `requestLimit` requests can be done. Format is "PTxS" where x is the duration in seconds.
 
