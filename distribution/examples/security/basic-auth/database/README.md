# Basic Authentication - JDBC

This example explains how to protect an API or a Web application using __HTTP Basic Authentication__ with a JDBC datasource.


## Running the Example

1. Go to the `examples/security/basic-auth/database` directory.

2. Visit [H2 Downloads](https://www.h2database.com/html/download-archive.html) and download the most recent `Platform-Independent Zip`.

3. Unzip the downloaded file inside the current directory (should result in an h2 folder), make sure to install the `h2-*.jar` (database driver) from `./h2/bin` into the `<membrane-root>/lib` directory.

4. Run `run_h2.sh` or `run_h2.bat`, the web console opens in your primary browser (if not, press the H2 tray icon), enter `org.h2.Driver` as `Driver Class`, `jdbc:h2:mem:userdata` as `JDBC URL` and `sa` as username with an empty password.

5. Create demo users:  
   
   Once logged in, enter the following SQL into the text box, then press the run button above.
   ```SQL
   CREATE TABLE "user" (
     "nickname" VARCHAR(255) NOT NULL,
     "password" VARCHAR(255) NOT NULL,
     PRIMARY KEY ("nickname")
   );
   
   INSERT INTO "user" ("nickname", "password")
   VALUES ('johnsmith123', 'pass123'), ('membrane', 'proxy');
   ```

5. Execute `service-proxy.bat` or `service-proxy.sh`.

6. Open the URL http://localhost:2000 in your browser.

7. Login with the username `membrane` and the password `gateway`.


## How it is done

First take a look at the `proxies.xml` file.

```xml
<router>
  <api port="2000">
    <basicAuthentication>
	  <jdbcUserDataProvider datasource="jdbc:h2:mem:userdata" tableName="user" userColumnName="nickname" passwordColumnName="password" />
	</basicAuthentication>
	<target url="https://api.predic8.de"/>
  </api>
</router>
```

There is an `<api>` component that directs calls from port `2000` to `https://api.predic8.de`, the basicAuthentication-plugin is called for every of its requests.

Now take a closer look at the `<basicAuthentication>` element:

```xml
<basicAuthentication>
  <jdbcUserDataProvider datasource="jdbc:h2:mem:userdata" tableName="user" userColumnName="nickname" passwordColumnName="password" />
</basicAuthentication>
```

We define a new `jdbcUserDataProvider`, this userDataProvider loads basic authentication login data from a JDBC datasource. The table name, username and password columns are specified using the according attributes of the provider element.
The datasource is defined through the attribute of the same name, and accepts a JDBC uri.

When opening the URL `http://localhost:2000/`, membrane will respond with `401 Unauthorized`.

```html
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