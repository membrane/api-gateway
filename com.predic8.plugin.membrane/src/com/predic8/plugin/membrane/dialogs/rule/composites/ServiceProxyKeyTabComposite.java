/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.rules.*;
import com.predic8.plugin.membrane.components.RuleKeyGroup;
import com.predic8.plugin.membrane.util.SWTUtil;

public class ServiceProxyKeyTabComposite extends SecurityTabComposite {

	private RuleKeyGroup ruleKeyGroup;
	
	public ServiceProxyKeyTabComposite(Composite parent) {
		super(parent, false);
		setLayout(SWTUtil.createGridLayout(1, 12));
	
		createSecurityGroup(this);
		
		ruleKeyGroup = new RuleKeyGroup(this, SWT.NONE);
		
	}

	@Override
	public void setRule(Rule rule) {
		super.setRule(rule);
		ruleKeyGroup.setInput(rule.getKey());
		securityGroup.getSecureConnectionButton().setSelection(rule.isInboundTLS());
	}
	
	public ServiceProxyKey getUserInput() {
		return ruleKeyGroup.getUserInput();
	}
	
	@Override
	public String getTitle() {
		return "Proxy Key";
	}
	
	@Override
	public void commit() {
		if (rule == null)
			return;
		
		ServiceProxyKey ruleKey = ruleKeyGroup.getUserInput();
		if (ruleKey == null) {
			MessageDialog.openError(this.getShell(), "Error", "Illeagal input! Please check again");
			return;
		}

		rule.setKey(ruleKey);
	}
	
	@Override
	public boolean isDataChanged() {
		return super.isDataChanged() || ruleKeyGroup.isDataChanged();
	}
}
