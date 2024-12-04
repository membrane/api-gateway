/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import org.apache.commons.cli.*;
import java.util.Arrays;

public class MembraneCommandLine {

    private CommandLine cl;
    private String command;
    private static final String[] VALID_COMMANDS = {"start", "stop", "restart", "status"}; // add your commands here

    public void parse(String[] args) throws ParseException {
        if (args.length > 0 && !args[0].startsWith("-")) {
            command = args[0];
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        cl = new DefaultParser().parse(getOptions(), args, true);
    }

    public void printUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("service-proxy.sh <command> [options]\n\nCommands:\n  " +
                String.join("\n  ", VALID_COMMANDS) + "\n\nOptions:", getOptions());
    }

    public String getCommand() {
        return command;
    }

    public boolean hasValidCommand() {
        return command != null && Arrays.asList(VALID_COMMANDS).contains(command);
    }

    public boolean needHelp() {
        return cl == null || cl.hasOption('h');
    }

    public boolean hasConfiguration() {
        return cl.hasOption('c') || cl.hasOption('t');
    }

    public String getConfiguration() {
        return (cl.hasOption('c')) ? cl.getOptionValue('c') : cl.getOptionValue('t');
    }

    public boolean hasOpenApiSpec() {
        return cl.hasOption("oas");
    }

    public String getOpenApiSpec() {
        return cl.getOptionValue("oas");
    }

    public boolean hasRequestValidation() {
        return cl.hasOption("v");
    }

    public boolean hasResponseValidation() {
        return cl.hasOption("V");
    }

    public boolean hasPort() {
        return cl.hasOption("p");
    }

    public String getPort() {
        return cl.getOptionValue("p");
    }

    private Options getOptions() {
        Options options = new Options();
        options.addOption(Option.builder("h").longOpt("help").desc("Help content for router.").build());
        options.addOption(Option.builder("c").longOpt("config").argName("proxies.xml location").hasArg().desc("Location of the proxies configuration file").build());
        options.addOption(Option.builder("t").longOpt("test").argName("proxies.xml location").hasArg().desc("Verify proxies configuration file").build());
        options.addOption(Option.builder("oas").longOpt("openapi").argName("OpenAPI location").hasArg().desc("Location of OpenAPI file").build());
        options.addOption(Option.builder("v").longOpt("validate-requests").argName("Validate OpenAPI Requests").desc("Enable validation of requests against set OpenAPI").build());
        options.addOption(Option.builder("V").longOpt("validate-responses").argName("Validate OpenAPI Responses").desc("Enable validation of responses against set OpenAPI").build());
        options.addOption(Option.builder("p").longOpt("port").argName("API Port").hasArg().desc("Port the default API or OpenAPI should initialize").build());
        return options;
    }

    public boolean isDryRun() {
        return cl.hasOption('t');
    }
}
