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

package com.predic8.plugin.membrane.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;

public class AddHeaderFieldDialog extends Dialog {

	private GridLayout gridLayout4BaseAreaComp;

	private Composite baseAreaComp;

	private Text headerFieldNameText;
	
	private Text headerFieldValueText;
	
	private TableViewer tableViewer;
	
	public AddHeaderFieldDialog(Shell parentShell, TableViewer tableViewer) {
		super(parentShell);
		this.tableViewer = tableViewer;
	}

	protected Control createDialogArea(Composite parent) {
		baseAreaComp = (Composite) super.createDialogArea(parent);
		gridLayout4BaseAreaComp = new GridLayout();
		gridLayout4BaseAreaComp.numColumns = 2;
		gridLayout4BaseAreaComp.marginLeft = 20;
		gridLayout4BaseAreaComp.marginRight = 20;
		gridLayout4BaseAreaComp.marginTop = 20;
		gridLayout4BaseAreaComp.marginBottom = 20;
		baseAreaComp.setLayout(gridLayout4BaseAreaComp);

		
		Label headerNameLabel = new Label(baseAreaComp, SWT.NONE);
		GridData gridData4HeaderNameLabel = new GridData();
		gridData4HeaderNameLabel.heightHint = 22;
		gridData4HeaderNameLabel.widthHint = 72;
		headerNameLabel.setLayoutData(gridData4HeaderNameLabel);
		headerNameLabel.setText("Field Name");

		headerFieldNameText = new Text(baseAreaComp, SWT.NONE);
		GridData gridData4HeaderNameText = new GridData();
		gridData4HeaderNameText.heightHint = 20;
		gridData4HeaderNameText.widthHint = 140;
		headerFieldNameText.setLayoutData(gridData4HeaderNameText);	
		
		
		Label headerValueLabel = new Label(baseAreaComp, SWT.NONE);
		GridData gridData4HeaderValueLabel = new GridData();
		gridData4HeaderValueLabel.heightHint = 22;
		gridData4HeaderValueLabel.widthHint = 72;
		headerValueLabel.setLayoutData(gridData4HeaderValueLabel);
		headerValueLabel.setText("Field Value");

		headerFieldValueText = new Text(baseAreaComp, SWT.NONE);
		GridData gridData4HeaderValueText = new GridData();
		gridData4HeaderValueText.heightHint = 20;
		gridData4HeaderValueText.widthHint = 140;
		headerFieldValueText.setLayoutData(gridData4HeaderValueText);	
		
		
		return baseAreaComp;
	}

	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, true);
	}

	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Add New Header Field");
	}

	public void dispose() {
		baseAreaComp.dispose();
	}
	
	@Override
	protected void okPressed() {
		String headerFieldName = headerFieldNameText.getText();
		String headerFieldValue = headerFieldValueText.getText();
		
		if ("".equals(headerFieldName) || "".equals(headerFieldValue) ) {
			MessageDialog.openError(this.getParentShell(), "Illegal Input", "Header field name and value both must be specified.");
		} else {
			HeaderField headerField = new HeaderField(headerFieldName, headerFieldValue);
			tableViewer.add(headerField);
			Message message = (Message) tableViewer.getInput();
			message.getHeader().add(headerField);
			
			super.okPressed();
		}
	}
	
	@Override
	protected void cancelPressed() {
		super.cancelPressed();
	}
}