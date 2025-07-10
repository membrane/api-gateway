# Cross-Origin Resource Sharing

The usage of APIs on websites is restricted by modern web browsers’ cross‑origin policies. To allow HTTP methods like
POST, custom headers, or credentials, you need to add CORS HTTP headers.

The CORS plugin makes it easy to add those headers and to answer preflight OPTIONS requests made by the browser.

For an in-depth explanation of using CORS for APIs, check out
the [CORS Guide for API Developers](https://www.membrane-api.io/cors-api-gateway.html).

---

## What This Project Does

- Provides an **HTML test page** for sending API requests with or without CORS-relevant headers.
- Demonstrates real browser behavior for preflight and actual requests.
- Lets you choose methods (`GET`, `POST`) and toggle headers to test different scenarios.
- Helps visualize when and why CORS headers are required.

---

## How to Run the Demo

1. Start the API Gateway by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
2. Open the `page.html` file directly in your browser.
3. Interact with the test page:
    - Select an API URL and HTTP method.
    - Choose any custom headers you'd like to send.
    - Click the **"Call API"** button.
4. Open Developer Tools → **Network** tab to inspect:
    - Whether a preflight `OPTIONS` request is triggered.
    - The final request and response, including headers.
    - Any CORS-related errors in the Console.

---

## Expected Behavior

### Port 2001 (`allowAll="true"`)

- All requests succeed, regardless of method or headers.
- CORS headers are always included in the response.
- Preflight `OPTIONS` requests are automatically answered.

### Port 2002 (`origins="null"`, explicit headers and methods)

- Requests with `Origin: null` are only allowed if:
- The origin `"null"` is configured.
- The request method is listed in `methods`.
- The custom headers match those listed in `headers`.
- If any header is not allowed (or casing differs), the preflight fails with a **403 Forbidden**.
- GET and POST requests **without custom headers** do not trigger preflights and are allowed.

---
