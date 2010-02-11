package com.predic8.plugin.membrane.wizards;

import java.io.IOException;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

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
	
	protected Composite createComposite(Composite parent, int columns) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = columns;
		layout.marginTop = 10;
		layout.marginLeft = 2;
		layout.marginBottom = 10;
		layout.marginRight = 10;
		layout.verticalSpacing = 20;
		composite.setLayout(layout);
		return composite;
	}
	
}
