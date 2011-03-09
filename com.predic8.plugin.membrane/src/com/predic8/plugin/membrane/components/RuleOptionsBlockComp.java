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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import com.predic8.plugin.membrane.dialogs.AbstractRuleViewer;


public class RuleOptionsBlockComp extends Composite {
	private AbstractRuleViewer ruleOptionViewer;
	
	private Button ruleOptionsBlockRequestCheckBox;

	private Button ruleOptionsBlockResponseCheckBox;

	public RuleOptionsBlockComp(Group container,AbstractRuleViewer ruleOptionViewer, int style) {
		super(container, style);
		this.ruleOptionViewer=ruleOptionViewer;
		setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
		setLayout(new GridLayout());

		ruleOptionsBlockRequestCheckBox = new Button(this, SWT.CHECK);
		ruleOptionsBlockRequestCheckBox.addSelectionListener(new SelectionListener(){
			public void widgetSelected(SelectionEvent e) {
				RuleOptionsBlockComp.this.ruleOptionViewer.setEnableOnlyModifyAndRestoreButton(true);
			}
			public void widgetDefaultSelected(SelectionEvent e) {
			}});
		ruleOptionsBlockRequestCheckBox.setText("Block Request");

		ruleOptionsBlockResponseCheckBox = new Button(this, SWT.CHECK);
		ruleOptionsBlockResponseCheckBox.addSelectionListener(new SelectionListener(){
			public void widgetSelected(SelectionEvent e) {
				RuleOptionsBlockComp.this.ruleOptionViewer.setEnableOnlyModifyAndRestoreButton(true);
			}
			public void widgetDefaultSelected(SelectionEvent e) {
			}});
		ruleOptionsBlockResponseCheckBox.setText("Block Response");
	}
	public void setRequestBlock(boolean bool){
		ruleOptionsBlockRequestCheckBox.setSelection(bool);
	}
	public boolean getRequestBlock(){
		return ruleOptionsBlockRequestCheckBox.getSelection();
	}
	public void setResponseBlock(boolean bool){
		ruleOptionsBlockResponseCheckBox.setSelection(bool);
	}
	public boolean getResponseBlock(){
		return ruleOptionsBlockResponseCheckBox.getSelection();
	}

	public void clear() {
		ruleOptionsBlockRequestCheckBox.setSelection(false);
		ruleOptionsBlockResponseCheckBox.setSelection(false);
	}

}