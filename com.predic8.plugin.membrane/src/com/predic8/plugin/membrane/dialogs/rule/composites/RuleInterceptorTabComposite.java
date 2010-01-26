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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.dialogs.rule.AddInterceptorDialog;
import com.predic8.plugin.membrane.dialogs.rule.EditInterceptorDialog;
import com.predic8.plugin.membrane.dialogs.rule.providers.InterceptorTableViewerContentProvider;
import com.predic8.plugin.membrane.dialogs.rule.providers.InterceptorTableViewerLabelProvider;

public class RuleInterceptorTabComposite extends Composite {

	private TableViewer tableViewer;

	private List<Interceptor> interceptors = new ArrayList<Interceptor>();
	
	private Button btEdit;
	
	private Button btRemove;
	
	private Button btUp, btDown;
	
	private Interceptor selectedInterceptor; 
	
	public RuleInterceptorTabComposite(final Composite parent) {
		super(parent, SWT.NONE);
		
		Composite listComposite = createListComposite();

		tableViewer = createTableViewer(listComposite);

		createControls(createControlsComposite());
	}

	private void createControls(Composite controls) {
		createNewButton(controls);
		
		btEdit = createEditButton(controls);
	
		btRemove = createRemoveButton(controls);
		
		btUp = createUpButton(controls);
		
		btDown = createDownButton(controls);
		
		
		new Label(controls, SWT.NONE).setText(" ");
		new Label(controls, SWT.NONE).setText(" ");
		new Label(controls, SWT.NONE).setText(" ");
	}

	private Composite createControlsComposite() {
		Composite controls = new Composite(this, SWT.NONE);
		RowLayout rowLayout = new RowLayout();
		rowLayout.type = SWT.VERTICAL;
		rowLayout.spacing = 15;
		rowLayout.fill = true;
		controls.setLayout(rowLayout);
		return controls;
	}

	private Composite createListComposite() {
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginTop = 10;
		layout.marginLeft = 5;
		layout.marginBottom = 10;
		layout.marginRight = 5;
		setLayout(layout);

		Composite listComposite = new Composite(this, SWT.NONE);
		listComposite.setLayout(new GridLayout());
		return listComposite;
	}

	private void createNewButton(final Composite controlsComposite) {
		Button btNew = new Button(controlsComposite, SWT.PUSH);
		btNew.setText("Add");
		btNew.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				AddInterceptorDialog dialog = new AddInterceptorDialog(controlsComposite.getShell(), RuleInterceptorTabComposite.this);
				if (dialog.getShell() == null) {
					dialog.create();
				}
				dialog.open();
			}
		});
	}

	private Button createEditButton(final Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Edit");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				EditInterceptorDialog dialog = new EditInterceptorDialog(controlsComposite.getShell(), RuleInterceptorTabComposite.this);
				if (dialog.getShell() == null) {
					dialog.create();
				}
				dialog.setInput(selectedInterceptor);
				dialog.open();
			}
		});
		return bt;
	}

	private Button createRemoveButton(Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Remove");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				interceptors.remove(selectedInterceptor);
				selectedInterceptor = null;
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						tableViewer.setInput(interceptors);
					}
				});
			}
		});
		return bt;
	}

	private Button createUpButton(Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Up");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = interceptors.indexOf(selectedInterceptor);
				if (index == 0)
					return;
				interceptors.remove(index);
				interceptors.add(index - 1, selectedInterceptor);
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						tableViewer.setInput(interceptors);
					}
				});
			}
		});
		return bt;
	}

	private Button createDownButton(Composite controlsComposite) {
		Button btDown = new Button(controlsComposite, SWT.PUSH);
		btDown.setText("Down");
		btDown.setEnabled(false); 
		btDown.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = interceptors.indexOf(selectedInterceptor);
				if (index == interceptors.size() -1)
					return;
				interceptors.remove(index);
				interceptors.add(index + 1, selectedInterceptor);
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						tableViewer.setInput(interceptors);
					}
				});
			}
		});
		return btDown;
	}

	private TableViewer createTableViewer(Composite listComposite) {
		final TableViewer viewer = new TableViewer(listComposite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(viewer);
		viewer.setContentProvider(new InterceptorTableViewerContentProvider());
		viewer.setLabelProvider(new InterceptorTableViewerLabelProvider());
		
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
				if (selection == null || selection.isEmpty()) {
					enableButtonsOnSelection(false);
					return;
				}
				enableButtonsOnSelection(true);
				selectedInterceptor = (Interceptor)selection.getFirstElement();
			}

		});

		
		GridData gData = new GridData();
		gData.widthHint = 330;
		gData.heightHint = 270;
		viewer.getTable().setLayoutData(gData);
		return viewer;
	}

	private void enableButtonsOnSelection(boolean status) {
		btEdit.setEnabled(status);
		btRemove.setEnabled(status);
		btUp.setEnabled(status);
		btDown.setEnabled(status);
	}
	
	private void createColumns(TableViewer viewer) {
		String[] titles = { "Interceptor Name", "Class Name" };
		int[] bounds = { 100, 240 };

		for (int i = 0; i < titles.length; i++) {
			final TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
			column.getColumn().setAlignment(SWT.CENTER);
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);
		}

		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
	}

	public void setInput(Rule rule ) {
		interceptors.addAll(rule.getInterceptors());
		tableViewer.setInput(interceptors);
	}

	public void addNewInterceptor(Interceptor interceptor) {
		interceptors.add(interceptor);
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				tableViewer.setInput(interceptors);
			}
		});
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

}
