/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class RouterCLI {

	public static void main(String[] args) throws ParseException {

		CommandLine commandLine = new BasicParser().parse(getOptions(), args);
		if (commandLine.hasOption('h')) {
			System.out.println("-h Help content for router.");
			System.out.println("--help 'Help content for router.");
			System.out.println("-c 'configurationFileName'");
			System.out.println("--config 'configurationFileName'");
			System.out.println("-b 'springConfigurationFileName'.");
			return;
		}

		try {
			Router.init(getConfigFile(commandLine), RouterCLI.class.getClassLoader()).getConfigurationManager().loadConfiguration(getRulesFile(commandLine));
		} catch (ClassNotFoundException e) {
		
			e.printStackTrace();
			
		} catch (Exception ex) {
			ex.printStackTrace();
			System.err.println("Could not read rules configuration. Please specify a file containing rules using the -c command line option. Or make sure that the file " + System.getenv("MEMBRANE_HOME") + "/conf/rules.xml exists");
			System.exit(1);
		}

		
		new RouterCLI().waitForever();
		
	}

	private synchronized void waitForever() {
		try {
			wait();
		} catch (InterruptedException e) {
			
		}
	}
	
	private static String getRulesFile(CommandLine line) {
		if (line.hasOption('c')) {
			return line.getOptionValue('c');
		} else {
			return System.getenv("MEMBRANE_HOME") +  System.getProperty("file.separator") + "conf" + System.getProperty("file.separator") + "rules.xml";
		}
	}

	private static String getConfigFile(CommandLine line) {
		if (line.hasOption('b')) {
			return "file:" + line.getOptionValue('b');
		}
		return System.getenv("MEMBRANE_HOME") +  System.getProperty("file.separator") + "conf"  + System.getProperty("file.separator") + "monitor-beans.xml";
	}

	private static Options getOptions() {
		Options options = new Options();
		options.addOption("h", "help", false, "Help content for router.");
		options.addOption("c", "config", true, "configuration file name.");
		options.addOption("b", "beans", true, "spring configuration file name.");
		return options;
	}

}
