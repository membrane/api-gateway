package com.predic8.plugin.membrane.wizards;

import java.io.IOException;

import org.eclipse.jface.wizard.WizardPage;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.transport.http.HttpTransport;

public abstract class AbstractRuleWizardPage extends WizardPage {

	protected AbstractRuleWizardPage(String pageName) {
		super(pageName);
	}

	boolean canFinish() {
		return false;
	}
	
	boolean performFinish(AddRuleWizard wizard) throws IOException {
		return true;
	}
	
	protected HttpTransport getHttpTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}

	protected RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}
	
}
