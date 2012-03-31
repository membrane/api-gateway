package com.predic8.plugin.membrane.dialogs.rule;

import org.eclipse.swt.widgets.Shell;

import com.predic8.plugin.membrane.dialogs.rule.composites.*;

public class ServiceProxyEditDialog extends AbstractProxyEditDialog {
	
	public ServiceProxyEditDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	public String getTitle() {
		return "Edit Service Proxy Configuration";
	}

	@Override
	protected AbstractProxyFeatureComposite createProxyKeyTabItem() {
		return new ServiceProxyKeyTabComposite(tabFolder);
	}

	@Override
	protected void addCustomTabItems() {
		addTabTabItem(new ServiceProxyTargetTabComposite(tabFolder));
	}

	@Override
	protected AbstractProxyFeatureComposite createProxyXMLConfTabItem() {
		return new ServiceProxyXMLConfComposite(tabFolder);
	}

}
