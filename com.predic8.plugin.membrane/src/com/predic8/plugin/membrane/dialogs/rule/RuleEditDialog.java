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


package com.predic8.plugin.membrane.dialogs.rule;

import java.io.IOException;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleActionsTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleGeneralInfoTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class RuleEditDialog extends Dialog {

	protected Rule rule;
	
	protected TabFolder tabFolder;
	
	protected RuleGeneralInfoTabComposite generalInfoComposite;
	
	protected RuleActionsTabComposite actionsComposite;
	
	protected RuleInterceptorTabComposite interceptorsComposite;
	
	protected RuleEditDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(getTitle());
		shell.setSize(520, 500);
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "OK", false);
		createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", true);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(SWTUtil.createGridLayout(1, 10));
		
		tabFolder = new TabFolder(container, SWT.NONE);
		GridData gd = new GridData();
		gd.widthHint = 440;
		gd.heightHint = 440;
		gd.grabExcessHorizontalSpace = true;
		gd.grabExcessVerticalSpace = true;
		tabFolder.setLayoutData(gd);
		
		
		return container;
	}
	
	public abstract String getTitle();
	
	public void setInput(Rule rule) {
		if (rule == null)
			return;
		this.rule = rule;
		generalInfoComposite.setRule(rule);
		actionsComposite.setInput(rule);
		interceptorsComposite.setInput(rule);
	}
	
	protected void openErrorDialog(String msg) {
		MessageDialog.openError(this.getShell(), "Error", msg);
	}

	protected void openWarningDialog(String msg) {
		MessageDialog.openWarning(this.getShell(), "Warning", msg);
	}

	protected boolean openConfirmDialog(String msg) {
		return MessageDialog.openConfirm(this.getShell(), "Confirm", msg);
	}
	
	public abstract void onOkPressed();
	
	@Override
	protected void okPressed() {
		onOkPressed();
		close();
	}
	
	protected void updateRule(RuleKey ruleKey, boolean addToManager) throws IOException {
		rule.setName(generalInfoComposite.getRuleName());
		rule.setKey(ruleKey);
		rule.setLocalHost(generalInfoComposite.getLocalHost());
		rule.setBlockRequest(actionsComposite.isRequestBlocked());
		rule.setBlockResponse(actionsComposite.isResponseBlocked());
		rule.setInterceptors(interceptorsComposite.getInterceptors());
		if (addToManager) {
			getRuleManager().addRuleIfNew(rule);
		}
	}
	
	protected RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}
	
	protected HttpTransport getTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}

	protected void doRuleUpdate(RuleKey ruleKey) {
		if (ruleKey.equals(rule.getKey())) {
			try {
				updateRule(ruleKey, false);
			} catch (IOException e) {
				openErrorDialog("Can not open port. Please check again");
			}
			getRuleManager().ruleChanged(rule);
			return;
		}

		if (getRuleManager().exists(ruleKey)) {
			openErrorDialog("Illeagal input! Your rule key conflict with another existent rule.");
			return;
		}
		if (!openConfirmDialog("You've changed the rule key, so all the old history will be cleared."))
			return;

		if (!getTransport().isAnyThreadListeningAt(ruleKey.getPort())) {
			try {
				getTransport().openPort(ruleKey.getPort(), rule.isInboundTLS());
			} catch (IOException e1) {
				openErrorDialog("Failed to open the new port. Please change another one. Old rule is retained");
				return;
			}
		}
		getRuleManager().removeRule(rule);
		if (!getRuleManager().isAnyRuleWithPort(rule.getKey().getPort()) && (rule.getKey().getPort() != ruleKey.getPort())) {
			try {
				getTransport().closePort(rule.getKey().getPort());
			} catch (IOException e2) {
				openErrorDialog("Failed to close the obsolete port: " + rule.getKey().getPort());
			}
		}
		try {
			updateRule(ruleKey, true);
		} catch (IOException e) {
			openErrorDialog("Can not open port. Please check again");
		}
		getRuleManager().ruleChanged(rule);
	}
	
}
