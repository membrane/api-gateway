OAUTH2 AUTHORIZATION THROUGH MEMBRANE

With the OAuth2 client/resource and authorization server interceptor you can provide a secure layer in front of any unprotected resource.


RUNNING THE EXAMPLE

In this example we will use an authorization server that can authenticate users through a login dialog and a client that
communicates with this authentication server on behalf of the user to securely access any unprotected resource.

To run the example complete the following steps:

1. run the service-proxy.bat/.sh in the authorization_server subfolder

2. navigate to http://localhost:2000/.well-known/openid-configuration in your browser to see the openid-discovery file

3. run the service-proxy.bat/.sh in the client subfolder

4. navigate to http://localhost:2001 in your browser to start the login procedure

5. log in with "john" as username and "password" as password ( without quotes )

6. give consent for sharing information by clicking on accept

7. get redirected automatically to the unprotected resource


HOW IT IS DONE

The following part describes the example in detail.

First take a look at the proxies.xml file in the authorization_server subfolder.

[...]
<serviceProxy name="Authorization Server" port="2000">

    <oauth2authserver location="logindialog" issuer="http://localhost:2000" consentFile="consentFile.json">
        <staticUserDataProvider>
            <user username="john" password="password" email="john@predic8.de" />
        </staticUserDataProvider>
        <staticClientList>
            <client clientId="abc" clientSecret="def" callbackUrl="http://localhost:2001/oauth2callback" />
        </staticClientList>
        <bearerToken/>
        <claims value="aud email iss sub username">
            <scope id="username" claims="username"/>
            <scope id="profile" claims="username email password"/>
        </claims>
    </oauth2authserver>

</serviceProxy>
[...]

You will see a service proxy that listens on port 2000 for incoming calls, in particular it listens for oauth2/openid-connect calls.

This is done by the oauth2authserver element that acts as the authorization server and has several attributes and child elements for configuration.

The location attribute points to the folder where the login dialog index.html resides that is used in the login procedure.
The issuer attribute names the openid-connect issuer and is also the base url for all oauth2/openid-connect calls.
The consentFile attribute points to a json file where scopes and claims can be described for usage in the index.html.

The first child element is of base type UserDataProvider and is used to define information for a specific user. In this example we
use a StaticUserDataProvider that enables us to define the information directly in the configuration file.

The second child element is of base type ClientList and is used to register clients. In this example we use a StaticClientList that enables
us to define the information directly in the configuration file.

The third child element is of base type TokenGenerator and specifies the type of access token that is used in the oauth2/openid-connect
authorization process. Here the BearerToken generator is used.

The fourth and last child element specifies the claims and scopes that should be available for the authorization requester. The
value attribute specifies the available claims separated by spaces. With those claims one can build scope child elements that define oauth2 scopes.
The scope child elements consist of the id attribute to give the scope a name and the claims attribute to specify the requests claims separated by spaces.


Now take a look at the proxies.xml in the client subfolder.

[...]
<serviceProxy name="Membrane Resource service" port="2001">

    <oauth2Resource publicURL="http://localhost:2001/">
        <membrane src="http://localhost:2000" clientId="abc" clientSecret="def" scope="openid profile" claims="username" claimsIdt="sub" />
    </oauth2Resource>

    <target host="thomas-bayer.com" port="80"/>

</serviceProxy>
[...]

You will see a a service proxy that listens on port 2001 for incoming calls to redirect those to a specified authorization server. The incoming
calls are converted to proper oauth2 calls based on the configuration.

This is done by the oauth2Resource element that acts as the client/resource that has one attribute and element for configuration.

The publicURL attribute specifies the location the client/resource can be publicly called.

The first and only child element is of base type AuthorizationService and it has several attributes that specify how the oauth2 calls are done. In this
example the MembraneAuthorizationService is used that gets the most part of its configuration from openid-discovery files.
The src attribute points to an openid-provider ( in this example this is the issuer attribute in the authorization server ) and gets the openid-discovery
file from the issuer.
The cliendId and clientSecret attributes is given to the user at registration at the authorization server.
The scope attributes specifies the requested scopes.
The claims attribute specifies individual claims for the request and is only usable with the openid scope. These claims are then available at the
userinfo endpoint.
The claimsIdt attribute specifies individual claims for the request and is only usable with the openid scope. These claims are then available in
the idToken.