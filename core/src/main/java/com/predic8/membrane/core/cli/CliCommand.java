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

import com.predic8.membrane.core.util.Pair;
import org.apache.commons.cli.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.apache.commons.lang3.StringUtils.trim;

public class CliCommand {
    private final String name;
    private final String description;
    private final List<Pair<String, String>> examples;
    private final Map<String, CliCommand> subcommands;
    private Options options;
    private CliCommand parent;
    private CommandLine commandLine;

    public CliCommand(String name, String description) {
        this.name = name;
        this.description = description;
        this.subcommands = new LinkedHashMap<>();
        this.options = new Options();
        this.examples = new ArrayList<>();
    }

    public CliCommand addSubcommand(CliCommand command) {
        subcommands.put(command.getName(), command);
        command.parent = this;
        return this;
    }

    public CliCommand addOption(Option option) {
        options.addOption(option);
        return this;
    }

    public CliCommand addExample(String exampleDescription, String exampleCommand) {
        examples.add(new Pair<>(exampleDescription, exampleCommand));
        return this;
    }

    public CliCommand parse(String[] args) throws ParseException {
        if (isCommand(args)) {
            String cmd = args[0];
            if (hasSubcommand(cmd)) {
                return subcommands.get(cmd).parse(Arrays.copyOfRange(args, 1, args.length));
            }
            throw new ParseException("Unknown command: " + cmd);
        }

        try {
            commandLine = new DefaultParser().parse(options, args, true);
        } catch (MissingOptionException e) {
            throw new MissingRequiredOptionException(e.getMessage(), this);
        }

        return this;
    }

    private static boolean isCommand(String[] args) {
        return args.length > 0 && !args[0].startsWith("-");
    }

    private boolean hasSubcommand(String cmd) {
        return subcommands.containsKey(cmd);
    }

    String getCommandPath() {
        if (parent == null) {
            return name;
        }
        return parent.getCommandPath() + " " + name;
    }

    public void printHelp() {
        new HelpFormatter().printHelp(getUsageHelp(), getCommandHelp(), options, getExamplesHelp());
    }

    private @NotNull String getUsageHelp() {
        StringBuilder usage = new StringBuilder(getCommandPath());
        if (!subcommands.isEmpty()) {
            usage.append(" <command>");
        }
        if (!options.getOptions().isEmpty()) {
            usage.append(" [options]\n\n");
        }
        return usage.toString();
    }

    private @NotNull String getExamplesHelp() {
        StringBuilder examples = new StringBuilder();
        if (!this.examples.isEmpty()) {
            examples.append("\nExamples:\n");
            this.examples.forEach(example ->
                    examples.append(" ")
                            .append(example.first())
                            .append("\n    ")
                            .append(example.second())
                            .append("\n"));
        }
        return examples.toString();
    }

    private @NotNull String getCommandHelp() {
        StringBuilder commands = new StringBuilder();
        if (!subcommands.isEmpty()) {
            commands.append("Commands:\n");
            subcommands.forEach((cmd, ns) ->
                    commands.append(" ")
                            .append(cmd)
                            .append(" - ")
                            .append(ns.getDescription())
                            .append("\n")
            );
            commands.append("\n");
        }
        if (!options.getOptions().isEmpty()) {
            commands.append("Options:\n");
        }
        return commands.toString();
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public boolean isOptionSet(String opt) {
        return commandLine != null && commandLine.hasOption(opt);
    }

    public String getOptionValue(String opt) {
        return commandLine != null ? trim(commandLine.getOptionValue(opt)) : null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}