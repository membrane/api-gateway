/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Intellij Fix: Run Config/add distribution to working directory
 */
public class IDEStarter {
	public static void main(String[] args) {
		// for testing or development purposes
		// Start in "distribution" folder
		try {
			File file = new File("conf/log4j2.xml");
			Configurator.initialize(null, new ConfigurationSource(new FileInputStream(file)));
		} catch (IOException e) {
			LoggerFactory.getLogger(IDEStarter.class).error("Error loading log configuration.");
		}

		RouterCLI.main(args);
	}

}
