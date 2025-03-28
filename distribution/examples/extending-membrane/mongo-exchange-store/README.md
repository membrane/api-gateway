# MongoDB Exchange Store

Track and store all your Exchanges, even after restarts by connecting Membrane to MongoDB. View live data in the
Admin Console. Great for debugging, audits, or traffic insights.
This quick guide shows you how to set it up in minutes.

### Prerequisite

- **MongoDB installed:**

    - If MongoDB is already installed, skip to the next step.
- You need a running MongoDB instance. You can either:
- **Option 1: Use Docker**
    - Run the following command to start MongoDB in a Docker container:

  ```shell
    docker run --name mongo-exchange-store -p 27017:27017 -d mongo:latest
  ```
- This starts MongoDB in the background, accessible at ```mongodb://localhost:27017```.
- **Option 2: Otherwise, install MongoDB**
  - Download and install MongoDB from: [https://www.mongodb.com/try/download/community](https://www.mongodb.com/try/download/community).

---

1. **Download MongoDB Driver:**

- Download the MongoDB
  driver [https://repo1.maven.org/maven2/org/mongodb/mongodb-driver-sync/5.0.1/mongodb-driver-sync-5.0.1.jar](https://repo1.maven.org/maven2/org/mongodb/mongodb-driver-sync/5.0.1/mongodb-driver-sync-5.0.1.jar).
- Place it in the `lib` directory of your Membrane installation.

2. **Configure `proxies.xml`:**

- Example configuration for MongoDB:

   ```xml
   <mongoDBExchangeStore id="es" connection="mongodb://localhost:27017/" database="exchange"
                          collection="exchanges" />
    <router exchangeStore="es">
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

5. **You can test it using curl:**

```shell
curl -X POST http://localhost:2000 -H "Content-Type: application/json" -d '{"message": "Hallo"}'
```

6. **Verify Exchange Storage via MongoDB:**

- Once an exchange has been processed, it is stored in MongoDB under the database ```exchange```. in
  the collection ```exchanges```. You can verify using and MongoDB UI  (e.g., MongoDB Compass).

7. **Verify Exchange Storage via Admin Console:**

- Open the Admin Console by navigating to ```http://localhost:9000``` in your browser.
- Under the ```Call``` section, you will see a list of exchanges. These are the same entries stored in the MongoDB
  collection.
- Even if you stop Membrane and restart it, the exchanges will still appear in the Admin Console. This is because
  Membrane pulls the exchange data from MongoDB on startup.