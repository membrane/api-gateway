# Protecting an API with OAuth2 Using the Resource Owner Password Flow

This step-by-step guide demonstrates how to secure an API using OAuth2 and the Resource Owner Password Flow. A client authenticates using user credentials to request an access token, which is validated via a token validator before accessing the protected resource.

The following images shows the flow of this example.
![OAuth2 Password flow for APIs](oauth2-password-flow-for-apis.png)

## How to Run the Example

This example is located in the `$MEMBRANE_HOME/examples/security/oauth2/api` folder. Follow the instructions below to set up and test the flow:

### 1. Start the Authorization Server

In this example, Membrane acts as a pre-configured OAuth2 authorization server. You can use Membrane as-is or replace it with providers like Azure Entra ID, AWS Cognito, or Keycloak.

Steps to start the authorization server:
1. Open the `authorization_server` subfolder in a terminal.
2. Run `membrane.cmd` or `membrane.sh`.
3. Verify the server is running by visiting [http://localhost:9000/admin](http://localhost:9000/admin).

### 2. Start the Server with the Protected Resource

The `token_validator` folder contains a Membrane setup that validates tokens and protects the resource.

Steps to start the token validator:
1. Open the `token_validator` folder in a terminal.
2. Run `membrane.cmd` or `membrane.sh` there.
3. Verify the resource side is running at [http://localhost:9001/admin](http://localhost:9001/admin).

### 3. Get a Token and Access the Protected Resource

To request a token and access the resource, you can use a command-line client based on `curl` (detailed below). Alternatively, you can use the `rest.http` file or follow the [Postman guide](https://membrane-api.io/security/oauth2-using-postman/).

1. Open a terminal and run the client:

   **Linux**:  
   ```bash
   ./client.sh john password
   ```

   **Windows**:  
   ```cmd
   client.bat john password
   ```

   The `client.sh/bat` is requesting the token in one call to the authorization server and than calling the protected resource with the token in a second call. Both calls are displayed for illustration.

   Example output:
   ```
   1.) Requesting Token
   POST http://localhost:7007/oauth2/token
   grant_type=password&username=john&password=password&client_id=abc&client_secret=def
   
   Got Token: nmsi0fsghcfq3dc9hm064eoq72

   2.) Calling API
   GET http://localhost:2000
   Authorization: Bearer nmsi0fsghcfq3dc9hm064eoq72

   Got: { "success": true }
   ```

   **Note:** Replace `username` and `password` with appropriate credentials.



## How It Works

### Authorization Server

The authorization server authenticates users and issues access tokens.

### Token Validator

The token validator is a Membrane API Gateway instance that verifies access tokens against the authorization server. If valid, requests are forwarded to the protected resource. Invalid tokens result in a `400 Bad Request` response.

The configuration for the token validator is defined in `proxies.xml`:

```xml
<api name="Token Validator" port="2000">
    <tokenValidator endpoint="http://localhost:7007/oauth2/userinfo"/>
    <target host="localhost" port="3000"/>
</api>
```

- **tokenValidator**: Validates the access token.
- **target**: Routes valid requests to the protected resource.

**Important:** Ensure the resource at port 3000 is inaccessible externally in production environments to prevent unauthorized access.

### Trusted Client

The client script authenticates using user credentials to obtain an access token and calls the protected resource with the token in the `Authorization` header.

Have a look at the `client.sh/.bat` script. There you'll find some variables:

- **clientId**: Application client ID.
- **clientSecret**: Corresponding client secret.
- **tokenEndpoint**: OAuth2 token endpoint.
- **target**: Protected resource URL.

Those variables correspond with the configuration of the authentication server.

There are two script versions: Shell and PowerShell. Alternatively, a Java implementation (`OAuth2TrustedClient`) is available in the example folder.

---

## Postman Guide

Prefer using Postman? Follow the detailed guide on [Membrane API OAuth2 with Postman](https://membrane-api.io/security/oauth2-using-postman/).