# Basic Authentication - JDBC

This example explains how to protect an API or a Web application using __HTTP Basic Authentication__ with a JDBC datasource.


## Running the Example

1. Go to the `examples/security/basic-auth/database` directory.

2. Visit [H2 Downloads](https://www.h2database.com/html/download-archive.html) and download the most recent `Platform-Independent Zip`.

3. Unzip the downloaded file inside the current directory (should result in an h2 folder), then run `run_h2.sh` or `run_h2.bat`.

4. The web console opens in your primary browser (if not, press the H2 tray icon), enter `org.h2.Driver` as `Driver Class`, `jdbc:h2:mem:userdata` as `JDBC URL` and `sa` as username with an empty password.

5. Create demo users:  
   
   Once logged in, enter the following SQL into the text box, then press the run button above it.
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
// TODO
---
See:
- [basicAuthentication](https://www.membrane-soa.org/api-gateway-doc/current/configuration/reference/basicAuthentication.htm) reference