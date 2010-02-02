/* Copyright 2009 predic8 Gmb
H, www.predic8.com

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
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.plugin.membrane.dialogs.rule.composites.RuleInterceptorTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.providers.AddInterceptorTableViewerContentProvider;
import com.predic8.plugin.membrane.dialogs.rule.providers.AddInterceptorTableViewerLabelProvider;

public class AddInterceptorDialog extends Dialog {

	private TableViewer tableViewer;
	
	private RuleInterceptorTabComposite interceptorComposite;
	
	public AddInterceptorDialog(Shell shell, RuleInterceptorTabComposite parent) {
		super(shell);
		this.interceptorComposite = parent;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Add New Interceptor");
		shell.setSize(300, 400);
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "OK", false);
		createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", true);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = createTopContainer(parent);
		
		Label label = new Label(container, SWT.NONE);
		label.setText("List of currently available interceptors.");
		
		new Label(container, SWT.NONE).setText("  ");
		
		tableViewer = new TableViewer(container, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		tableViewer.setContentProvider(new AddInterceptorTableViewerContentProvider());
		tableViewer.setLabelProvider(new AddInterceptorTableViewerLabelProvider());
		
		tableViewer.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
		
		tableViewer.setInput(Router.getInstance().getInterceptors());
		
		return container;
	}

	private Composite createTopContainer(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		GridLayout gridLayout = new GridLayout();
		gridLayout.marginTop = 20;
		gridLayout.marginLeft = 20;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 20;
		container.setLayout(gridLayout);
		return container;
	}
	
	private void createColumns(TableViewer viewer) {
		String[] titles = { "Interceptor Name"};
		int[] bounds = { 240 };

		for (int i = 0; i < titles.length; i++) {
			final TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
			column.getColumn().setAlignment(SWT.CENTER);
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);
		}

		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
	}
	
	@Override
	protected void okPressed() {
		IStructuredSelection selection = (IStructuredSelection)tableViewer.getSelection();
		if (selection == null || selection.isEmpty())
			close();
		
		Interceptor interceptor = (Interceptor)selection.getFirstElement();
		interceptorComposite.addNewInterceptor(interceptor);
		
		super.okPressed();
	}
	
}
