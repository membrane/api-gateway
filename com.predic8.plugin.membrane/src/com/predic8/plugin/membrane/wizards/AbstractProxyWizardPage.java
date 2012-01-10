package com.predic8.plugin.membrane.wizards;

import java.io.IOException;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class AbstractProxyWizardPage extends WizardPage {

	protected AbstractProxyWizardPage(String pageName) {
		super(pageName);
	}

	boolean canFinish() {
		return false;
	}
	
	boolean performFinish(AddProxyWizard wizard) throws IOException {
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
		GridLayout layout = SWTUtil.createGridLayout(columns, 10, 2, 10, 10);
		layout.verticalSpacing = 20;
		composite.setLayout(layout);
		return composite;
	}
	
	protected void createFullDescriptionLabel(Composite composite, String description) {
		Label label = new Label(composite, SWT.WRAP);
		label.setText(description);
		label.setBounds(120, 10, 100, 100);
		
		GridData gData = new GridData();
		gData.horizontalSpan = 2;
		gData.verticalSpan = 2;
		label.setLayoutData(gData);
	}
	
}
