---
type: breaking
area: WSDL and SOAP
---
- `validator` with `wsdl`: a rejected message is now answered with the status the SOAP HTTP bindings require instead of `200`. A SOAP 1.1 fault has status `500`. A SOAP 1.2 fault has status `400` for a rejected request and `500` for a rejected response. Clients and scripts that took `200` with a fault for a rejection now see these statuses. [#3766](https://github.com/membrane/api-gateway/pull/3766)
- `validator` with `wsdl`: a rejected response is now reported as a `Server` (SOAP 1.1) or `Receiver` (SOAP 1.2) fault instead of `Client` or `Sender`, because the backend, not the client, sent the invalid message. A rejected request stays a `Client` or `Sender` fault. [#3766](https://github.com/membrane/api-gateway/pull/3766)
- `validator` with `wsdl`: a SOAP 1.2 fault is now sent with `Content-Type: application/soap+xml` instead of `text/xml`. [#3766](https://github.com/membrane/api-gateway/pull/3766)
