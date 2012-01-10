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

package com.predic8.plugin.membrane.wizards;


import java.io.IOException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class AddProxyWizard extends Wizard {

	private ProxyTypeSelectionPage selectionWizardPage = new ProxyTypeSelectionPage();

	private WSDLProxyConfigurationPage wsdlProxyPage = new WSDLProxyConfigurationPage();
	
	ListenPortConfigurationPage listenPortConfigPage = new ListenPortConfigurationPage();

	private TargetConfigurationPage targetHostConfigPage = new TargetConfigurationPage();

	private AdvancedProxyConfigurationPage advancedProxyConfigPage = new AdvancedProxyConfigurationPage();

	private ProxyRuleConfigurationPage proxyRuleConfigPage = new ProxyRuleConfigurationPage();
	
	public AddProxyWizard() {
		setWindowTitle("Add a new Proxy");
	}

	@Override
	public void addPages() {
		addPage(selectionWizardPage);
		addPage(wsdlProxyPage);
		addPage(listenPortConfigPage);
		addPage(advancedProxyConfigPage);
		addPage(targetHostConfigPage);
		addPage(proxyRuleConfigPage);
	}
	
	@Override
	public boolean performFinish() {
		try {
			return getCurrentPage().performFinish(this);
		} catch (Exception ex) {
			openWarningDialog(ex.getMessage());
			return false;
		}
	}

	void createServiceProxy(ServiceProxyKey ruleKey) throws IOException {
		ServiceProxy rule = new ServiceProxy();
		rule.setTargetHost(targetHostConfigPage.getTargetHost());
		rule.setTargetPort(Integer.parseInt(targetHostConfigPage.getTargetPort()));
		rule.setKey(ruleKey);
		rule.setInboundTLS(advancedProxyConfigPage.getSecurityGroup().getSecureConnection());
		rule.setOutboundTLS(targetHostConfigPage.getSecurityGroup().getSecureConnection());
		
		getRuleManager().addRuleIfNew(rule);
	}

	@Override
	public boolean performCancel() {
		return true;
	}

	@Override
	public boolean canFinish() {
		return getCurrentPage().canFinish();		
	}

	private AbstractProxyWizardPage getCurrentPage() {
		return ((AbstractProxyWizardPage)getContainer().getCurrentPage());
	}

	void openWarningDialog(String msg) {
		MessageDialog.openWarning(this.getShell(), "Warning", msg);
	}
	
	private int getListenPort() {
		return Integer.parseInt(advancedProxyConfigPage.getListenPort());
	}

	private String getListenHost() {
		return advancedProxyConfigPage.getListenHost();
	}

	private String getMethod() {
		return advancedProxyConfigPage.getMethod();
	}

	ServiceProxyKey getRuleKey() {
		if (advancedProxyConfigPage.getUsePathPatter()) {
			ServiceProxyKey key = new ServiceProxyKey(getListenHost(), getMethod(), advancedProxyConfigPage.getPathPattern(), getListenPort());
			key.setUsePathPattern(true);
			key.setPathRegExp(advancedProxyConfigPage.isRegExp());
			return key;
		} 
		return  new ServiceProxyKey(getListenHost(), getMethod(), ".*", getListenPort());
	}

	boolean checkIfSimilarRuleExists() {
		if (getRuleManager().exists(getRuleKey())) {
			openWarningDialog("You've entered a duplicated rule key.");
			return true;
		}
		return false;
	}
	
	protected RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}

	protected HttpTransport getHttpTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}
	
	void addProxy() throws IOException {
		createServiceProxy(getRuleKey());
	}

}
