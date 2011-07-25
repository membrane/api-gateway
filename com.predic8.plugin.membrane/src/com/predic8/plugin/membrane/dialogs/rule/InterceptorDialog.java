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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class InterceptorDialog extends Dialog {

	protected Text textName;
	protected Text textClassName;
	
	protected RuleInterceptorTabComposite interceptorComposite;
	
	public InterceptorDialog(Shell shell, RuleInterceptorTabComposite parent) {
		super(shell);
		this.interceptorComposite = parent;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(getDialogTitle());
		shell.setSize(420, 440);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = createComposite(parent);
		
		new Label(composite, SWT.NONE).setText("Please enter interceptor data for rule in the fields below.");
		
		new Label(composite, SWT.NONE).setText(" ");
		
		Group group = createGroup(composite);
		
		new Label(group, SWT.NONE).setText("Name: ");
		
		createTextForName(group);
		
		new Label(group, SWT.NONE).setText("Classname: ");
		
		createTextForClassName(group);
		
		return composite;
	}

	private void createTextForClassName(Group group) {
		textClassName = new Text(group, SWT.BORDER);
		GridData gData = new GridData();
		gData.widthHint = 270;
		textClassName.setLayoutData(gData);
	}

	private void createTextForName(Group group) {
		textName = new Text(group, SWT.BORDER);
		GridData gData = new GridData();
		gData.widthHint = 120;
		textName.setLayoutData(gData);
	}

	private Group createGroup(Composite composite) {
		Group group = new Group(composite, SWT.NONE);
		group.setText("Interceptor Description");
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		group.setLayout(SWTUtil.createGridLayout(2, 10));
		return group;
	}

	private Composite createComposite(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 30;
		gridLayout.marginLeft = 10;
		gridLayout.marginBottom = 30;
		gridLayout.marginRight = 10;
		container.setLayout(gridLayout);
		return container;
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "OK", false);
		createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", true);
	}
	
	@Override
	protected void okPressed() {
		try {
			interceptorComposite.addNewInterceptor(instantinateInterceptor());
		} catch (Exception e) {
			e.printStackTrace();
		}
		close();
	}

	@SuppressWarnings({"rawtypes" })
	private Interceptor instantinateInterceptor() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		Interceptor interceptor = (Interceptor)((Class) Class.forName(textClassName.getText().trim())).newInstance();
		interceptor.setDisplayName(textName.getText().trim());
		return interceptor;
	}
	
	public abstract String getDialogTitle();
	
}
