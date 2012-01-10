package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.*;
import com.predic8.plugin.membrane.actions.ShowSecurityPreferencesAction;
import com.predic8.plugin.membrane.util.SWTUtil;


public class SecurityGroup {

	protected Button btSecureConnection;
	
	protected boolean outgoing;
	
	private Group securityGroup;
	
	public SecurityGroup( boolean outgoing) {
		this.outgoing = outgoing;
	}
	
	public void createContent(Composite parent) {
		securityGroup = new Group(parent, SWT.NONE);
		securityGroup.setLayout(SWTUtil.createGridLayout(1, 5));
		securityGroup.setText("Security");
		
		createButton();
		
		if (!outgoing) {
			Label label = new Label(securityGroup, SWT.NONE);
			label.setText("To enable secure connections you must provide a keystore at the");
			createLink("<A>Security Preferences Page</A>");
		}
	}
	
	protected void createLink(String linkText) {
		Link link = new Link(securityGroup, SWT.NONE);
		link.setText(linkText);
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ShowSecurityPreferencesAction action = new ShowSecurityPreferencesAction();
				action.run();
			}
		});
	}
	
	protected void createButton() {
		btSecureConnection = new Button(securityGroup, SWT.CHECK);
		btSecureConnection.setText(getCheckButtonText());
		btSecureConnection.setEnabled(getEnabledStatus());
	}
	
	
	private String getCheckButtonText() {
		StringBuffer buf = new StringBuffer();
		buf.append("Secure ");
		if (outgoing)
			buf.append("outgoing");
		else
			buf.append("incoming");
		buf.append(" ");
		buf.append("connections (SSL/TLS)");
		return buf.toString();
	}
	
	protected boolean getEnabledStatus() {
		return outgoing || Router.getInstance().getConfigurationManager().getProxies().isKeyStoreAvailable();
	}

	public void enableSecureConnectionButton() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				btSecureConnection.setEnabled(getEnabledStatus());
			}
		});
	}
	
	public boolean getSecureConnection() {
		return btSecureConnection.getSelection();
	}
	
	
	public Button getSecureConnectionButton() {
		return btSecureConnection;
	}
	
	public Group getSecurityGroup() {
		return securityGroup;
	}
}
