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
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class Router {

	public static final String MEMROUTER_HOME = "MEMROUTER_HOME";

	public static void main(String[] args) {

		Options routerOptions = new Options();
		routerOptions.addOption("h", "help", false, "Help content for router.");
		routerOptions.addOption("c", "config", true, "configuration file name.");
		routerOptions.addOption("b", "beans", true, "spring configuration file name.");

		CommandLineParser clParser = new BasicParser();

		CommandLine commandLine = null;
		try {
			commandLine = clParser.parse(routerOptions, args);
			if (commandLine.hasOption('h') ) {
				System.out.println("-h Help content for router.");
				System.out.println("--help 'Help content for router.");
				System.out.println("-c 'configurationFileName'");
				System.out.println("--config 'configurationFileName'");
				System.out.println("-b 'springConfigurationFileName'.");
				return;
			}
			
			Resource configResource = new ClassPathResource("router-beans.xml");
			String rulesFile = "";
			if (commandLine.hasOption('c')) {
				rulesFile = commandLine.getOptionValue('c'); 
			} else {
				rulesFile =  System.getenv("MEMROUTER_HOME") +  "/conf/rules.xml" ;
			}
			
			if (commandLine.hasOption('b')) {
				configResource = new FileSystemResource(commandLine.getOptionValue('b'));
			}
			
			Core.init(configResource);
			
			XmlBeanFactory beanFactory = new XmlBeanFactory(configResource);
			
			ConfigurationManager manager = (ConfigurationManager) (beanFactory.getBean("configurationManager"));
	    	try {
	    		manager.loadConfiguration(rulesFile);
	    	} catch (Exception ex) {
	    	    ex.printStackTrace();
	    	    System.exit(1);
	    	}
	    	
	    	while(true) {
	    		
			}
	    	
		} catch (ParseException pvException) {
			System.out.println(pvException.getMessage());
		}

	}

}
