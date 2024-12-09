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

import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

public class CliCommandTest {

    private static CliCommand rootCommand;

    @BeforeAll
    static void setUp() {
        rootCommand = new CliCommand("root", "Root command");
        rootCommand.addOption(Option.builder("a")
                        .longOpt("option-a")
                        .hasArg()
                        .desc("Option A")
                        .build())
                .addOption(Option.builder("b")
                        .desc("Flag B")
                        .build())
                .addExample("Example Number 1", "root sub")
                .addExample("Example Number 2", "root without");


        CliCommand subCommand = new CliCommand("sub", "Sub command");
        subCommand.addOption(Option.builder("x")
                        .hasArg()
                        .desc("Option X")
                        .build())
                .addOption(Option.builder("y")
                        .desc("Flag Y")
                        .build());

        CliCommand subCommandWithoutOptions = new CliCommand("without", "Sub command without options");

        CliCommand subCommandWithRequiredOption = new CliCommand("required", "Sub command with required options");
        subCommandWithRequiredOption.addOption(Option.builder("z")
                        .required()
                        .desc("Required option Z")
                        .build());

        rootCommand.addSubcommand(subCommand);
        rootCommand.addSubcommand(subCommandWithoutOptions);
        rootCommand.addSubcommand(subCommandWithRequiredOption);
    }

    @Test
    void shouldParseRootCommandOptions() throws ParseException {
        CliCommand result = rootCommand.parse(new String[]{"-b", "-a", "value"});

        assertEquals("root", result.getName());
        assertTrue(result.isOptionSet("a"));
        assertEquals("value", result.getOptionValue("a"));
        assertTrue(result.isOptionSet("b"));
        assertNull(result.getOptionValue("b"));
    }

    @Test
    void shouldParseSubCommandOptions() throws ParseException {
        CliCommand result = rootCommand.parse(new String[]{"sub", "-y", "-x", "sub value"});

        assertEquals("sub", result.getName());
        assertTrue(result.isOptionSet("x"));
        assertEquals("sub value", result.getOptionValue("x"));
        assertTrue(result.isOptionSet("y"));
        assertNull(result.getOptionValue("y"));
    }

    @Test
    void shouldThrowErrorForMissingRequiredOption() {
        assertThrows(MissingOptionException.class, () -> rootCommand.parse(new String[]{"required"}));
    }

    @Test
    void shouldThrowParseExceptionForUnknownSubcommand() {
        ParseException exception = assertThrows(ParseException.class, () ->
                rootCommand.parse(new String[]{"unknown"})
        );
        assertEquals("Unknown subcommand: unknown", exception.getMessage());
    }

    @Test
    void shouldReturnFullCommandPath() throws ParseException {
        CliCommand cmd = rootCommand.parse(new String[]{"sub"});
        assertEquals("root sub", cmd.getCommandPath());
    }

    @Test
    void shouldPrintRootHelpWithSubcommandsAndOptions() throws ParseException {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        rootCommand.parse(new String[]{"-b"}).printHelp();
        String output = outContent.toString();
        assertEquals("""
                usage: root <command> [options]
                
                Commands:
                 sub - Sub command
                 without - Sub command without options
                 required - Sub command with required options
                
                Options:
                 -a,--option-a <arg>   Option A
                 -b                    Flag B
                
                Examples:
                 Example Number 1
                    root sub
                 Example Number 2
                    root without
                """, output);

        System.setOut(System.out);
    }

    @Test
    void shouldPrintSubHelpWithOptions() throws ParseException {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        rootCommand.parse(new String[]{"sub", "-y"}).printHelp();
        String output = outContent.toString();
        assertEquals("""
                usage: root sub [options]
                
                Options:
                 -x <arg>   Option X
                 -y         Flag Y
                """, output);

        System.setOut(System.out);
    }

    @Test
    void shouldPrintSubHelpWithoutOptions() throws ParseException {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        rootCommand.parse(new String[]{"without"}).printHelp();
        String output = outContent.toString();
        assertEquals("""
                usage: root without
                
                """, output);

        System.setOut(System.out);
    }

    @Test
    void shouldHandleEmptyArgs() throws ParseException {
        CliCommand result = rootCommand.parse(new String[]{});
        assertFalse(result.isOptionSet("a"));
        assertNull(result.getOptionValue("a"));
    }
}