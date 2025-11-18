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
import org.jetbrains.annotations.*;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static org.apache.commons.cli.Option.*;

public class MembraneCommandLine {
    private final CliCommand rootNamespace;
    private CliCommand currentNamespace;

    public MembraneCommandLine() {
        rootNamespace = getRootNamespace(getRootOptions());
    }

    private static Options getRootOptions() {
        return new Options().addOption(builder("h").longOpt("help").desc("Display this text").build())
                .addOption(builder("c").longOpt("config").argName("apis.yaml or proxies.xml").hasArg().desc("Location of the configuration file").build())
                .addOption(builder("t").longOpt("test").argName("apis.yaml or proxies.xml").hasArg().desc("Verifies configuration file and terminates").build());
    }

    private static @NotNull CliCommand getRootNamespace(Options rootOptions) {
        String ext = isWindows() ? "cmd" : "sh";
        return new CliCommand("membrane." + ext, "Membrane Service Proxy") {{
            setOptions(rootOptions);

            addExample("Start gateway with its default configuration file in conf/apis.yaml",
                "membrane." + ext)
                    .addExample("Start gateway configured from OpenAPI",
                            "membrane." + ext + " oas -l conf/fruitshop-api.yml")
                    .addExample("Start with configuration from OpenAPI URL and validate requests",
                            "membrane." + ext + " oas -v -l https://api.predic8.de/shop/v2/api-docs");

            addSubcommand(new CliCommand("start", " (Default) Same function as command omitted. Start gateway with configuration from proxies.xml") {{
                setOptions(rootOptions);
            }});

            addSubcommand(new CliCommand("oas", "Use a single OpenAPI document to configure and start gateway") {{
                addOption(builder("h").longOpt("help").desc("Display this text").build())
                        .addOption(builder("l").longOpt("location").argName("OpenAPI location").hasArg().required().desc("(Required) Set URL or path to an OpenAPI document").build())
                        .addOption(builder("p").longOpt("port").argName("API port").hasArg().desc("Listen port").build())
                        .addOption(builder("v").longOpt("validate-requests").desc("Validate requests against OpenAPI").build())
                        .addOption(builder("V").longOpt("validate-responses").desc("Validate responses against OpenAPI").build());

            }});

            addSubcommand(new CliCommand("generate-jwk", "Generate a JSON Web Key and write it to a file") {{
                addOption(builder("o").longOpt("output").argName("file").hasArg().required().desc("Output file for JWK").build());
                addOption(builder("b").longOpt("bits").argName("bitlength").hasArg().desc("Key length in bits (default: 2048)").build());
                addOption(builder("overwrite").desc("Overwrite the output file, if it exists.").build());
            }});

            addSubcommand(new CliCommand("private-jwk-to-public", "Convert a private JWK to a public JWK.") {{
                addOption(builder("i").longOpt("input").argName("file").hasArg().desc("Input file (JWK, private).").build());
                addOption(builder("o").longOpt("output").argName("file").hasArg().desc("Output file (JWK, public).").build());
            }});

        }};
    }

    public void parse(String[] args) throws ParseException {
        currentNamespace = rootNamespace.parse(args);
    }

    public CliCommand getRootNamespace() {
        return rootNamespace;
    }

    public CliCommand getCommand() {
        return currentNamespace;
    }

    public boolean noCommand() {
        return currentNamespace == rootNamespace;
    }
}