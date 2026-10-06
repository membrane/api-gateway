# CORS Tutorial

Let web pages from other origins call your APIs. Browsers block cross-origin calls with
non-simple methods, custom headers or credentials unless the API answers with CORS headers.
The `cors` plugin adds those headers and answers the browser's preflight `OPTIONS` requests.

For an in-depth explanation, see the
[CORS Guide for API Developers](https://www.membrane-api.io/cors-api-gateway.html).

Before you start, make sure you have completed the
[Getting Started](../getting-started) tutorial.

To begin, open [10-CORS-Allow-All.yaml](10-CORS-Allow-All.yaml) and follow the instructions
in the file. Both steps use [page.html](page.html) to send real browser requests.
