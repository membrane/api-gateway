# Basic Authentication

This example explains how to protect an API or a Web application using __HTTP Basic Authentication__.


## Running the Example


1. Go to the `examples/basic-auth` directory.

2. Execute `service-proxy.bat` or `service-proxy.sh`

3. Open the URL http://localhost:2000 in your browser.

4. Login with the username `alice` and the password `membrane`.


## How it is done

First take a look at the `proxies.xml` file.

```
<proxies>
  <api port="2000">
    <basicAuthentication>
      <user name="alice" password="membrane" />
    </basicAuthentication>
    <target url="https://api.predic8.de"/>
  </api>
</proxies>
```

There is an `<api>` that directs calls from the port 2000 to `https://api.predic8.de`. The basicAuthentication-plugin is called for every request.

Now take a closer look at the `<basicAuthentication>` element:

```
<basicAuthentication>
  <user name="alice" password="membrane" />
</basicAuthentication>
```

You can add users by using `user` elements. The `username` and the `password` are given by the attributes name and password of the `user` element. In the example there is one user with username `alice` and password `membrane`. 

When opening the URL `http://localhost:2000/` membrane will respond with `401 Not Authorized`.

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

See:
- [basicAuthentication](https://www.membrane-soa.org/api-gateway-doc/current/configuration/reference/basicAuthentication.htm) reference