# JWT Token Validation against Microsoft's Azure AD

This tutorial shows how to use Membrane API Gateway to validate Json Web Tokens (JWTs) issued by Microsoft's Azure AD.

Membrane API Gateway protects the Backend API.

![Overview](00-overview.png)

1. The client retrieves the token from Azure AD.
2. The JWT is issued by Azure AD and returned to the client
3. The client then accesses the Backend through Membrane API Gateway.

Membrane API Gateway exposes the Backend e.g. to the hostile internet. Only requests with a valid JWT are allowed to pass.


# Azure AD Setup

Both the Client and the Backend are registered in Azure AD with an "App registration". Don't get confused. ;)

## 1. Register the backend

1. Open https://aad.portal.azure.com/#view/Microsoft_AAD_IAM/ActiveDirectoryMenuBlade/~/Overview 
2. Sign in, if necessary.
3. Take note of your *Tenant ID*. (Format should be ``44xxxxx6-xxxx-xxxx-xxxx-bxxxxxxxxx51``)

4. Open https://aad.portal.azure.com/#view/Microsoft_AAD_IAM/ActiveDirectoryMenuBlade/~/RegisteredApps .
5. Choose ``New registration``.
6. Enter a name (e.g. ``Demo Backend``) and click ``Register``.

   ![Backend Registration](01-backend-registration.png)

7. Next to *Application ID URI*, click on ``Add an Application ID URI``.
8. Next to *Application ID URI*, click on ``Set``.
9. Click on ``Save``.
10. Take note of your *Application ID URI*. (Format should be ``api://2axxxx16-xxxx-xxxx-xxxx-faxxxxxxxxf0``.)
11. Go to *App roles*.
12. Click on *Create app role*.
13. Enter ``ReadWrite`` as *Display name*, *Value* as well as *Description*. 
14. Choose ``Both (Users/Groups + Applications)`` as *Allowed member type**.
15. Click on ``Apply``.

## 2. Get a valid token

There are several possibilities of getting a valid token. For this demo, we use the OAuth 2.0 client credentials flow. (This is an example of the *Access without a user* scenario described on https://learn.microsoft.com/en-us/azure/active-directory/develop/permissions-consent-overview .)

### 2.a. Register your client

1. Open https://aad.portal.azure.com/#view/Microsoft_AAD_IAM/ActiveDirectoryMenuBlade/~/RegisteredApps .
2. Choose ``New registration``.
3. Enter a name (e.g. ``Demo Client``) and click ``Register``.
   ![Client Registration](03-client-registration.png)

4. Take note of your ``Application (client) ID``.
5. Next to *Client credentials*, click on ``Add a certificate or secret``.
6. Click on ``New client secret``.
   ![Creating a new client secret](04-client-secret-creation.png)
7. Enter ``Demo Secret`` as Description.
8. Click on ``Add``.
9. Take note of the ``Value``, also known as the ``client secret``.
10. Click on ``API permissions``.
11. Click on ``Add a permission``.
    ![Adding a permission](05-client-permission-granting.png)
13. Below ``My APIs``, choose ``Demo Backend``.
    ![Adding a permission](06-client-permission-granting2.png)
14. Select the ``ReadWrite`` permission.
    ![Adding a permission](07-client-permission-granting3.png)
15. Click on ``Add permission``.

### 2.b. Get a token

In the following command,

| replace                                      | by                                                 |
|----------------------------------------------|----------------------------------------------------|
| ``7fxxxxx1-xxxx-xxxx-xxxx-6xxxxxxxxx1b``     | the Application (client) ID (see section 2.a.)     |
| ``Dlxxx~xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxc1f`` | the client secret (see section 2.a.)               |
| ``2axxxx16-xxxx-xxxx-xxxx-faxxxxxxxxf0``     | your backend's Application ID URI (see section 1.) |
| ``44xxxxx6-xxxx-xxxx-xxxx-bxxxxxxxxx51``     | the tenant ID (see section 1.)                     |


```
curl -o token.json -d "grant_type=client_credentials&client_id=7fxxxxx1-xxxx-xxxx-xxxx-6xxxxxxxxx1b&client_secret=Dlxxx~xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxc1f&scope=api://2axxxx16-xxxx-xxxx-xxxx-faxxxxxxxxf0/.default" "https://login.microsoftonline.com/44xxxxx6-xxxx-xxxx-xxxx-bxxxxxxxxx51/oauth2/v2.0/token" -v
```

and execute it.

Look at the file ``token.json`` and extract the token (Format should be similar to ``ey``...``J9.ey``...``fQ.DL``...``hQ``.)

# Azure AD Token Validation using Membrane API Gateway

## Start Membrane

1. Open the ``proxies.xml`` file in this directory.
2. Take a look at the following section:

   ```xml
   <jwtAuth expectedAud="api://2axxxx16-xxxx-xxxx-xxxx-faxxxxxxxxf0">
       <jwks jwksUris="https://login.microsoftonline.com/common/discovery/keys" />
   </jwtAuth>
   ```

   This allows only HTTP requests which include valid JWT tokens to pass.

3. Replace ``2axxxx16-xxxx-xxxx-xxxx-faxxxxxxxxf0`` by your backend's Application ID URI (see section 1.)
4. Open a console in the same directory.
5. Execute ``service-proxy.bat`` in the console and keep it running.

## Token Validation

In the following command,

| replace                     | by        |
|-----------------------------|-----------|
| ``ey...J9.ey...fQ.DL...hQ`` | the token |

```
curl -H "Authorization: Bearer ey...J9.ey...fQ.DL...hQ" http://localhost:8080/
```

and execute it.

You should be greeted by a message like

```
Hello there, d5xxxxx2-xxxx-xxxx-xxxx-fxxxxxxxxx7e.
```

This is the OID of the subject (=owner) of the token, which was assigned to the Demo Client. (See also https://learn.microsoft.com/de-de/azure/active-directory/develop/id-tokens .)

The greeting, including the OID, was issued as specified in ``proxies.xml``.

---
See:
- [jwtAuth](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/jwtAuth.htm) reference
- [jwks](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/jwks.htm) reference