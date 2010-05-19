package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.actions.ShowSecurityPreferencesAction;

public abstract class SecurityTabComposite extends Composite {

	protected Button btSecureConnection;
	
	public SecurityTabComposite(Composite parent) {
		super(parent, SWT.NONE);
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
	}
	
	protected void createSecureConnectionButton(Composite parent) {
		btSecureConnection = new Button(parent, SWT.CHECK);
		btSecureConnection.setText("Secure Connection (SSL/STL)");
		btSecureConnection.setEnabled(Router.getInstance().getConfigurationManager().getConfiguration().isSecurityConfigurationAvailable());
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
	
	public void setSecureConnection(boolean selected) {
		btSecureConnection.setSelection(selected);
	}
}
