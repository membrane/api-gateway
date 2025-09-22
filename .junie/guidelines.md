Membrane API Gateway — Project-specific Development Guidelines

Audience: Experienced Java developers working on this repository.

Last verified with: Java 21, Maven 3.9+, Windows dev environment. Date: 2025-09-22 09:55 local.

Overview
- Repository layout: Maven multi-module project
  - Modules: annot, core, starter, distribution, war, test, maven-plugin
  - Java level: 21 (see root pom.xml properties javac.source/target)
  - Primary runtime module: distribution (contains router packaging and runnable artifacts)
  - Primary library code: core
  - Shared test utilities: test

Build and Configuration
- Prerequisites:
  - JDK 21 on PATH (ensure java -version returns 21.x)
  - Maven 3.9+ (mvn -v)
  - Git LFS not required for build
- Full build (all modules):
  - mvn -q -DskipTests package
  - Produces module artifacts under each module’s target directory. distribution assembles runnable bits.
- Build one module only:
  - mvn -q -pl core -am -DskipTests package
    - -pl selects the module; -am builds required dependencies.
- Useful flags:
  - -DskipTests skips unit tests (Surefire) and integration tests (Failsafe) entirely.
  - -DskipITs skips Failsafe integration tests only (if you want Surefire unit tests to run).
  - -Dtest=ClassName or -Dtest=ClassName#method for targeted unit tests.
  - -T 1C parallelizes the build per core.
- Code generation and packaging:
  - core uses an annotation processor (service-proxy-annot) to generate router-conf.xsd; maven-antrun copies it into docs at package time.
  - CycloneDX SBOM is generated at package for core.

Running the Router (local):
- Quick start with defaults:
  - After packaging distribution: distribution\target contains router binaries and scripts.
  - Configuration is under distribution\conf\proxies.xml (example provided). Edit/extend this file to add routes.
- From IDE: Run com.predic8.membrane.core.RouterCLI with appropriate args, or use distribution scripts. RouterCLI reads configuration based on the working directory and arguments.

Testing
- Frameworks:
  - JUnit 5 (Surefire). Integration tests via Maven Failsafe where applicable.
  - Shared testing utilities are provided by module test (artifactId service-proxy-test). Import as test scope in modules.
- Run all unit tests (entire repo):
  - mvn -q test
- Run unit tests for a specific module:
  - mvn -q -pl core -am test
- Target a single test class:
  - mvn -q -pl core -Dtest=com.predic8.membrane.core.transport.http.HttpTransportTest test
- Target a single test method:
  - mvn -q -pl core -Dtest=com.predic8.membrane.core.transport.http.HttpTransportTest#testOpenPortOK_NoSSL test
- Run integration tests (if any configured in the module):
  - mvn -q -pl core -DskipTests -DskipITs=false verify
    - Failsafe runs during integration-test/verify phases. The default config in core binds it to look for IntegrationTestsWithInternet/IntegrationTestsWithoutInternet suites.
- Windows path note: Always use backslashes for file paths when referencing resources; Maven parameters still use Java FQNs for -Dtest, not file paths.

Adding New Tests
- Placement:
  - Unit tests go under <module>\src\test\java with package mirroring main sources.
  - If you need common helpers, depend on module test (service-proxy-test) with scope test.
- Dependencies:
  - Most common test deps are already declared in core (JUnit Jupiter API/Engine, Mockito, Rest-Assured, XMLUnit, etc.). For other modules, add the same as needed, keeping scope test.
- Naming/Discovery:
  - JUnit 5 doesn’t require naming conventions, but Surefire by default discovers any class with @Test methods on the JUnit Platform.
  - Integration tests executed by Failsafe are typically bound by pattern or explicit suite classes (see core/pom.xml) — don’t rely on the old *IT.java naming unless the module config specifies it.
- Logging in tests:
  - Prefer SLF4J; enable additional logging via -Dorg.slf4j.simpleLogger.defaultLogLevel=debug or module-specific log4j configuration if present.

Demonstrated Example (verified now)
The following minimal test was added temporarily under core and executed successfully to validate the workflow:
- File created at: core\src\test\java\com\predic8\membrane\core\SanityTest.java
- Contents:
  package com.predic8.membrane.core;
  
  import org.junit.jupiter.api.Test;
  import static org.junit.jupiter.api.Assertions.*;
  
  public class SanityTest {
      @Test
      void sumsWork() {
          int a = 2 + 2;
          assertEquals(4, a, "Basic arithmetic should work");
      }
  }
- Command to run just this test class:
  mvn -q -pl core -Dtest=com.predic8.membrane.core.SanityTest test
- Result: Passed (1/1). The file has been removed after verification to keep the repo clean.

Module-Specific Notes and Pitfalls
- core:
  - Uses Java 21 language features; ensure IDE and toolchain are on 21.
  - Surefire in core explicitly sets argLine -Dfile.encoding=UTF-8 — do not remove; some tests and YAML parsing rely on UTF-8.
  - Integration suites are driven by specific class names (IntegrationTestsWithInternet/IntegrationTestsWithoutInternet). Avoid introducing external network dependencies into regular unit tests.
  - SpEL functions (com.predic8.membrane.core.lang.spel.functions.*): All built-in functions must be static and accept SpELExchangeEvaluationContext as last parameter. Keep these non-destructive. When adding functions, follow the documented constraints to auto-register via BuiltInFunctionResolver.
  - SecurityScheme scopes are surfaced via Exchange properties (SECURITY_SCHEMES). When writing tests touching scopes(), prefer setting Exchange properties rather than mocking deep router chains.
- distribution:
  - Conf files under distribution\conf are used by RouterCLI and scripts. If you change schema in core, ensure router-conf.xsd copy step still functions and docs generation remains intact.
- test (module):
  - Provides testing utilities (HTTP client helpers, common fixtures). Use as a test-scoped dependency from other modules to avoid duplication.

Code Style and Quality
- Java style is standard; prefer immutability and Optional for nullable flows where already used in core.
- Annotations:
  - org.jetbrains.annotations are available; use @NotNull/@Nullable for public APIs in core.
- Static analysis:
  - SpotBugs reporting is configured at the parent level; run mvn -Pspotbugs site if needed (or check the reporting section in module poms).
- Logging:
  - Use SLF4J (logback/log4j routing present). Avoid System.out in production code; in tests it’s acceptable for quick debug but prefer logger.

Quick Troubleshooting
- Compilation fails due to Java version: Ensure JAVA_HOME points to JDK 21 and IDE uses language level 21.
- Tests hang on network: Constrain to -Dtest=... for unit tests; avoid running Failsafe suites that require internet unless necessary.
- Classpath or annotation processor errors: Clean build with mvn -q -U -e -X -DskipTests clean package to refresh dependencies and get detailed logs.

Release/Packaging
- Full release artifacts are assembled under distribution\target after mvn package at the root. WAR packaging is in war module for servlet containers.

Contact/Upstream
- Upstream: https://github.com/membrane/api-gateway
- Issues: https://github.com/membrane/api-gateway/issues
