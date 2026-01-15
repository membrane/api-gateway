### ACCESS CONTROL

The `accessControl` plugin lets you restrict who can call your APIs. Rules can match the client by **IP address** (IPv4/IPv6, optional CIDR) or by **hostname** (regex). Rules are evaluated **top to bottom**. The first matching rule decides. If nothing matches, access is denied.

### RUNNING THE EXAMPLE

1. Start Membrane from this directory:
   ```sh
   ./membrane.sh
   ```
    or on Windows:
   ```sh
   membrane.cmd
   ```
2. Call the API on port 2000 (allowed for everyone):
   ```sh
   curl http://localhost:2000/
   ```
3. Call `/products` (allowed on localhost):
   ```sh
   curl http://localhost:2000/products
   ```
4. Call `/vendors` (denied for localhost):
   ```sh
   curl http://localhost:2000/vendors
   ```
5. Open the [`apis.yaml`](apis.yaml) file to see how the three API routes are defined and how `accessControl` is applied per route.

Notes:

* Hostname rules are regexes matched against the peer hostname (resolved only when hostname rules exist).
* IP rules support CIDR (e.g., `10.0.0.0/8`, `2001:db8::/64`), and work for both IPv4 and IPv6.

---

See:

* [accessControl Documentation](https://www.membrane-api.io/docs/current/accessControl.html)
