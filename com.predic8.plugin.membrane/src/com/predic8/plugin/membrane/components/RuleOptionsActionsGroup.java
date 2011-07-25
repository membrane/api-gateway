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

package com.predic8.plugin.membrane.components;

import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;


public class RuleOptionsActionsGroup {
	private Group ruleOptionsActionGroup;

	public RuleOptionsActionsGroup(Composite parent, int style) {
		ruleOptionsActionGroup = new Group(parent, style);
		ruleOptionsActionGroup.setText("Action");
		ruleOptionsActionGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		ruleOptionsActionGroup.setLayout(new GridLayout());
	}
	
	public Group getRuleOptionsActionGroup() {
		return ruleOptionsActionGroup;
	}
}