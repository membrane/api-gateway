# Basic Authentication - JDBC

This example walks you through setting up **HTTP Basic Authentication** for an API or Web application using a JDBC data source.


## Running the Example

1. Navigate to the `examples/security/basic-auth/database` directory.

2. Download the latest `Platform-Independent Zip` from the [H2 Downloads](https://www.h2database.com/html/download-archive.html) page.

3. Unzip the downloaded file inside the current directory (resulting in an h2 folder). Install the `h2-*.jar` (database driver) from `./h2/bin` into the `<membrane-root>/lib` directory.

4. Execute `run_h2.sh` or `run_h2.bat`.  This should open the web console in your primary browser (if not, press the H2 tray icon). Log in using `org.h2.Driver` as `Driver Class`, `jdbc:h2:mem:userdata` as `JDBC URL` and `sa` as username with an empty password.

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

Let's examine the `proxies.xml` file.

```xml
<spring:bean name="dataSource" class="org.apache.commons.dbcp2.BasicDataSource">
   <spring:property name="driverClassName" value="org.h2.Driver" />
   <spring:property name="url" value="jdbc:h2:tcp://localhost/mem:userdata" />
   <spring:property name="username" value="sa" />
   <spring:property name="password" value="" />
</spring:bean>

<router>
  <api port="2000">
    <basicAuthentication>
	  <jdbcUserDataProvider datasource="dataSource" tableName="user" userColumnName="nickname" passwordColumnName="password" />
	</basicAuthentication>
	<target url="https://api.predic8.de"/>
  </api>
</router>
```

This configuration sets up an `<api>` component that directs calls from port `2000` to `https://api.predic8.de`, invoking the basicAuthentication-plugin for each request.

Let's take a closer look at the `<basicAuthentication>` element:

```xml
<basicAuthentication>
  <jdbcUserDataProvider datasource="jdbc:h2:mem:userdata" tableName="user" userColumnName="nickname" passwordColumnName="password" />
</basicAuthentication>
```

We define a new `jdbcUserDataProvider` that fetches authentication details from a JDBC datasource.
The attributes of the provider element specify the table name and the columns for username and password.

The datasource attribute requires a bean that implements the java DataSource interface,
in this example we use the following spring `bean` element definition to create one:

```xml
<spring:bean name="dataSource" class="org.apache.commons.dbcp2.BasicDataSource">
   <spring:property name="driverClassName" value="org.h2.Driver" />
   <spring:property name="url" value="jdbc:h2:tcp://localhost/mem:userdata" />
   <spring:property name="username" value="sa" />
   <spring:property name="password" value="" />
</spring:bean>
```

Membrane includes the class `org.apache.commons.dbcp2.BasicDataSource`, we can use it as a DataSource implementor.
Now we simply define our connection data as we did for the web console, except for the url.
Because we are targeting an external database, we will have to specify the address within in the JDBC url `...tcp://localhost/mem...`.

---
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