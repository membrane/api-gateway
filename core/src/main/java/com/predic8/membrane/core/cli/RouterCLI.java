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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.spring.TrackingFileSystemXmlApplicationContext;
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
import org.jose4j.lang.JoseException;
import org.slf4j.*;
import org.springframework.beans.factory.xml.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.config.spring.TrackingFileSystemXmlApplicationContext.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isOpenAPIMisplacedError;
import static com.predic8.membrane.core.util.ExceptionUtil.*;
import static com.predic8.membrane.core.util.OSUtil.*;
import static com.predic8.membrane.core.util.URIUtil.*;
import static java.lang.Integer.*;
import static org.apache.commons.lang3.exception.ExceptionUtils.getMessage;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

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
            try {
                String proxies = getRulesFile(commandLine);
                TrackingFileSystemXmlApplicationContext bf =
                        new TrackingFileSystemXmlApplicationContext(new String[]{proxies}, false);
                bf.refresh();
            } catch (Throwable e) {
                System.err.println(getExceptionMessageWithCauses(e));
                System.exit(1);
            }
            System.exit(0);
        }

        if (commandLine.getCommand().getName().equals("generate-jwk")) {
            generateJWK(commandLine);
            System.exit(0);
        }

        if (commandLine.getCommand().getName().equals("private-jwk-to-public")) {
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

        try {
            getRouter(commandLine).waitFor();
        } catch (InterruptedException e) {
            // do nothing
        }
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
        } catch (Exception ex) {
            SpringConfigurationErrorHandler.handleRootCause(ex, log);
        }
        System.exit(1);
        // Will never be reached
        return null;
    }

    private static Router initRouterByConfig(MembraneCommandLine commandLine) throws Exception {
        String config = getRulesFile(commandLine);
        if(config.endsWith(".xml")) {
            return initRouterByXml(commandLine);
        } else if (config.endsWith(".yaml") || config.endsWith(".yml")) {
            return initRouterByYAML(commandLine, "c");
        }else {
            throw new RuntimeException("Unsupported file extension.");
        }
    }

    private static Router initRouterByOpenApiSpec(MembraneCommandLine commandLine) throws Exception {
        Router router = new HttpRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(getApiProxy(commandLine));
        router.init();
        return router;
    }

    private static Router initRouterByYAML(MembraneCommandLine commandLine, String option) throws Exception {
        String location = commandLine.getCommand().getOptionValue(option);
        var fileReader = new FileReader(location);

        var router = new HttpRouter();
        router.setBaseLocation(location);
        router.setHotDeploy(false);
        router.setAsynchronousInitialization(true);
        router.start();

        var beanCache = new BeanCache(router);
        beanCache.start();

        YAMLParser parser = new YAMLFactory().createParser(new File(location));
        var om = new ObjectMapper();
        int count = 0;
        while (!parser.isClosed()) {
            Map<?, ?> m = om.readValue(parser, Map.class);
            Map<Object, Object> meta = (Map<Object, Object>) m.get("metadata");

            if (meta == null) {
                // generate name, if it doesnt exist
                meta = new HashMap<>();
                ((Map<Object, Object>)m).put("metadata", meta);
                meta.put("name", "artifact" + ++count);
                meta.put("uid", UUID.randomUUID().toString());
            } else {
                // fake UID
                meta.put("uid", location + "-" + meta.get("name"));
            }

            beanCache.handle(WatchAction.ADDED, m);
            parser.nextToken();
        }

        beanCache.fireConfigurationLoaded();

        return router;
    }

    private static void generateJWK(MembraneCommandLine commandLine) {
        int bits = 2048;
        String bitsArg = commandLine.getCommand().getOptionValue("b");
        if (bitsArg != null) {
            bits = Integer.parseInt(bitsArg);
        }

        boolean overwrite = commandLine.getCommand().isOptionSet("overwrite");
        String outputFile = commandLine.getCommand().getOptionValue("o");

        if (outputFile == null) {
            log.error("Missing required option: -o <output file>");
            commandLine.getCommand().printHelp();
            System.exit(1);
        }

        RsaJsonWebKey rsaJsonWebKey = null;
        try {
            rsaJsonWebKey = RsaJwkGenerator.generateJwk(bits);
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
        rsaJsonWebKey.setKeyId(new BigInteger(130, new SecureRandom()).toString(32));
        rsaJsonWebKey.setUse("sig");
        rsaJsonWebKey.setAlgorithm("RS256");

        Path path = Paths.get(outputFile);
        if (path.toFile().exists() && !overwrite) {
            log.error("Output file ({}) already exists.", outputFile);
            System.exit(1);
        }
        try {
            Files.writeString(path, rsaJsonWebKey.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE));
        } catch (IOException e) {
            log.error(e.getMessage());
            System.exit(1);
        }
    }

    private static void privateJWKtoPublic(String input, String output) {
        try {
            Map map = new ObjectMapper().readValue(new File(input), Map.class);
            RsaJsonWebKey rsa = new RsaJsonWebKey(map);
            Files.writeString(Paths.get(output), rsa.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY));
        } catch (IOException | JoseException e) {
            log.error(e.getMessage());
            System.exit(1);
        }
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

    private static Router initRouterByXml(MembraneCommandLine commandLine) throws Exception {
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