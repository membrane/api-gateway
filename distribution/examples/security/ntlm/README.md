# NTLM Authentication for APIs

In this example we are going to see how NTLM authentication is handled with Membrane API Gateway.


## Setup
1. Setup IIS server on the Windows machine.
    1. Let the IIS server listen on port `8111`.
    2. Configure the IIS server to use NTLM authentication.
2. Have cURL or another HTTP client ready (e.g. insomnia - https://insomnia.rest/download/).
3. (optional) Configure additional Windows credentials for usage with this example.


## Running the Example

To run the example execute the following steps:

1. Start Membrane API Gateway proxy by running the `service-proxy.[bat|sh]` in this folder.
   
2. Look at the console window and wait until `'Membrane ... up and running!'`. This window will remain open
   
3. Call http://localhost from your HTTP client (example - `curl -v localhost`).
   
4. Observe a `401 Unauthorized` error message.
   
5. Call http://localhost again but set the `X-Username` and `X-Password` headers to your Windows credentials (example - `curl -v -H "X-Username: $USERNAME" -H "X-Password: $PASSWORD" localhost`).
   
4. Observe a '200 Ok' success message.



## How it is done

Have a look at the configuration in the `proxies.xml` of this example.
```
<router>
  <serviceProxy port="80">
    <ntlm user="X-Username" pass="X-Password" />
    <target host="localhost" port="8111"/>
  </serviceProxy>
</router>
```
* Membrane is configured as a simple virtual endpoint listening on port `80`.
  
* When connecting the request is routed through the NTLM interceptor to start the authentication process.
  
* For that the windows credentials of a valid user are needed.
  
* The NTLM interceptor (by default) fetches those from custom headers - here called `X-Username` and `X-Password`.
  
* When the authentication process has finished the original call is routed to the target server specified in the target element.

The here given configuration for NTLM can be extended to encompass all four NTLM parameters.  
The following items are a list of attributes that map an NTLM parameter to a custom header for usage by the NTLM interceptor.

* user - (Windows username)
* pass - (Corresponding password for username
* domain - (Domain the Windows server is residing in)
* workstation - (Workstation the user is assigned to)

---
See:
- [ntlm](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/ntlm.htm) reference
