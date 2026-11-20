---
type: breaking
area: Java API
---
- Java API (only relevant when subclassing `AbstractBody`): `readLocal()` is replaced by `readChunksWithin(long maxLength)`, which reads no further than the part that takes the body beyond `maxLength` and returns `false` in that case. `read()` passes `Long.MAX_VALUE`. Subclasses have to implement the new method instead of the old one
- Java API (only relevant when Membrane is used as a library): `LimitInterceptor.LengthLimitingStream` is removed. To check the length of a body, use `Message.checkBodyLength(long)`, which also checks a body compressed with gzip, deflate or br once decoded (other codings by the bytes received), or `AbstractBody.readUpTo(long)`. Both return a `BodyLengthCheck`; a body that was left unread because it is too long fails with a `BodyLengthExceededException`
