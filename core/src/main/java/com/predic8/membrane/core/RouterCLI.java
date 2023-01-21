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

package com.predic8.membrane.core;

import java.io.File;

import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.util.*;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionStoreException;

import com.predic8.membrane.core.config.spring.CheckableBeanFactory.InvalidConfigurationException;
import com.predic8.membrane.core.config.spring.TrackingFileSystemXmlApplicationContext;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;

import static com.predic8.membrane.core.Constants.MEMBRANE_HOME;
import static com.predic8.membrane.core.config.spring.TrackingFileSystemXmlApplicationContext.handleXmlBeanDefinitionStoreException;
import static com.predic8.membrane.core.util.OSUtil.fixBackslashes;
import static com.predic8.membrane.core.util.OSUtil.getOS;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

public class RouterCLI {

    private static final Logger LOG = LoggerFactory.getLogger(RouterCLI.class);

    public static void main(String[] args) {

        MembraneCommandLine cl = getMembraneCommandLine(args);

        Router router = null;
        try {
            try {
                router = Router.init(getRulesFile(cl), RouterCLI.class.getClassLoader());
            } catch (XmlBeanDefinitionStoreException e) {
                handleXmlBeanDefinitionStoreException(e);
            }
        } catch (InvalidConfigurationException e) {
            System.err.println("Fatal error: " + e.getMessage());
            System.exit(1);
        } catch (Exception ex) {
            Throwable rootCause = getRootCause(ex);
            if (rootCause instanceof ExitException ee)
                handleExitException(ee);
            else if (rootCause instanceof PortOccupiedException poe)
                handlePortOccupiedException(poe);
            else
                ex.printStackTrace();
            System.exit(1);
        }

        try {
            if (router != null)
                router.waitFor();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private static MembraneCommandLine getMembraneCommandLine(String[] args) {
        MembraneCommandLine cl = new MembraneCommandLine();

        try {
            cl.parse(args);
        } catch (ParseException e) {
            System.err.println("Error parsing commandline " + e.getMessage());
            cl.printUsage();
            System.exit(1);
        }

        if (cl.needHelp()) {
            cl.printUsage();
            System.exit(0);
        }
        return cl;
    }

    private static void handlePortOccupiedException(PortOccupiedException poe) {
        printStars();
        System.err.println();
        System.err.printf("Membrane is configured to open port %d. But this port is alreay in\n", poe.getPort());
        System.err.println("""
                use by a different program. To start Membrane do one of the following:
                                
                1. Find and stop the program that is occupying the port. Then restart Membrane.""");
        System.err.println();
        switch (getOS()) {
            case WINDOWS -> printHowToFindPortWindows();
            case LINUX, MAC -> printHowToFindPortLinux();
        }
        System.err.println("""       
                2. Configure Membrane to use a different port. Propably in the conf/proxies.xml
                file. Then restart Membrane.
                """);
    }

    private static void printHowToFindPortWindows() {
        System.err.println("""
                netstat -aon | find /i "listening"
                """);
    }

    private static void printHowToFindPortLinux() {
        System.err.println("""
                e.g.:
                > lsof -i :2000
                COMMAND    PID    USER  TYPE
                java     80910 predic8  IPv6  TCP  (LISTEN)
                > kill -9 80910
                """);
    }

    private static void handleExitException(ExitException exitException) {
        printStars();
        System.err.println();
        System.err.println(exitException.getMessage());
        System.err.println();
    }

    private static void printStars() {
        System.err.println("**********************************************************************************");
    }

    private static String getRulesFile(MembraneCommandLine line) {
        ResolverMap rm = new ResolverMap();
        if (line.hasConfiguration()) {
            String s = fixBackslashes(line.getConfiguration());
            if (shouldResolveFile(s)) {
                // absolute
                try {
                    rm.resolve(s);
                    return s;
                } catch (ResourceRetrievalException e) {
                    System.err.println("Could not open Membrane's configuration file: " + s + " not found.");
                    System.exit(1);
                }
            }
            return getRulesFileFromRelativeSpec(rm, s, "");
        }
        return getRulesFileFromRelativeSpec(rm, "conf/proxies.xml", getErrorNotice());
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
        String membraneHome = System.getenv(MEMBRANE_HOME);
        String try1 = ResolverMap.combine(prefix(getUserDir()), relativeFile);
        try {
            rm.resolve(try1);
            return try1;
        } catch (ResourceRetrievalException e) {
            // ignored
        }
        String try2 = null;
        if (membraneHome != null) {
            try2 = ResolverMap.combine(prefix(membraneHome), relativeFile);
            try {
                rm.resolve(try2);
                return try2;
            } catch (ResourceRetrievalException e) {
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
