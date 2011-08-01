package com.predic8.plugin.membrane.wizards;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.SecurityConfigurationChangeListener;
import com.predic8.plugin.membrane.actions.ShowSecurityPreferencesAction;

public abstract class SecurityWizardPage extends AbstractRuleWizardPage implements SecurityConfigurationChangeListener {

	protected Button btSecureConnection;
	
	protected SecurityWizardPage(String pageName) {
		super(pageName);
	}
	
	protected void createSecurityComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginBottom = 10;
		composite.setLayout(layout);
		
		createSecureConnectionButton(composite);
		
		Label label = new Label(composite, SWT.NONE);
		label.setText("To enable secure connection you must provide keystore and truststore data.");
	
		createLink(composite, "<A>Security Preferences Page</A>");
		
		Router.getInstance().getConfigurationManager().addSecurityConfigurationChangeListener(this);
	}
	
	protected abstract void addListenersToSecureConnectionButton();
	
	protected void createSecureConnectionButton(Composite composite) {
		btSecureConnection = new Button(composite, SWT.CHECK);
		btSecureConnection.setText("SecureConnection (SSL/STL)");
		btSecureConnection.setEnabled(Router.getInstance().getConfigurationManager().getProxies().isKeyStoreAvailable());
	}

	protected void createLink(Composite composite, String linkText) {
		Link link = new Link(composite, SWT.NONE);
		link.setText(linkText);
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ShowSecurityPreferencesAction action = new ShowSecurityPreferencesAction();
				action.run();
			}
		});
	}

	public boolean getSecureConnection() {
		return btSecureConnection.getSelection();
	}
	
	protected void enableSecureConnectionButton() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				btSecureConnection.setEnabled(Router.getInstance().getConfigurationManager().getProxies().isKeyStoreAvailable());
			}
		});
	}
	
	public void securityConfigurationChanged() {
		enableSecureConnectionButton();
	}
	
	@Override
	public void dispose() {
		Router.getInstance().getConfigurationManager().removeSecurityConfigurationChangeListener(this);
		super.dispose();
	}
}
