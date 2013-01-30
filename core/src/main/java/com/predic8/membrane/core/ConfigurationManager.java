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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractRefreshableApplicationContext;

import com.predic8.membrane.core.RuleManager.RuleDefinitionSource;
import com.predic8.membrane.core.rules.Rule;

public class ConfigurationManager {

	protected static Log log = LogFactory.getLog(ConfigurationManager.class
			.getName());

	private TrackingFileSystemXmlApplicationContext applicationContext;
	private HotDeploymentThread deploymentThread;
	private boolean hotDeploy = true;
	private String filename;

	private final Router router;
	
	public ConfigurationManager(Router router) {
		this.router = router;
	}

	public void loadConfiguration(String fileName) throws Exception {
		this.filename = fileName;
		
		applicationContext = new TrackingFileSystemXmlApplicationContext(new String[] { fileName }, false, Router.getBeanFactory());
		if (Router.getBeanFactory() != null)
			applicationContext.setClassLoader(Router.getBeanFactory().getClassLoader());
		applicationContext.refresh();
		applicationContext.start();
		
		replaceProxiesRules(router, applicationContext);
		
		if (!fileName.startsWith("classpath:") && hotDeploy) {
			if (deploymentThread == null) {
				deploymentThread = new HotDeploymentThread(router, applicationContext);
				deploymentThread.setFiles(applicationContext.getFiles());
				deploymentThread.start();
			} else {
				deploymentThread.setFiles(applicationContext.getFiles());
			}
		}
	}
	
	private static void replaceProxiesRules(Router router, ApplicationContext applicationContext) throws Exception {
		router.getRuleManager().removeRulesFromSource(RuleDefinitionSource.PROXIES);

		for (Rule rule : applicationContext.getBeansOfType(Rule.class).values()) {
			router.getRuleManager().addProxy(rule, RuleDefinitionSource.PROXIES);
		}
		
		for (Rule rule : applicationContext.getBeansOfType(Rule.class).values())
			rule.init(router);
		
		router.getRuleManager().openPorts();
		
		log.debug("replaced " + RuleDefinitionSource.PROXIES + ".");
	}

	public String getDefaultConfigurationFile() {
		return System.getProperty("user.home")
				+ System.getProperty("file.separator") + ".membrane.xml";
	}

	public Router getRouter() {
		return router;
	}

	public boolean isHotDeploy() {
		return hotDeploy;
	}

	public void setHotDeploy(boolean hotDeploy) {
		this.hotDeploy = hotDeploy;
	}

	public void stopHotDeployment() {
		if (!hotDeploy)
			return;
		try {
			deploymentThread.interrupt();
			deploymentThread.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	public String getFilename() {
		return filename;
	}
	
	public static class HotDeploymentThread extends SpringContextReloader.HotDeploymentThread {
		private Router router;
		
		public HotDeploymentThread(Router router, AbstractRefreshableApplicationContext applicationContext) {
			super(applicationContext);
			this.router = router;
		}

		@Override
		protected void reload() throws Exception {
			// TODO: research what happens if reload() is not overwritten - does proxies context get reloaded twice?
			applicationContext.stop();
			applicationContext.refresh();
			applicationContext.start();
			
			replaceProxiesRules(router, applicationContext);
		}
	}

	public Proxies getProxies() {
		return (Proxies) applicationContext.getBean("proxies");
	}
}
