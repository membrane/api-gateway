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

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.rules.*;

public class ProxyRuleConfigurationPage extends AbstractPortConfigurationPage {

	public static final String PAGE_NAME = "Proxy Rule Configuration";

	protected ProxyRuleConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Proxy Rule");
		setDescription("Specify Listen Port for Proxy Rule");
	}

	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);

		createFullDescriptionLabel(composite, "A rule is listenening on a TCP port for incomming connections.\n" + "The port number can be any integer between 1 and 65535.");  

		createListenPortLabel(composite);

		listenPortText = createListenPortText(composite);
		
		setControl(composite);
	}

	@Override
	public IWizardPage getNextPage() {
		return null;
	}

	@Override
	public boolean canFinish() {
		return true;
	}

	@Override
	boolean performFinish(AddProxyWizard wizard) throws IOException {
		ProxyRuleKey key = new ProxyRuleKey(getListenPort());
		if (getRuleManager().exists(key)) {
			wizard.openWarningDialog("You've entered a duplicated rule key.");
			return false;
		}

		getRuleManager().addProxyIfNew(new ProxyRule(key));
		return true;
	}

}
