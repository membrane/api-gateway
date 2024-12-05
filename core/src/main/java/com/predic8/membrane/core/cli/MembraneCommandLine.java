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

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class MembraneCommandLine {
    private final CliCommand rootNamespace;
    private CliCommand currentNamespace;

    public MembraneCommandLine() {
        Options rootOptions = new Options().addOption(Option.builder("h").longOpt("help").desc("Display this help screen").build())
                .addOption(Option.builder("c").longOpt("config").argName("proxies.xml Location").hasArg().desc("Location of the proxies configuration file").build())
                .addOption(Option.builder("t").longOpt("test").argName("proxies.xml Location").hasArg().desc("Verifies configuration file and terminates").build());

        rootNamespace = new CliCommand("service-proxy.sh", "Membrane Service Proxy") {{
            setOptions(rootOptions);

            addExample("Start gateway configured from OpenAPI file",
                        "service-proxy.sh oas -l conf/fruitshop-api.yml")
            .addExample("Start gateway configured from OpenAPI URL and validate requests",
                        "service-proxy.sh oas -v -l https://api.predic8.de/shop/v2/api-docs");

            addSubcommand(new CliCommand("start", " (Default) Same function as command omitted. Start gateway with configuration from proxies.xml") {{
                setOptions(rootOptions);
            }});

            addSubcommand(new CliCommand("oas", "Use a single OpenAPI document to configure and start gateway") {{
                addOption(Option.builder("h").longOpt("help").desc("Display this help screen").build())
                .addOption(Option.builder("l").longOpt("location").argName("OpenAPI Location").hasArg().required().desc("(Required) Set URL or path to an OpenAPI document").build())
                .addOption(Option.builder("p").longOpt("port").argName("API Port").hasArg().desc("Port the OpenAPI should initialize").build())
                .addOption(Option.builder("v").longOpt("validate-requests").desc("Enable validation of requests against set OpenAPI").build())
                .addOption(Option.builder("V").longOpt("validate-responses").desc("Enable validation of responses against set OpenAPI").build());

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