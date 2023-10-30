# Protecting an API with OAuth2 - Resource Owner Password Flow

This example shows the OAuth2 password flow. A client requests an access token ( with user credentials ) and the token is verified through a token validator.


## Running the example

### Setup

1. Go to the [Postman](https://www.postman.com/downloads/) website and install the latest desktop client for your OS.
2. Open the Postman app and drag `environment.json` and `requests.json` into the menu bar to the right.
3. Click on the `environments` tab and tick the checkmark on the `oauth2 environment`.
4. Click on the `collections` tab and open the `oauth2 example requests` folder.

### Sending the Requests

1. Click on the `POST` request and press `Send`. This will request an oauth token from the authorization server using the username `john` and password `password`.
2. Click on the `GET` request and send it. Now the previously acquired oauth token will be set in the `Authorization` header using the `Bearer` keyword to authenticate with the token validator.

See:
- [oauth2authserver](https://www.membrane-soa.org/api-gateway-doc/5.2/configuration/reference/oauth2authserver.htm) reference
- [tokenValidator](https://www.membrane-soa.org/api-gateway-doc/5.2/configuration/reference/tokenValidator.htm) reference