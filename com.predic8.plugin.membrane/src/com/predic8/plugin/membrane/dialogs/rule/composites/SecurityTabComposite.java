package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.*;
import com.predic8.plugin.membrane.components.SecurityGroup;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class SecurityTabComposite extends AbstractProxyFeatureComposite implements SecurityConfigurationChangeListener{
	
	protected SecurityGroup securityGroup;
	
	public SecurityTabComposite(Composite parent, boolean outgoing) {
		super(parent);
		securityGroup = new SecurityGroup(outgoing);
	}

	protected void createSecurityGroup(Composite parent) {
		securityGroup.createContent(parent);
		securityGroup.getSecurityGroup().setLayoutData(SWTUtil.getGreedyHorizontalGridData());
		Router.getInstance().getConfigurationManager().addSecurityConfigurationChangeListener(this);
		securityGroup.getSecureConnectionButton().addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				dataChanged = true;
			}
		
		});
	}
	
	public void securityConfigurationChanged() {
		securityGroup.enableSecureConnectionButton();
	}
	
	@Override
	public void dispose() {
		Router.getInstance().getConfigurationManager().removeSecurityConfigurationChangeListener(this);
		super.dispose();
	}
	
}
