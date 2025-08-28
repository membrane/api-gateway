# Load Balancer: Health Monitoring with TLS

You can run the sample from the README.me with TLS protection. By making a few changes described here. 

1. `proxies-tls.xml` contains the needed TLS configuration. Take a short look at it.
2. Start Membrane with that file:

    - macOS/Linux: `./membrane.sh -c proxies-tls.xml`
    - Windows: `membrane.cmd -c proxies-tls.xml`

3. Verify that the load balancer and the backends are working

   ```bash
   curl -k https://localhost:8443
   curl -k https://localhost:8002
   curl -k https://localhost:8002
   ```

   -k let curl accept the self signed certificates located in the certificates folder.

4. Admin console URL is `https://localhost:9443`


# Hints and Tips

- Do not use the sample certificates for production
- You'll find the script to create the sample certificates, trust and keystores in the `certificates` folder:

  ```bash
  create-certificates.sh
  create-certificates.cmd
  ```