package com.predic8.plugin.membrane.wizards;

import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.*;
import com.predic8.plugin.membrane.components.SecurityGroup;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class SecurityWizardPage extends AbstractProxyWizardPage implements SecurityConfigurationChangeListener {

	protected SecurityGroup securityGroup;
	
	protected SecurityWizardPage(String pageName, boolean outgoing) {
		super(pageName);
		securityGroup = new SecurityGroup(outgoing);
	}
	
	protected void createSecurityGroup(Composite parent) {
		securityGroup.createContent(parent);
		securityGroup.getSecurityGroup().setLayoutData(SWTUtil.getGreedyHorizontalGridData());
		Router.getInstance().getConfigurationManager().addSecurityConfigurationChangeListener(this);
	}
	
	protected abstract void addListenersToSecureConnectionButton();
	
	@Override
	public void securityConfigurationChanged() {
		securityGroup.enableSecureConnectionButton();
	}
	
	@Override
	public void dispose() {
		Router.getInstance().getConfigurationManager().removeSecurityConfigurationChangeListener(this);
		super.dispose();
	}
	
	public SecurityGroup getSecurityGroup() {
		return securityGroup;
	}
}
