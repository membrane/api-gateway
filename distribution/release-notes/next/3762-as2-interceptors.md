---
type: feature
area: AS2
---
- `as2Receiver`: receive AS2 (RFC 4130) messages, e.g. EDI documents, through the Membrane AS2 Extension. Incoming messages are decrypted, decompressed and verified, the payload is forwarded to the backend, and the sender gets a signed MDN [#3762](https://github.com/membrane/api-gateway/pull/3762)
- `as2Sender`: send documents to a trading partner over AS2 through the Membrane AS2 Extension. A plain request becomes a signed, encrypted AS2 message, and the partner's MDN is verified [#3762](https://github.com/membrane/api-gateway/pull/3762)
- Only synchronous MDNs are supported. Without the extension, Membrane fails at startup when one of the interceptors is configured
