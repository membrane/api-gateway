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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.plugin.membrane.dialogs.rule.composites.ProxyInterceptorTabComposite;
import com.predic8.plugin.membrane.dialogs.rule.providers.AddInterceptorTableViewerContentProvider;
import com.predic8.plugin.membrane.dialogs.rule.providers.AddInterceptorTableViewerLabelProvider;
import com.predic8.plugin.membrane.util.SWTUtil;

public class AddInterceptorDialog extends Dialog {

	private TableViewer tableViewer;
	
	private ProxyInterceptorTabComposite interceptorComposite;
	
	public AddInterceptorDialog(Shell shell, ProxyInterceptorTabComposite parent) {
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
		
		new Label(container, SWT.NONE).setText("List of currently available interceptors.");
		
		new Label(container, SWT.NONE).setText("  ");
		
		tableViewer = createTableViewer(container);
		
		return container;
	}

	private TableViewer createTableViewer(Composite container) {
		TableViewer tableViewer = new TableViewer(container, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		tableViewer.setContentProvider(new AddInterceptorTableViewerContentProvider());
		tableViewer.setLabelProvider(new AddInterceptorTableViewerLabelProvider());
		
		tableViewer.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
		
		tableViewer.setInput(Router.getInstance().getInterceptors());
		return tableViewer;
	}

	private Composite createTopContainer(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(SWTUtil.createGridLayout(1, 20));
		return container;
	}
	
	private void createColumns(TableViewer viewer) {
		String[] titles = { "Interceptor Name"};
		int[] bounds = { 240 };

		for (int i = 0; i < titles.length; i++) {
			final TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
			col.getColumn().setAlignment(SWT.CENTER);
			col.getColumn().setText(titles[i]);
			col.getColumn().setWidth(bounds[i]);
			col.getColumn().setResizable(true);
			col.getColumn().setMoveable(true);
		}

		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
	}
	
	@Override
	protected void okPressed() {
		IStructuredSelection selection = (IStructuredSelection)tableViewer.getSelection();
		if (selection == null || selection.isEmpty())
			close();
		
		interceptorComposite.addNewInterceptor((Interceptor)selection.getFirstElement());
		
		super.okPressed();
	}
	
}
