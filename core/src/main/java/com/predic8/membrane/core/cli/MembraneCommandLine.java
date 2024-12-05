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

import static org.apache.commons.cli.Option.*;

public class MembraneCommandLine {
    private final CliCommand rootNamespace;
    private CliCommand currentNamespace;

    public MembraneCommandLine() {
        rootNamespace = getRootNamespace(getRootOptions());
    }

    private static Options getRootOptions() {
        return new Options().addOption(builder("h").longOpt("help").desc("Display this text").build())
                .addOption(builder("c").longOpt("config").argName("proxies.xml location").hasArg().desc("Location of the proxies configuration file").build())
                .addOption(builder("t").longOpt("test").argName("proxies.xml location").hasArg().desc("Verifies configuration file and terminates").build());
    }

    private static @NotNull CliCommand getRootNamespace(Options rootOptions) {
        return new CliCommand("service-proxy.sh", "Membrane Service Proxy") {{
            setOptions(rootOptions);

            addExample("Start gateway configured from OpenAPI file",
                    "service-proxy.sh oas -l conf/fruitshop-api.yml")
                    .addExample("Start gateway configured from OpenAPI URL and validate requests",
                            "service-proxy.sh oas -v -l https://api.predic8.de/shop/v2/api-docs");

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