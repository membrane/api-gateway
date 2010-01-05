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
		Composite container = (Composite) super.createDialogArea(parent);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 30;
		gridLayout.marginLeft = 10;
		gridLayout.marginBottom = 30;
		gridLayout.marginRight = 10;
		
		container.setLayout(gridLayout);
		
		Label lbEntry = new Label(container, SWT.NONE);
		lbEntry.setText("Please enter interceptor data for rule in the fields below.");
		
		Label lbDummy = new Label(container, SWT.NONE);
		lbDummy.setText(" ");
		
		
		Group group = new Group(container, SWT.NONE);
		group.setText("Interceptor Description");
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		
		GridLayout groupGridLayout = new GridLayout();
		groupGridLayout.numColumns = 2;
		groupGridLayout.marginTop = 10;
		groupGridLayout.marginLeft = 10;
		groupGridLayout.marginBottom = 10;
		groupGridLayout.marginRight = 10;
		group.setLayout(groupGridLayout);
		
		Label lbName = new Label(group, SWT.NONE);
		lbName.setText("Name: ");
		
		textName = new Text(group, SWT.BORDER);
		GridData griddata4NameText = new GridData();
		griddata4NameText.widthHint = 120;
		textName.setLayoutData(griddata4NameText);
		
		Label lbClassName = new Label(group, SWT.NONE);
		lbClassName.setText("Classname: ");
		
		textClassName = new Text(group, SWT.BORDER);
		GridData griddata4ClassNameText = new GridData();
		griddata4ClassNameText.widthHint = 270;
		textClassName.setLayoutData(griddata4ClassNameText);
		
		return container;
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "OK", false);
		createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", true);
	}
	
	@Override
	protected void okPressed() {
		String name = textName.getText().trim();
		String className = textClassName.getText().trim();
		
		try {
			Interceptor interceptor = (Interceptor)((Class) Class.forName(className)).newInstance();
			interceptor.setDisplayName(name);
			interceptorComposite.addNewInterceptor(interceptor);
		} catch (Exception e) {
			e.printStackTrace();
		}
		close();
	}
	
	public abstract String getDialogTitle();
	
}
