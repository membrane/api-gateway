/* Copyright 2024 predic8 GmbH, www.predic8.com

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

import org.apache.commons.cli.*;
import java.util.*;

public class CliCommand {
    private final String name;
    private final String description;
    private final Map<String, CliCommand> subcommands;
    private final Options options;
    private CommandLine commandLine;

    public CliCommand(String name, String description) {
        this.name = name;
        this.description = description;
        this.subcommands = new LinkedHashMap<>();
        this.options = new Options();
    }

    public CliCommand addSubcommand(CliCommand namespace) {
        subcommands.put(namespace.getName(), namespace);
        return this;
    }

    public CliCommand addOption(Option option) {
        options.addOption(option);
        return this;
    }

    public CliCommand parse(String[] args) throws ParseException {
        if (isCommand(args)) {
            String cmd = args[0];
            if (hasSubcommand(cmd)) {
                return subcommands.get(cmd).parse(Arrays.copyOfRange(args, 1, args.length));
            } else {
                throw new ParseException("Unknown subcommand: " + cmd);
            }
        }

        commandLine = new DefaultParser().parse(options, args, true);
        return this;
    }

    private static boolean isCommand(String[] args) {
        return args.length > 0 && !args[0].startsWith("-");
    }

    public void printHelp() {
        StringBuilder usage = new StringBuilder(name);
        if (!subcommands.isEmpty()) {
            usage.append(" <command>");
        }

        if (!options.getOptions().isEmpty()) {
            usage.append(" [options]\n\n");
        }

        if (!subcommands.isEmpty()) {
            usage.append("Commands:\n");
            subcommands.forEach((cmd, ns) ->
                    usage.append(" ")
                         .append(cmd)
                         .append(" - ")
                         .append(ns.getDescription())
                         .append("\n")
            );
            usage.append("\n");
        }

        if (!options.getOptions().isEmpty()) {
            usage.append("Options:");
            new HelpFormatter().printHelp(usage.toString(), options);
        } else {
            new HelpFormatter().printHelp(usage.toString(), new Options());
        }
    }

    public boolean isOptionSet(String opt) {
        return commandLine != null && commandLine.hasOption(opt);
    }

    public String getOptionValue(String opt) {
        return commandLine != null ? commandLine.getOptionValue(opt) : null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasSubcommand(String cmd) {
        return subcommands.containsKey(cmd);
    }
}