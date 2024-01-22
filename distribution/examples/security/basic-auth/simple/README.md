# Basic Authentication - Simple

This example explains how to protect an API or a Web application using __HTTP Basic Authentication__ with config embedded user data or an htpasswd file.


## Running the Example


1. Go to the `examples/security/basic-auth/simple` directory.

2. Execute `service-proxy.bat` or `service-proxy.sh`

3. Open the URL http://localhost:2000 in your browser.

4. Login with the username `alice` and the password `membrane`.

5. Open the URL http://localhost:3000 in your browser.

6. Login with credentials `membrane` & `proxy` or `john` & `smith123`


## How it is done

First take a look at the `proxies.xml` fil

```
<router>
  <api port="2000">
    <basicAuthentication>
      <user name="alice" password="membrane" />
    </basicAuthentication>
    <target url="https://api.predic8.de"/>
  </api>
  <api port="3000">
	<basicAuthentication>
	  <fileUserDataProvider htpasswdPath="./.htpasswd" />
	</basicAuthentication>
	<target url="https://api.predic8.de"/>
  </api>
</router>
```

There are two `<api>` components that direct calls from port `2000` and `3000` to `https://api.predic8.de`. The basicAuthentication-plugin is called for every request.

Now take a closer look at the `<basicAuthentication>` element:

```
<basicAuthentication>
  <user name="alice" password="membrane" />
</basicAuthentication>
```

Users are added through `user` elements. The username and the password are given by the attributes `name` and `password` of the `user` element. The example demonstrates one user, with username `alice` and password `membrane`.   
Alternatively, the `fileUserDataProvider` can be used to read basic authentication data from an htpasswd formatted file. The algorithm magic strings must be compatible with Unix `crypt(3)`.

When opening the URL `http://localhost:2000/` or the `3000` port equivalent, membrane will respond with `401 Not Authorized`.

```
HTTP/1.1 401 Unauthorized
Content-Type: text/html;charset=utf-8
WWW-Authenticate: Basic realm="Membrane Authentication"

<HTML><HEAD><TITLE>Error</TITLE><META HTTP-EQUIV='Content-Type' CONTENT='text/html; charset=utf-8'></HEAD><BODY><H1>401 Unauthorized.</H1></BODY></HTML>
```

The response will have the `WWW-Authenticate` header set. First the browser will ask you for your username and password. Then it will send the following request:

```
GET / HTTP/1.1
Host: localhost:2000
Authorization: Basic bWVtYnJhbmU6bWVtYnJhbmU=
```

Notice how the `Authorization` header is set with the hash of username and password. If the user is valid, membrane will let the request pass and the target host will respond.

---
See:
- [basicAuthentication](https://www.membrane-soa.org/api-gateway-doc/current/configuration/reference/basicAuthentication.htm) reference