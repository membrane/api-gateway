/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.kubernetes.BeanCache;
import com.predic8.membrane.core.kubernetes.client.WatchAction;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.resolver.*;
import org.apache.commons.cli.*;
import org.jetbrains.annotations.*;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.slf4j.*;
import org.springframework.beans.factory.xml.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.config.spring.TrackingFileSystemXmlApplicationContext.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.util.ExceptionUtil.*;
import static com.predic8.membrane.core.util.OSUtil.*;
import static com.predic8.membrane.core.util.URIUtil.*;
import static java.lang.Integer.*;

public class RouterCLI {

    private static final Logger log = LoggerFactory.getLogger(RouterCLI.class);

    public static void main(String[] args) {
        MembraneCommandLine commandLine = getMembraneCommandLine(args);
        if (commandLine.getCommand().isOptionSet("h")) {
            commandLine.getCommand().printHelp();
            System.exit(0);
        }

        // Dry run
        if (commandLine.noCommand() && commandLine.getCommand().isOptionSet("t")) {
            System.exit(0);
        }

        try {
            getRouter(commandLine).waitFor();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private static @NotNull Router getRouter(MembraneCommandLine commandLine) {
        try {
            if (commandLine.getCommand().getName().equals("generate-jwk")) {
                initRouterByGenerateJWK(commandLine);
                System.exit(0);
            }
            return switch (commandLine.getCommand().getName()) {
                case "oas" -> initRouterByOpenApiSpec(commandLine);
                case "yaml" -> initRouterByYAML(commandLine);
                default -> initRouterByConfig(commandLine);
            };
        } catch (InvalidConfigurationException e) {
            log.error("Fatal error: {}", concatMessageAndCauseMessages(e));
        } catch (Exception ex) {
            SpringConfigurationErrorHandler.handleRootCause(ex, log);
        }
        System.exit(1);
        // Will never be reached
        return null;
    }

    private static Router initRouterByOpenApiSpec(MembraneCommandLine commandLine) throws Exception {
        Router router = new HttpRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(getApiProxy(commandLine));
        router.init();
        return router;
    }

    private static Router initRouterByYAML(MembraneCommandLine commandLine) throws Exception {
        String location = commandLine.getCommand().getOptionValue("l");
        var fileReader = new FileReader(location);

        var router = new HttpRouter();
        router.setBaseLocation(location);
        router.setHotDeploy(false);
        router.start();

        var beanCache = new BeanCache(router);
        beanCache.start();

        YAMLParser parser = new YAMLFactory().createParser(new File(location));
        var om = new ObjectMapper();
        while (!parser.isClosed()) {
            Map<?, ?> m = om.readValue(parser, Map.class);
            Map<Object, Object> meta = (Map<Object, Object>) m.get("metadata");

            // fake UID
            meta.put("uid", location + "-" + meta.get("name"));

            beanCache.handle(WatchAction.ADDED, m);
            parser.nextToken();
        }

        return router;
    }

    private static void initRouterByGenerateJWK(MembraneCommandLine commandLine) throws Exception {
        int bits = 2048;
        String bitsArg = commandLine.getCommand().getOptionValue("b");
        if (bitsArg != null) {
            bits = Integer.parseInt(bitsArg);
        }

        String outputFile = commandLine.getCommand().getOptionValue("o");

        RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(bits);
        rsaJsonWebKey.setKeyId(new BigInteger(130, new SecureRandom()).toString(32));
        rsaJsonWebKey.setUse("sig");
        rsaJsonWebKey.setAlgorithm("RS256");

        Files.writeString(Paths.get(outputFile), rsaJsonWebKey.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE));
    }

    private static @NotNull APIProxy getApiProxy(MembraneCommandLine commandLine) {
        APIProxy api = new APIProxy();
        api.setPort(commandLine.getCommand().isOptionSet("p") ?
                parseInt(commandLine.getCommand().getOptionValue("p")) : 2000);
        api.setSpecs(List.of(getOpenAPISpec(commandLine)));
        return api;
    }

    private static @NotNull OpenAPISpec getOpenAPISpec(MembraneCommandLine commandLine) {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = commandLine.getCommand().getOptionValue("l");
        if (commandLine.getCommand().isOptionSet("v"))
            spec.setValidateRequests(YES);
        if (commandLine.getCommand().isOptionSet("V"))
            spec.setValidateResponses(YES);
        return spec;
    }

    private static Router initRouterByConfig(MembraneCommandLine commandLine) throws Exception {
        try {
            return Router.init(getRulesFile(commandLine));
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
                    System.err.println("Could not open Membrane's configuration file: " + filename + " not found.");
                    System.exit(1);
                }
            }
            return getRulesFileFromRelativeSpec(rm, filename, "");
        }
        return getRulesFileFromRelativeSpec(rm, "conf/proxies.xml", getErrorNotice());
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

    private static String getErrorNotice() {
        String errorNotice = "Please specify the location of Membrane's proxies.xml configuration file using the -c command line option.";
        if (System.getenv(MEMBRANE_HOME) != null) {
            return errorNotice + " Or create the file in MEMBRANE_HOME/conf (" + System.getenv("MEMBRANE_HOME") + "/conf/proxies.xml).";
        }
        return errorNotice + """
                You can also point the MEMBRANE_HOME environment variable to Membrane's distribution root directory
                and ensure that MEMBRANE_HOME/conf/proxies.xml exists.
                """;
    }

    private static boolean shouldResolveFile(String s) {
        return s.startsWith("file:") || s.startsWith("/") || s.length() > 3 && s.startsWith(":/", 1);
    }

    private static String getRulesFileFromRelativeSpec(ResolverMap rm, String relativeFile, String errorNotice) {
        String try1 = pathFromFileURI(ResolverMap.combine(prefix(getUserDir()), relativeFile));
        try (InputStream ignored = rm.resolve(try1)) {
            return try1;
        } catch (Exception e) {
            log.error("Could not resolve path to configuration (attempt 1).", e);
        }

        String try2 = null;
        if (System.getenv(MEMBRANE_HOME) != null) {
            try2 = pathFromFileURI(ResolverMap.combine(prefix(System.getenv(MEMBRANE_HOME)), relativeFile));
            try (InputStream ignored = rm.resolve(try2)) {
                return try2;
            } catch (Exception e) {
                log.error("Could not resolve path to configuration (attempt 2).", e);
            }
        }
        System.err.println("Could not find Membrane's configuration file at " + try1 + (try2 == null ? "" : " and not at " + try2) + " " + errorNotice);
        System.exit(1);
        throw new RuntimeException();
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
}