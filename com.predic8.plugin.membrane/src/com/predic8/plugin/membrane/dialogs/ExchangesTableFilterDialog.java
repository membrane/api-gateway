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
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.plugin.membrane.dialogs.components.AbstractFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.ClientFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.MethodFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.RuleFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.ServerFilterComposite;
import com.predic8.plugin.membrane.dialogs.components.StatusCodeFilterComposite;
import com.predic8.plugin.membrane.filtering.ClientFilter;
import com.predic8.plugin.membrane.filtering.ExchangesFilter;
import com.predic8.plugin.membrane.filtering.MethodFilter;
import com.predic8.plugin.membrane.filtering.RulesFilter;
import com.predic8.plugin.membrane.filtering.ServerFilter;
import com.predic8.plugin.membrane.filtering.StatusCodeFilter;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ExchangesTableFilterDialog extends Dialog {

	private ExchangesView exchangesView;

	private TabFolder tabFolder;

	private AbstractFilterComposite[] filterComposites = new AbstractFilterComposite[5];

	private Button btRemoveFilters;

	public ExchangesTableFilterDialog(Shell parentShell, ExchangesView parent) {
		super(parentShell);
		this.exchangesView = parent;

	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Exchange Filters");
		shell.setSize(440, 560);
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);

		composite.setLayout(new GridLayout());

		btRemoveFilters = new Button(composite, SWT.PUSH);
		btRemoveFilters.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showAllFromEveryComposite();
			}
		});
		btRemoveFilters.setText("Remove  all  filters");

		GridData gridData = new GridData(SWT.RIGHT, SWT.FILL, true, true, 1, 1);
		gridData.grabExcessHorizontalSpace = true;
		btRemoveFilters.setLayoutData(gridData);

		Label label = new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL);
		label.setLayoutData(new GridData(410, 12));

		return super.createButtonBar(composite);
	}

	private ExchangesFilter getFilterForClass(Class<? extends ExchangesFilter> clazz) {
		if (exchangesView.getFilterManager().getFilterForClass(clazz) != null) {
			return exchangesView.getFilterManager().getFilterForClass(clazz);
		} else {
			try {
				return clazz.newInstance();
			} catch (Exception e) {
				throw new RuntimeException("Should never happen.");
			}
		}
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);

		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		container.setLayout(layout);

		tabFolder = new TabFolder(container, SWT.NONE);

		GridData gridData4Folder = new GridData();
		gridData4Folder.grabExcessHorizontalSpace = true;
		gridData4Folder.widthHint = 400;
		tabFolder.setLayoutData(gridData4Folder);

		createComposites();
		createTabFolders();

		return container;
	}

	private void createComposites() {
		filterComposites[0] = new MethodFilterComposite(tabFolder, getFilterForClass(MethodFilter.class));
		filterComposites[1] = new RuleFilterComposite(tabFolder, getFilterForClass(RulesFilter.class));
		filterComposites[2] = new ServerFilterComposite(tabFolder, getFilterForClass(ServerFilter.class));
		filterComposites[3] = new ClientFilterComposite(tabFolder, getFilterForClass(ClientFilter.class));
		filterComposites[4] = new StatusCodeFilterComposite(tabFolder, getFilterForClass(StatusCodeFilter.class));
	}

	private void showAllFromEveryComposite() {
		for (AbstractFilterComposite composite : filterComposites) {
			composite.showAll();
		}
	}

	private void createTabFolders() {
		for (AbstractFilterComposite composite : filterComposites) {
			TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
			tabItem.setText(composite.getFilterName());
			tabItem.setControl(composite);
		}
	}

	private void updateFilterManager() {
		for (AbstractFilterComposite composite : filterComposites) {
			if (composite.getFilter().isDeactivated()) {
				exchangesView.getFilterManager().removeFilter(composite.getFilter().getClass());
			} else {
				exchangesView.getFilterManager().addFilter(composite.getFilter());
			}
		}
	}

	@Override
	protected void okPressed() {
		if (btRemoveFilters.getSelection()) {
			exchangesView.getFilterManager().removeAllFilters();
			exchangesView.reloadAll();
			return;
		}

		updateFilterManager();

		exchangesView.reloadAll();
		super.okPressed();
	}

}