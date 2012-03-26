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
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.components.composites.RuleInterceptorControlsComposite;
import com.predic8.plugin.membrane.dialogs.rule.AddInterceptorDialog;
import com.predic8.plugin.membrane.dialogs.rule.EditInterceptorDialog;
import com.predic8.plugin.membrane.dialogs.rule.providers.InterceptorTableViewerContentProvider;
import com.predic8.plugin.membrane.dialogs.rule.providers.InterceptorTableViewerLabelProvider;
import com.predic8.plugin.membrane.util.SWTUtil;

public class ProxyInterceptorTabComposite extends Composite {

	private TableViewer tableViewer;

	private List<Interceptor> interceptors = new ArrayList<Interceptor>();
	
	private Interceptor selectedInterceptor; 
	
	private RuleInterceptorControlsComposite controlsComposite;
	
	public ProxyInterceptorTabComposite(final Composite parent) {
		super(parent, SWT.NONE);
		
		Composite listComposite = createListComposite();

		tableViewer = createTableViewer(listComposite);

		controlsComposite = new RuleInterceptorControlsComposite(this);
	}

	private Composite createListComposite() {
		setLayout(SWTUtil.createGridLayout(2, 10, 5, 10, 5));
		Composite listComposite = new Composite(this, SWT.NONE);
		listComposite.setLayout(new GridLayout());
		return listComposite;
	}
	
	public void addNewInterceptor() {
		AddInterceptorDialog dialog = new AddInterceptorDialog(Display.getCurrent().getActiveShell(), ProxyInterceptorTabComposite.this);
		if (dialog.getShell() == null) {
			dialog.create();
		}
		dialog.open();
	}

	public void editSelectedInterceptor() {
		EditInterceptorDialog dialog = new EditInterceptorDialog(Display.getCurrent().getActiveShell(), this);
		if (dialog.getShell() == null) {
			dialog.create();
		}
		dialog.setInput(selectedInterceptor);
		dialog.open();
	}
	
	
	public void removeSelectedInterceptor() {
		interceptors.remove(selectedInterceptor);
		selectedInterceptor = null;
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				tableViewer.setInput(interceptors);
			}
		});
	}

	public void moveUpSelectedInterceptor() {
		int index = interceptors.indexOf(selectedInterceptor);
		if (index == 0)
			return;
		Collections.swap(interceptors, index, index - 1);
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				tableViewer.setInput(interceptors);
			}
		});
	}
	
	public void moveDownSelectedInterceptor() {
		int index = interceptors.indexOf(selectedInterceptor);
		if (index == interceptors.size() -1)
			return;
		Collections.swap(interceptors, index, index + 1);
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				tableViewer.setInput(interceptors);
			}
		});
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
					controlsComposite.enableDependentButtons(false);
					return;
				}
				controlsComposite.enableDependentButtons(true);
				selectedInterceptor = (Interceptor)selection.getFirstElement();
			}

		});

		
		GridData gData = new GridData();
		gData.widthHint = 330;
		gData.heightHint = 270;
		viewer.getTable().setLayoutData(gData);
		return viewer;
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
