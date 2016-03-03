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

public class MembraneCommandLine {

	CommandLine cl;

	public void parse(String[] args) throws ParseException {
		cl = new DefaultParser().parse(getOptions(), args, true);
	}

	public void printUsage() {
		System.out.println("-h              Help content for router.");
		System.out.println("--help          Help content for router.");
		System.out.println("-c <location>   Location of the proxies configuration file");
	}

	public boolean needHelp() {
		return cl.hasOption('h');
	}

	public boolean hasConfiguration() {
		return cl.hasOption('c');
	}

	public String getConfiguration() {
		return cl.getOptionValue('c');
	}

	private Options getOptions() {
		Options options = new Options();
		options.addOption("h", "help", false, "Help content for router.");
		options.addOption("c", "config", true, "configuration file name.");
		return options;
	}

}
