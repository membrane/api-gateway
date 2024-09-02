# Traffic Shadowing

This example demonstrates the use of the `custom strategy`. The custom strategy evaluates a groovy snippet every time an exchange finishes.
Running exchanges are available through list `running` and completed exchanges through `completed`, the current exchange can be accessed through `current`. 
The return value of the snippet should be the final response which terminates the plugin operation, but keeps currently running exchanges still alive. 
Returning null will simply skip ahead to the next completion event. The map `vars` can be used to keep track of values between related calls of the script.

## Running the Example

1. Run `service-proxy.bat` or `service-proxy.sh`
2. Open [localhost:2000](http://localhost:2000) in your browser or use `curl`:

   ```                                                                                                    
   curl -v http://localhost:2000
   ```

   The output should look like this:
   
   ```
   Number 1
   ```
   
   The groovy snippet checked for every completed exchange, if the value of the header field `x-value` is smaller than the previously smallest.
   If it was smaller, it sets the exchange and the smallest integer as the value of the variable `vars["min_value_exchange"]` and `vars["min_value"]`.
   Once all exchanges returned, we terminate the parallel plugin by returning the stored exchange with the smallest `x-value` header.

3. Change the API on port `3000` to the following configuration:
   
   ```xml
   <api port="3000">
      <setHeader name="x-value" value="4" />
      <static>
          Number 4
      </static>
      <return />
   </api>
   ```
   
   Now if you repeat step 2, the resulting response body will be:
   
   ```
   Number 2
   ```
   
   This is because we replaced the smallest number (1) with 4, which in return makes number 2 the smallest.

## Configuration

Simply use the \<custom> tag and insert the groovy snippet as text content of the element. Now the expression will be used as parallel strategy.

```xml
<api port="2000">
   <log />
   <parallel>
      <custom>
         
      </custom>
      <target url="http://localhost:3000" />
      <target url="http://localhost:4000" />
      <target url="http://localhost:5000" />
   </parallel>
</api>

<api port="3000">
   <setHeader name="x-value" value="1" />
   <static>
      Number 1
   </static>
   <return />
</api>
...
```