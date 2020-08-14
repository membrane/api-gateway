/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionStoreException;

import com.predic8.membrane.core.config.spring.CheckableBeanFactory.InvalidConfigurationException;
import com.predic8.membrane.core.config.spring.TrackingFileSystemXmlApplicationContext;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;


public class RouterCLI {

    private static final Logger LOG = LoggerFactory.getLogger(RouterCLI.class);

	public static void main(String[] args) {

		MembraneCommandLine cl = new MembraneCommandLine();
		try {
			cl.parse(args);
			if (cl.needHelp()) {
				cl.printUsage();
				return;
			}

			try {
				Router.init(getRulesFile(cl), RouterCLI.class.getClassLoader());
			} catch (XmlBeanDefinitionStoreException e) {
				TrackingFileSystemXmlApplicationContext.handleXmlBeanDefinitionStoreException(e);
			}
		} catch (InvalidConfigurationException e) {
			System.err.println("Fatal error: " + e.getMessage());
			System.exit(1);
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		new RouterCLI().waitForever();

	}

	private synchronized void waitForever() {
		try {
			wait();
		} catch (InterruptedException e) {
			// do nothing
		}
	}

	private static String getRulesFile(MembraneCommandLine line) {
		ResolverMap rm = new ResolverMap();
		if (line.hasConfiguration()) {
			String s = line.getConfiguration().replaceAll("\\\\", "/");
			if (s.startsWith("file:") || s.startsWith("/") || s.length() > 3 && s.substring(1, 3).equals(":/")) {
				// absolute
				try {
					rm.resolve(s);
					return s;
				} catch (ResourceRetrievalException e) {
					System.err.println("Could not open Membrane's configuration file: " + s + " not found.");
					System.exit(1);
				}
			}
			return getRulesFileFromRelativeSpec(rm, s, "");
		} else {
			String errorNotice = "Please specify the location of Membrane's proxies.xml configuration file using the -c command line option.";
			if (System.getenv("MEMBRANE_HOME") != null) {
				errorNotice += " Or create the file in MEMBRANE_HOME/conf (" + System.getenv("MEMBRANE_HOME") + "/conf/proxies.xml).";
			} else {
				errorNotice += " You can also point the MEMBRANE_HOME environment variable to Membrane's distribution root directory "+
						"and ensure that MEMBRANE_HOME/conf/proxies.xml exists.";
			}
			return getRulesFileFromRelativeSpec(rm, "conf/proxies.xml", errorNotice);
		}
	}

	private static String getRulesFileFromRelativeSpec(ResolverMap rm, String relativeFile, String errorNotice) {
		String membraneHome = System.getenv("MEMBRANE_HOME");
		String userDir = System.getProperty("user.dir").replaceAll("\\\\", "/");
		if (!userDir.endsWith("/"))
			userDir += "/";
		String try1 = ResolverMap.combine(prefix(userDir), relativeFile);
		try {
			rm.resolve(try1);
			return try1;
		} catch (ResourceRetrievalException e) {
		}
		String try2 = null;
		if (membraneHome != null) {
			try2 = ResolverMap.combine(prefix(membraneHome), relativeFile);
			try {
				rm.resolve(try2);
				return try2;
			} catch (ResourceRetrievalException e) {
			}
		}
		System.err.println("Could not find Membrane's configuration file at " + try1 + (try2 == null ? "" : " and not at " + try2) + " . " + errorNotice);
		System.exit(1);
		throw new RuntimeException();
	}

	private static String prefix(String dir) {
		if (dir.startsWith("/"))
			return "file://" + dir;
		return dir;
	}

}
