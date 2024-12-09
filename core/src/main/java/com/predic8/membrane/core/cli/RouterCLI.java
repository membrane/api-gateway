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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.URIFactory;
import org.apache.commons.cli.*;
import org.slf4j.*;
import org.springframework.beans.factory.xml.*;

import java.io.*;
import java.util.List;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.config.spring.TrackingFileSystemXmlApplicationContext.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.core.util.OSUtil.*;
import static java.lang.Integer.parseInt;

public class RouterCLI {

    private static final Logger log = LoggerFactory.getLogger(RouterCLI.class);

    public static void main(String[] args) {
        MembraneCommandLine commandLine = getMembraneCommandLine(args);
        if (commandLine.getCommand().isOptionSet("h")) {
            commandLine.getCommand().printHelp();
            System.exit(0);
        }

        Router router = null;
        try {
            if (commandLine.getCommand().getName().equals("oas")) {
                router = initRouterByOpenApiSpec(commandLine);
            } else {
                router = initRouterByConfig(commandLine);
            }
        } catch (InvalidConfigurationException e) {
            log.error("Fatal error: " + e.getMessage());
            System.exit(1);
        }
        catch (Exception ex) {
            SpringConfigurationErrorHandler.handleRootCause(ex,log);
            System.exit(1);
        }

        // Dry run
        if (commandLine.noCommand() && commandLine.getCommand().isOptionSet("t")) {
            System.exit(0);
        }

        try {
            if (router != null)
                router.waitFor();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private static Router initRouterByOpenApiSpec(MembraneCommandLine commandLine) throws Exception {
        Router router = new HttpRouter();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();

        spec.location = commandLine.getCommand().getOptionValue("l");

        if (commandLine.getCommand().isOptionSet("v"))
            spec.setValidateRequests(YES);
        if (commandLine.getCommand().isOptionSet("V"))
            spec.setValidateResponses(YES);

        APIProxy api = new APIProxy();
        api.setPort(commandLine.getCommand().isOptionSet("p") ?
                parseInt(commandLine.getCommand().getOptionValue("p")) : 2000);
        api.setSpecs(List.of(spec));
        router.getRuleManager().addProxyAndOpenPortIfNew(api);

        router.init();
        return router;
    }

    private static Router initRouterByConfig(MembraneCommandLine commandLine) throws InvalidConfigurationException, IOException {
        try {
            return Router.init(getRulesFile(commandLine), RouterCLI.class.getClassLoader());
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
                try(InputStream ignored = rm.resolve(filename)) {
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
        String try1 = ResolverMap.combine(prefix(getUserDir()), relativeFile);
        try(InputStream ignored = rm.resolve(try1)) {
            return try1;
        } catch (Exception e) {
            // ignored
        }

        String membraneHome = System.getenv(MEMBRANE_HOME);
        String try2 = null;
        if (membraneHome != null) {
            try2 = ResolverMap.combine(prefix(membraneHome), relativeFile);
            try(InputStream ignored =  rm.resolve(try2)) {
                return try2;
            } catch (Exception e) {
                // ignored
            }
        }
        System.err.println("Could not find Membrane's configuration file at " + try1 + (try2 == null ? "" : " and not at " + try2) + " . " + errorNotice);
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