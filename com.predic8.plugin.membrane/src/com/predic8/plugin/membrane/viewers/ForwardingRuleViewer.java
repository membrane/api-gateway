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

package com.predic8.plugin.membrane.viewers;

import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.components.RuleOptionsActionsGroup;
import com.predic8.plugin.membrane.components.RuleOptionsBlockComp;
import com.predic8.plugin.membrane.components.RuleOptionsRuleKeyGroup;
import com.predic8.plugin.membrane.components.RuleOptionsTargetGroup;
import com.predic8.plugin.membrane.dialogs.AbstractRuleViewer;

public class ForwardingRuleViewer extends AbstractRuleViewer {

	private RuleOptionsRuleKeyGroup ruleOptionsRuleKeyGroup;

	private RuleOptionsTargetGroup ruleOptionsTargetGroup;

	public ForwardingRuleViewer(Composite parent, int style) {
		super(parent);

		ruleOptionsRuleKeyGroup = new RuleOptionsRuleKeyGroup(this, SWT.NONE);

		ruleOptionsTargetGroup = new RuleOptionsTargetGroup(this, SWT.NONE);

		ruleOptionsCommandComp = new Composite(this, SWT.NONE);

		
		ruleOptionsActionsGroup = new RuleOptionsActionsGroup(this, SWT.NONE);
		ruleOptionsBlockComp = new RuleOptionsBlockComp(ruleOptionsActionsGroup.getRuleOptionsActionGroup(), this, SWT.NONE);
		
		final GridData gridData4CommandComp = new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.GRAB_VERTICAL | GridData.VERTICAL_ALIGN_END);
		gridData4CommandComp.horizontalSpan = 2;
		ruleOptionsCommandComp.setLayoutData(gridData4CommandComp);
		final GridLayout gridLayout4CommandComp = new GridLayout();
		gridLayout4CommandComp.numColumns = 3;
		ruleOptionsCommandComp.setLayout(gridLayout4CommandComp);

		ruleOptionsModifyButton = new Button(ruleOptionsCommandComp, SWT.NONE);
		final GridData gridData4ModifyButton = new GridData();
		gridData4ModifyButton.widthHint = 45;
		ruleOptionsModifyButton.setLayoutData(gridData4ModifyButton);
		ruleOptionsModifyButton.setText("Modify");

		ruleOptionsModifyButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				editSelectedRule();

			}
		});

		ruleOptionsModifyButton.setVisible(false);

		ruleOptionsResetButton = new Button(ruleOptionsCommandComp, SWT.NONE);
		final GridData gridData4ResetButton = new GridData();
		gridData4ResetButton.widthHint = 45;
		ruleOptionsResetButton.setLayoutData(gridData4ResetButton);
		ruleOptionsResetButton.setText("Reset");

		ruleOptionsResetButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				resetValues(rule);
			}
		});
		ruleOptionsResetButton.setVisible(false);
		
	}

	boolean isRuleKeyValid() {
		return ruleOptionsRuleKeyGroup.getUserInput() != null;
	}

	public void resetValues(Rule selectedRule) {
		if ((rule = selectedRule) != null && selectedRule instanceof ForwardingRule ) {
			nameText.setText(rule.getName());
			ruleOptionsRuleKeyGroup.setInput(rule.getRuleKey());
			ruleOptionsTargetGroup.setTargetHost(((ForwardingRule)rule).getTargetHost());
			ruleOptionsTargetGroup.setTargetPort(((ForwardingRule)rule).getTargetPort());
			ruleOptionsBlockComp.setRequestBlock(rule.isBlockRequest());
			ruleOptionsBlockComp.setResponseBlock(rule.isBlockResponse());
		}

	}

	public void resetValues() {
		ruleOptionsRuleKeyGroup.clear();
		ruleOptionsTargetGroup.clear();
		ruleOptionsBlockComp.clear();

	}

	public void setModifyButtonEnable(boolean bool) {
		if (ruleOptionsModifyButton != null)
			ruleOptionsModifyButton.setEnabled(bool);
	}

	public void setRestoreButtonEnable(boolean bool) {
		if (ruleOptionsResetButton != null)
			ruleOptionsResetButton.setEnabled(bool);
		if (bool == false)
			rule = null;
	}

	public boolean isInputChanged() {
		return ruleOptionsModifyButton.isEnabled();
	}

	public void editSelectedRule() {
		String targetHost = ruleOptionsTargetGroup.getTargetHost();
		String targetPort = ruleOptionsTargetGroup.getTargetPort();
		boolean blockRequest = ruleOptionsBlockComp.getRequestBlock();
		boolean blockResponse = ruleOptionsBlockComp.getResponseBlock();

		ForwardingRuleKey ruleKey = ruleOptionsRuleKeyGroup.getUserInput();
		if (ruleKey == null) {
			openErrorDialog("Illeagal input! Please check again");
			return;
		}

		if (ruleKey.equals(rule.getRuleKey())) {
			rule.setName(nameText.getText());
			((ForwardingRule)rule).setTargetHost(targetHost);
			((ForwardingRule)rule).setTargetPort(targetPort);
			rule.setBlockRequest(blockRequest);
			rule.setBlockResponse(blockResponse);
			Router.getInstance().getRuleManager().ruleChanged(rule);
			return;
		}

		if (Router.getInstance().getRuleManager().getRule(ruleKey) != null) {
			openErrorDialog("Illeagal input! Your rule key conflict with another existent rule.");
			return;
		}
		if (openConfirmDialog("You've changed the rule key, so all the old history will be cleared.")) {

			if (!((HttpTransport) Router.getInstance().getTransport()).isAnyThreadListeningAt(ruleKey.getPort())) {
				try {
					((HttpTransport) Router.getInstance().getTransport()).addPort(ruleKey.getPort());
				} catch (IOException e1) {
					openErrorDialog("Failed to open the new port. Please change another one. Old rule is retained");
					return;
				}
			}
			Router.getInstance().getRuleManager().removeRule(rule);
			if (!Router.getInstance().getRuleManager().isAnyRuleWithPort(rule.getRuleKey().getPort()) && (rule.getRuleKey().getPort() != ruleKey.getPort())) {
				try {
					((HttpTransport) Router.getInstance().getTransport()).closePort(rule.getRuleKey().getPort());
				} catch (IOException e2) {
					openErrorDialog("Failed to close the obsolete port: " + rule.getRuleKey().getPort());
				}
			}
			rule.setName(nameText.getText().trim());
			rule.setRuleKey(ruleKey);
			Router.getInstance().getRuleManager().addRuleIfNew(rule);
			((ForwardingRule)rule).setTargetHost(targetHost);
			((ForwardingRule)rule).setTargetPort(targetPort);
			rule.setBlockRequest(blockRequest);
			rule.setBlockResponse(blockResponse);
			Router.getInstance().getRuleManager().ruleChanged(rule);
		}
	}

}