# MongoDB Exchange Store

Track and store all your Exchanges, even after restarts by connecting Membrane to MongoDB. View live data in the
Admin Console. Great for debugging, audits, or traffic insights.
This quick guide shows you how to set it up in minutes.

### Prerequisite

- **Run MongoDB:**
  - Run the following command to start MongoDB in a Docker container:

    ```shell
    docker run --name mongo -p 27017:27017 mongo:latest
    ```
  - This starts MongoDB, accessible at ```mongodb://localhost:27017```.

---

1. **Download MongoDB Driver:**

- Download the MongoDB
  driver [https://repo1.maven.org/maven2/org/mongodb/mongodb-driver-sync/5.0.1/mongodb-driver-sync-5.0.1.jar](https://repo1.maven.org/maven2/org/mongodb/mongodb-driver-sync/5.0.1/mongodb-driver-sync-5.0.1.jar).
- Place it in the `lib` directory of your Membrane installation.

2. **Configure `proxies.xml`:**

- Example configuration for MongoDB:

   ```xml
   <mongoDBExchangeStore id="store" connection="mongodb://localhost:27017/" database="membrane"
                          collection="exchanges" />
    <router exchangeStore="store">
        <serviceProxy name="predic8.com" port="2000">
            <target url="https://membrane-soa.org" />
        </serviceProxy>
        <serviceProxy port="9000">
            <adminConsole />
        </serviceProxy>
    </router>
   ```

3. **run service.proxy.sh script:**

```shell
./membrane.sh
```

4. **You can test it using curl:**

```shell
curl -X POST http://localhost:2000 -H "Content-Type: application/json" -d '{"message": "Hallo"}'
```

5. **Verify Exchange Storage via Admin Console:**

- Open the Admin Console by navigating to ```http://localhost:9000``` in your browser.
- Under the ```Call``` section, you will see a list of exchanges. These are the same entries stored in the MongoDB
  collection.
- Even if you stop Membrane and restart it, the exchanges will still appear in the Admin Console. This is because
  Membrane pulls the exchange data from MongoDB on startup.