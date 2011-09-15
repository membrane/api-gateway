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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.components.ServiceProxyTargetGroup;
import com.predic8.plugin.membrane.util.SWTUtil;

public class ServiceProxyTargetTabComposite extends SecurityTabComposite {

	private ServiceProxyTargetGroup targetGroup;
	
	public ServiceProxyTargetTabComposite(Composite parent) {
		super(parent);
		setLayout(SWTUtil.createGridLayout(1, 20));
		
		createSecurityComposite(this);
		
		targetGroup = new ServiceProxyTargetGroup(this, SWT.NONE);
	}

	public ServiceProxyTargetGroup getTargetGroup() {
		return targetGroup;
	}
	
	public void setInput(Rule rule) {
		if (!(rule instanceof ServiceProxy))
			return;
		ServiceProxy fRule = (ServiceProxy)rule;
		targetGroup.setTargetHost(fRule.getTargetHost());
		targetGroup.setTargetPort(fRule.getTargetPort());
		btSecureConnection.setSelection(rule.isOutboundTLS());
	}
	
}
