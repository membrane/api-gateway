Membrane Service Proxy Maven Plugin
===================================

This Maven plugin allows running Service Proxy from Maven.

Configuration
-------------

Add the following to the `<build>` section of your *pom.xml*:

```xml
<plugin>
  <groupId>org.membrane-soa</groupId>
  <artifactId>service-proxy-maven-plugin</artifactId>
  <version>4.2.1-SNAPSHOT</version>
  <configuration>
    <proxiesXml>src/test/resources/proxies.xml</proxiesXml>
  </configuration>
</plugin>
```

Usage
-----

Run Service-Proxy with `mvn service-proxy:run`.

Notes
-----

The plugin currently does not support forking.
By default, it's bound to the *test-compile* phase. It might or might not fit your needs.
You can change it by defining the phase explicitly in your pom.
