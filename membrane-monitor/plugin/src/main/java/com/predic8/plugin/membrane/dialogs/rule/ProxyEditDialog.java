package com.predic8.plugin.membrane.dialogs.rule;

import org.eclipse.swt.widgets.Shell;

import com.predic8.plugin.membrane.dialogs.rule.composites.*;

public class ProxyEditDialog extends AbstractProxyEditDialog {
	
	public ProxyEditDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	public String getTitle() {
		return "Edit Proxy Configuration";
	}

	@Override
	protected AbstractProxyFeatureComposite createProxyKeyTabItem() {
		return new ProxyRuleKeyTabComposite(tabFolder);
	}

	@Override
	protected void addCustomTabItems() {
		// ProxyEditDialog has no custom tab items
	}

	@Override
	protected AbstractProxyFeatureComposite createProxyXMLConfTabItem() {
		return new ProxyXMLConfComposite(tabFolder);
	}
	
}
