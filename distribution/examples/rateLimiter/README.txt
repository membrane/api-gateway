RATE LIMIT INTERCEPTOR

With the RateLimitInterceptor you can limit the number of requests in a timeframe



RUNNING THE EXAMPLE

In this example we will send 4 requests to the service with a rate limit of 3 requests per 10 seconds. The first 3 requests will go through while the last one will be blocked with code 429. NOTICE: The interceptor runs in Lazy mode so you can run into a situation where you can do 6 requests and the 7th is blocked ( see below for details ).

1. Go to the examples/rateLimiter directory.

2. Execute service-proxy.bat

3. Open curl in the command line

4. Repeat 4 times within 10 seconds: Request the header of localhost:2000 with "curl localhost:2000 -I"



HOW IT IS DONE

This section describes the example in detail.  

First take a look at the proxies.xml file.

	<router>
		<serviceProxy port="2000">
			<rateLimiter requestLimit="3" requestLimitDuration="0:0:10:0" mode="LAZY" />
			<target host="www.google.de" port="80" />
		</serviceProxy>
	</router>

You will see that there is a serviceProxy on port 2000. Additionally the RateLimitInterceptor is added to the proxy and configured to 3 requests per 10 seconds in lazy mode.

The rateLimiter element has 3 values that you can set and by default it is set to 1000 requests per 1 hour in lazy mode.

requestLimit="x" can be any positive number. Specifies the number of requests per interval.
requestLimitDuration="H:m:s:ms" can be any Duration. Specifies the interval in which requestLimit requests can be done. Format is "H:m:s:ms" with H = hour, m = minute, s = seconds, ms = milliseconds
mode="mode" can be "PRECISE" or "LAZY". Specifies the mode the interceptor is working in.

Precise mode:
When a request is received the interceptor stores the time of the request for the requester ip. As a result of this all request times for a specific ip are available and a precise rate limiting is possible. This basically means that a request time is deleted after requestTime + rateLimitDuration time and requests when there are more than requestLimit request times.
Example: Server is set to 1000 requests per hour in precise mode. Alice sends her first request at 08:34 AM and instantly 6000 more afterwards ( similiar to a DDoS attack ). The precise mode will block further requests after the 1000th request of Alice and responds that the next request is possible at 09:34 AM.

Lazy mode:
When the interceptor starts up it stores the time of its start up and resets its internal request counter storage after requestLimitDuration time. When a request is received the interceptor increments a counter for the ip. As a result of this one can send up to 2*requestLimit requests within the requestLimitDuration.
Example: Server is set to 1000 requests per 2 minutes in lazy mode. Interceptor starts at 08:58 AM, Alice sends 1000 requests at 08:59 AM, interceptor resets at 09:00 AM and Alice sends another 1000 requests at 09:00 AM. Here alice sent 2000 requests in a timeframe of 1 minute ( 08:59 AM - 09:00 AM ) even though the requestLimit was 1000.

When to use which mode?
Precise mode uses more memory but enforces the rate limit strictly.
Lazy mode uses less memory and is faster but is more relaxed with the rate limit.
 
