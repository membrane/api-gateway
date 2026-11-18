---
type: security
area: HTTP Transport and Runtime
---
- The start scripts (`membrane.sh`, `membrane.cmd` and the Docker images) and the Tanuki `wrapper.conf` of the Windows service example now start the JVM with `-XX:-UseBASE64Intrinsics`, the interim mitigation for CVE-2026-114938 ("Java4Shell"), a memory corruption in the AVX-512 Base64 decoder of JDK 17, 21, 25 and 27 on x86-64. Installing a fixed JDK build remains necessary. To turn the intrinsics back on, for example on a patched JDK, add `-XX:+UseBASE64Intrinsics` to `JAVA_OPTS`.
