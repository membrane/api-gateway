/* Copyright 2009, 2012, 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.cli;

import com.fasterxml.jackson.core.*;
import com.predic8.membrane.annot.beanregistry.*;
import com.predic8.membrane.annot.yaml.*;
import com.predic8.membrane.core.config.spring.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.router.*;
import org.apache.commons.cli.*;
import org.apache.commons.codec.binary.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.springframework.beans.factory.xml.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.*;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.cli.util.JwkGenerator.*;
import static com.predic8.membrane.core.config.spring.CheckableBeanFactory.*;
import static com.predic8.membrane.core.config.spring.TrackingFileSystemXmlApplicationContext.*;
import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.proxies.ApiInfo.logInfosAboutStartedProxies;
import static com.predic8.membrane.core.util.ExceptionUtil.*;
import static com.predic8.membrane.core.util.OSUtil.*;
import static com.predic8.membrane.core.util.URIUtil.*;
import static com.predic8.membrane.core.util.text.TerminalColors.*;
import static java.lang.Integer.*;
import static org.apache.commons.lang3.exception.ExceptionUtils.*;

public class RouterCLI {

    private static final Logger log = LoggerFactory.getLogger(RouterCLI.class);

    public static void main(String[] args) {
        try {
            start(args);
        } catch (ExitException ignored) {
            // Nothing logged on purpose. The exception is just to trigger exit at one place.
            // Do logging where the exception is thrown.
            System.exit(1);
        }
    }

    private static void start(String[] args) {
        log.debug("CLI started with args: {}", Arrays.toString(args));
        var commandLine = getMembraneCommandLine(args);
        if (commandLine.getCommand().isOptionSet("h")) {
            commandLine.getCommand().printHelp();
            System.exit(0);
        }

        // Dry run
        if (commandLine.noCommand() && commandLine.getCommand().isOptionSet("t")) {
            dryRun(commandLine);
        }

        if (commandLine.getCommand().getName().equals("generate-jwk")) {
            generateJWK(commandLine);
            System.exit(0);
        }

        if (commandLine.getCommand().getName().equals("private-jwk-to-public")) {
            privateJwkToPublic(commandLine);
        }

        if (commandLine.getCommand().getName().equals("argon2id")) {
            argon2id(commandLine);
        }

        if (getRouter(commandLine) instanceof DefaultRouter dr)
            dr.waitFor();
    }

    private static void privateJwkToPublic(MembraneCommandLine commandLine) {
        String input = commandLine.getCommand().getOptionValue("i");
        String output = commandLine.getCommand().getOptionValue("o");
        if (input == null || output == null) {
            log.error("Both input (-i) and output (-o) files must be specified.");
            System.exit(1);
        }
        privateJWKtoPublic(
                input,
                output);
        System.exit(0);
    }

    private static void argon2id(MembraneCommandLine commandLine) {
        try {
            String password = commandLine.getCommand().getOptionValue("pass");
            String version = commandLine.getCommand().getOptionValue("v");
            String salt = commandLine.getCommand().getOptionValue("s");
            String iterations = commandLine.getCommand().getOptionValue("i");
            String memory = commandLine.getCommand().getOptionValue("m");
            String parallelism = commandLine.getCommand().getOptionValue("p");

            int v = version == null ? 19 : Integer.parseInt(version);
            int i = iterations == null ? 3 : Integer.parseInt(iterations);
            int m = memory == null ? 65536 : Integer.parseInt(memory);
            int p = parallelism == null ? 1 : Integer.parseInt(parallelism);
            if (password == null) {
                System.out.println("Enter password to hash:");
                Scanner s = new Scanner(System.in);
                password = s.nextLine();
            }
            byte[] s = salt == null ? null : Hex.decodeHex(salt.toCharArray());
            if (s == null) {
                SecureRandom random = new SecureRandom();
                s = new byte[16];
                random.nextBytes(s);
            }

            System.out.println(buildArgon2idPCH(password.getBytes(StandardCharsets.UTF_8), s, v, i, m, p));
        } catch (Exception e) {
            System.err.println(getExceptionMessageWithCauses(e));
            System.exit(1);
        }
        System.exit(0);
    }

    private static void dryRun(MembraneCommandLine commandLine) {
        try {
            new TrackingFileSystemXmlApplicationContext(new String[]{getRulesFile(commandLine)}, false).refresh();
        } catch (Throwable e) {
            System.err.println(getExceptionMessageWithCauses(e));
            System.exit(1);
        }
        System.exit(0);
    }

    public static String getExceptionMessageWithCauses(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        result.append("Exception: ").append(getMessage(throwable)).append("\n");
        String rootCauseMessage = getRootCauseMessage(throwable);
        if (!rootCauseMessage.equals(getMessage(throwable))) {
            result.append("Root cause: ").append(rootCauseMessage);
        }
        return result.toString();
    }

    private static @NotNull Router getRouter(MembraneCommandLine commandLine) {
        try {
            return switch (commandLine.getCommand().getName()) {
                case "oas" -> initRouterByOpenApiSpec(commandLine);
                case "yaml" -> initRouterByYAML(commandLine, "l");
                default -> initRouterByConfig(commandLine);
            };
        } catch (InvalidConfigurationException e) {
            String errorMsg = concatMessageAndCauseMessages(e);
            if (isOpenAPIMisplacedError(errorMsg)) {
                log.error("Fatal error caused by <openapi /> element. Make sure it is the first element of the API.\n{}", errorMsg);
            } else {
                log.error("Fatal error: {}", errorMsg);
            }
        } catch (ConfigurationParsingException e) {
            // Keep with one param to log otherwise first color code will be ignored!
            try {
                log.error("{}", "%s%s%s\n%s".formatted(RED(), e.getMessage(), RESET(), e.getFormattedReport()));
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        } catch (ExitException ignored) {
            // do nothing
        } catch (Exception ex) {
            SpringConfigurationErrorHandler.handleRootCause(ex, log);
        }
        System.exit(1);
        // Will never be reached
        return null;
    }

    private static Router initRouterByConfig(MembraneCommandLine commandLine) throws Exception {
        String config = getRulesFile(commandLine);
        if (config.endsWith(".xml")) {
            var router = initRouterByXml(config);
            logStartupMessage();
            return router;
        }
        if (config.endsWith(".yaml") || config.endsWith(".yml")) {
            return initRouterByYAML(config);
        }
        throw new RuntimeException("Unsupported file extension.");
    }

    private static Router initRouterByOpenApiSpec(MembraneCommandLine commandLine) throws Exception {
        var router = new DefaultRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(getApiProxy(commandLine));
        router.init();
        logInfosAboutStartedProxies(router.getRuleManager());
        logStartupMessage();
        return router;
    }

    private static Router initRouterByYAML(MembraneCommandLine commandLine, String option) throws Exception {
        return initRouterByYAML(commandLine.getCommand().getOptionValue(option));
    }

    static Router initRouterByYAML(String location) throws Exception {
        var router = new DefaultRouter();
        var grammar = new GrammarAutoGenerated();
        var reg = new BeanRegistryImplementation(grammar);

        router.setRegistry(reg);
        reg.register("router", router);

        List<BeanDefinition> beanDefinitions = reg.parseYamlBeanDefinitions(router.getResolverMap().resolve(location), grammar, getRootSourceFile(location));
        ensureSingleGlobalDefinition(beanDefinitions);
        getConfigDefinition(beanDefinitions)
                .ifPresent(configBd -> router.applyConfiguration((Configuration) reg.resolve(configBd.getName())));

        reg.finishStaticConfiguration();
        reg.start();

        // Must be set after reading the YAML. Otherwise, it will be overwritten
        router.getConfiguration().setBaseLocation(location);

        router.start();
        logInfosAboutStartedProxies(router.getRuleManager());
        logStartupMessage();
        return router;
    }

    private static @NotNull Optional<BeanDefinition> getConfigDefinition(List<BeanDefinition> bds) {
        List<BeanDefinition> configurationDefinitions = bds.stream()
                .filter(bd -> "configuration".equals(bd.getKind()))
                .toList();

        if (configurationDefinitions.size() > 1) {
            throw new IllegalStateException("Found multiple 'configuration' definitions (%d). Only one is allowed. Found at: %s"
                    .formatted(configurationDefinitions.size(), configurationDefinitions.stream()
                            .map(RouterCLI::formatConfigLocation)
                            .collect(Collectors.joining(", "))));
        }

        return configurationDefinitions.stream().findFirst();
    }

    private static void ensureSingleGlobalDefinition(List<BeanDefinition> bds) {
        List<BeanDefinition> globalDefinitions = bds.stream()
                .filter(bd -> "global".equals(bd.getKind()))
                .toList();

        if (globalDefinitions.size() > 1) {
            throw new IllegalStateException("Found multiple 'global' definitions (%d). Only one is allowed. Found at: %s"
                    .formatted(globalDefinitions.size(), globalDefinitions.stream()
                            .map(RouterCLI::formatConfigLocation)
                            .collect(Collectors.joining(", "))));
        }
    }

    private static String formatConfigLocation(BeanDefinition bd) {
        if (bd.getSourceMetadata() != null && bd.getSourceMetadata().sourceFile() != null) {
            return bd.getSourceMetadata().sourceFile().toString();
        }
        return bd.getName();
    }

    private static Path getRootSourceFile(String location) {
        if (!isWindowsAbsolutePath(location)) {
            try {
                URI uri = new URI(location);
                String scheme = uri.getScheme();
                if (scheme != null && !"file".equalsIgnoreCase(scheme))
                    return null;
            } catch (URISyntaxException ignored) {}
        }

        try {
            return Path.of(pathFromFileURI(location));
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static boolean isWindowsAbsolutePath(String location) {
        if (location == null || location.length() < 3) {
            return false;
        }
        return Character.isLetter(location.charAt(0))
                && location.charAt(1) == ':'
                && (location.charAt(2) == '/' || location.charAt(2) == '\\');
    }

    private static @NotNull APIProxy getApiProxy(MembraneCommandLine commandLine) throws IOException {
        APIProxy api = new APIProxy();
        api.setPort(commandLine.getCommand().isOptionSet("p") ?
                parseInt(commandLine.getCommand().getOptionValue("p")) : 2000);
        api.setOpenapi(List.of(getOpenAPISpec(commandLine)));
        return api;
    }

    private static @NotNull OpenAPISpec getOpenAPISpec(MembraneCommandLine commandLine) throws IOException {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = getLocation(commandLine);

        if (commandLine.getCommand().isOptionSet("v"))
            spec.setValidateRequests(YES);
        if (commandLine.getCommand().isOptionSet("V"))
            spec.setValidateResponses(YES);
        return spec;
    }

    private static String getLocation(MembraneCommandLine commandLine) throws IOException {
        String location = commandLine.getCommand().getOptionValue("l");

        if (location == null || location.isEmpty()) throw new RuntimeException(); // unreachable
        if (location.startsWith("http://") || location.startsWith("https://")) return location;

        File locFile = new File(location);
        if (locFile.isAbsolute()) return locFile.getCanonicalPath();

        return new File(getUserDir(), location).getCanonicalPath();
    }

    private static Router initRouterByXml(String config) throws Exception {
        try {
            return RouterXmlBootstrap.initByXML(config);
        } catch (XmlBeanDefinitionStoreException e) {
            handleXmlBeanDefinitionStoreException(e);
        }
        throw new RuntimeException("Router could not be initialized");
    }

    private static MembraneCommandLine getMembraneCommandLine(String[] args) {
        MembraneCommandLine cl = new MembraneCommandLine();

        try {
            cl.parse(args);
        } catch (MissingRequiredOptionException e) {
            e.getCommand().printHelp();
            System.exit(1);
        } catch (ParseException e) {
            System.err.println("Error parsing commandline " + e.getMessage());
            cl.getRootNamespace().printHelp();
            System.exit(1);
        }

        return cl;
    }

    private static String getRulesFile(MembraneCommandLine cl) throws IOException {
        ResolverMap rm = new ResolverMap();
        if (hasConfiguration(cl)) {
            String filename = fixBackslashes(getConfiguration(cl));
            if (shouldResolveFile(filename)) {
                // absolute
                try (InputStream ignored = rm.resolve(filename)) {
                    return filename;
                } catch (ResourceRetrievalException e) {
                    System.err.printf("Could not open Membrane's configuration file: %s not found.%n", filename);
                    System.exit(1);
                }
            }
            return getRulesFileFromRelativeSpec(rm, filename);
        }
        return getDefaultConfig();
    }

    private static String getDefaultConfig() {
        String callerDir = System.getenv("MEMBRANE_CALLER_DIR");
        if (callerDir == null || callerDir.isEmpty()) {
            callerDir = getUserDir();
        }

        // Prio 1: dir/apis.yaml | dir/apis.yml
        String config = tryConfig(callerDir, "apis.yaml");
        if (config != null) return config;
        config = tryConfig(callerDir, "apis.yml");
        if (config != null) return config;

        // Prio 2: dir/proxies.xml
        config = tryConfig(callerDir, "proxies.xml");
        if (config != null) return config;

        String membraneHome = System.getenv(MEMBRANE_HOME);
        if (membraneHome != null && !membraneHome.isEmpty()) {
            String homeConf = membraneHome + File.separator + "conf";

            // Prio 3: home/apis.yaml | home/apis.yml
            config = tryConfig(homeConf, "apis.yaml");
            if (config != null) return config;
            config = tryConfig(homeConf, "apis.yml");
            if (config != null) return config;

            // Prio 4: home/proxies.xml
            config = tryConfig(homeConf, "proxies.xml");
            if (config != null) return config;
        }

        System.err.println("No configuration file found (apis.yaml, apis.yml or proxies.xml). Provide one of these or use -c <file>.");
        System.exit(1);
        throw new RuntimeException("No configuration file found."); // unreachable
    }

    private static String tryConfig(String dir, String fileName) {
        if (dir == null || dir.isEmpty()) return null;
        File f = new File(dir, fileName);
        if (!f.isFile()) return null;
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    private static String getConfiguration(MembraneCommandLine cl) {
        return cl.getCommand().isOptionSet("c") ?
                cl.getCommand().getOptionValue("c") :
                cl.getCommand().getOptionValue("t");
    }

    private static boolean hasConfiguration(MembraneCommandLine cl) {
        return cl.getCommand().isOptionSet("c") ||
               cl.getCommand().isOptionSet("t");
    }

    private static boolean shouldResolveFile(String s) {
        return s.startsWith("file:") || s.startsWith("/") || s.length() > 3 && s.startsWith(":/", 1);
    }

    private static String getRulesFileFromRelativeSpec(ResolverMap rm, String relativeFile) {
        String try1 = pathFromFileURI(ResolverMap.combine(prefix(getUserDir()), relativeFile));
        if (canResolveConfigurationFile(rm, try1, 1))
            return try1;

        String try2 = null;
        if (System.getenv(MEMBRANE_HOME) != null) {
            try2 = pathFromFileURI(ResolverMap.combine(prefix(System.getenv(MEMBRANE_HOME)), relativeFile));
            if (canResolveConfigurationFile(rm, try2, 2))
                return try2;
        }
        System.err.printf("Could not find Membrane's configuration file at %s%s%n", try1, try2 == null ? "" : " and not at " + try2);
        throw new ExitException();
    }

    private static boolean canResolveConfigurationFile(ResolverMap rm, String try1, int attempt) {
        try (InputStream ignored = rm.resolve(try1)) {
            return true;
        } catch (Exception e) {
            log.warn("Could not resolve path to configuration (path: {} attempt: {}).", try1, attempt);
        }
        return false;
    }

    public static String getUserDir() {
        String userDir = fixBackslashes(System.getProperty("user.dir"));
        if (!userDir.endsWith("/"))
            return userDir + "/";
        return userDir;
    }

    private static String prefix(String dir) {
        File file = new File(dir);
        if (file.isAbsolute()) {
            return file.toURI().toString();
        }
        return dir;
    }

    private static void logStartupMessage() {
        log.info("{}{} {} up and running!{}", BRIGHT_CYAN(), PRODUCT_NAME, VERSION, RESET());
    }
}