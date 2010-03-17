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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.predic8.membrane.core.exchange.accessors.ClientExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.DurationExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.ExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.MethodExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.PathExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.RequestContentLengthExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.RequestContentTypeExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.ResponseContentLengthExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.ResponseContentTypeExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.RuleExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.ServerExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.StatusCodeExchangeAccessor;
import com.predic8.membrane.core.exchange.accessors.TimeExchangeAccessor;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ExchangesTableSorterDialog extends Dialog {

	private ExchangesView exchangesView;

	private List<Combo> comboSorters = new ArrayList<Combo>();

	private Button btDisable;

	private Button btEnable;

	private Composite childComp;

	private List<Button> addButtons = new ArrayList<Button>();

	private Button btAsc;

	private Button btDesc;

	private String[] sortNames = { StatusCodeExchangeAccessor.ID, TimeExchangeAccessor.ID, RuleExchangeAccessor.ID, MethodExchangeAccessor.ID, PathExchangeAccessor.ID, ClientExchangeAccessor.ID, ServerExchangeAccessor.ID, RequestContentTypeExchangeAccessor.ID, RequestContentLengthExchangeAccessor.ID, ResponseContentTypeExchangeAccessor.ID, ResponseContentLengthExchangeAccessor.ID, DurationExchangeAccessor.ID };

	public ExchangesTableSorterDialog(Shell parentShell, ExchangesView parent) {
		super(parentShell);
		this.exchangesView = parent;

	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Exchange Sorters");
		shell.setSize(440, 560);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(createTopLayout());

		createDisableButton(container);

		createEnableButton(container);

		new Label(container, SWT.NONE).setText("");

		childComp = createChildComposite(container);

		Label lbComboSorters = new Label(childComp, SWT.NONE);
		lbComboSorters.setText("Sort By ");
		lbComboSorters.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));

		comboSorters.add(createComboSorter());
		addButtons.add(createAddButton());

		comboSorters.add(createComboSorter());
		addButtons.add(createAddButton());

		comboSorters.add(createComboSorter());

		Composite compOrder = createOrderButtonComposite(container);

		btAsc = new Button(compOrder, SWT.RADIO);
		btAsc.setText("Ascending");
		btAsc.setSelection(true);

		btDesc = new Button(compOrder, SWT.RADIO);
		btDesc.setText("Descending");

		if (!exchangesView.getComparator().isEmpty()) {
			btEnable.setSelection(true);
			btDisable.setSelection(false);
			btAsc.setSelection(exchangesView.getComparator().isAscending());
			btDesc.setSelection(!exchangesView.getComparator().isAscending());
		}

		readAccessors();

		return container;
	}

	private void readAccessors() {
		for (int i = 0; i < exchangesView.getComparator().getAccessors().size(); i++) {
			activateComboSorter(i);
			if (i != 0) {
				addButtons.get(i - 1).setSelection(true);
			}
		}
	}

	private Composite createChildComposite(Composite container) {
		Composite comp = new Composite(container, SWT.BORDER);
		comp.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));

		GridData gData = new GridData();
		gData.grabExcessHorizontalSpace = true;
		gData.grabExcessVerticalSpace = true;
		gData.widthHint = 370;
		gData.heightHint = 300;
		comp.setLayoutData(gData);

		comp.setLayout(createChildLayout());
		return comp;
	}

	private Composite createOrderButtonComposite(Composite container) {
		Composite composite = new Composite(container, SWT.BORDER);
		composite.setLayout(new FillLayout(SWT.VERTICAL));

		GridData gData = new GridData();
		gData.grabExcessHorizontalSpace = true;
		gData.grabExcessVerticalSpace = true;
		gData.widthHint = 370;
		gData.heightHint = 40;
		composite.setLayoutData(gData);
		return composite;
	}

	private void activateComboSorter(int index) {
		comboSorters.get(index).setEnabled(true);
		comboSorters.get(index).setVisible(true);
		comboSorters.get(index).setText(exchangesView.getComparator().getAccessors().get(index).getId());
		if (index > 0)
			addButtons.get(index - 1).setVisible(true);
	}

	private Combo createComboSorter() {
		final Combo combo = createComboSorters(true);
		final int index = comboSorters.size();
		combo.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (combo.getSelectionIndex() >= 0) {
					addButtons.get(index).setVisible(true);
				}
			}
		});
		combo.setVisible(comboSorters.size() == 0);
		return combo;
	}

	private void createEnableButton(Composite container) {
		btEnable = new Button(container, SWT.RADIO);
		btEnable.setText("Enable exchange sorting");
		btEnable.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btEnable.getSelection()) {
					setControlsEnabled(true);
				}
			}

		});
		btEnable.setSelection(!exchangesView.getComparator().isEmpty());
	}

	private void createDisableButton(Composite container) {
		btDisable = new Button(container, SWT.RADIO);
		btDisable.setText("Disable exchange sorting");
		btDisable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btDisable.getSelection()) {
					setControlsEnabled(false);
					btAsc.setSelection(true);
					btDesc.setSelection(false);
				}
			}
		});
		btDisable.setSelection(exchangesView.getComparator().isEmpty());
	}

	private GridLayout createChildLayout() {
		GridLayout layoutChild = new GridLayout();
		layoutChild.marginTop = 15;
		layoutChild.marginLeft = 15;
		layoutChild.marginBottom = 15;
		layoutChild.marginRight = 15;
		layoutChild.numColumns = 2;
		layoutChild.verticalSpacing = 12;
		return layoutChild;
	}

	private GridLayout createTopLayout() {
		GridLayout layout = new GridLayout();
		layout.marginTop = 25;
		layout.marginLeft = 25;
		layout.marginBottom = 25;
		layout.marginRight = 25;
		layout.numColumns = 1;
		return layout;
	}

	private Button createAddButton() {
		final int index = comboSorters.size() - 1;
		Button bt = new Button(childComp, SWT.CHECK | SWT.LEFT);
		bt.setText("and");
		bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		bt.setVisible(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (addButtons.get(index).getSelection()) {
					comboSorters.get(index + 1).setVisible(true);
					comboSorters.get(index + 1).setEnabled(true);
				} else {
					makeControlsInvisible(index);
				}
			}
		});
		return bt;
	}

	private Combo createComboSorters(boolean visible) {
		Combo combo = new Combo(childComp, SWT.READ_ONLY);
		combo.setItems(sortNames);
		combo.setEnabled(false);
		combo.setVisible(visible);
		return combo;
	}

	@Override
	protected void okPressed() {
		exchangesView.getComparator().removeAllAccessors();
		if (btDisable.getSelection()) {
			super.okPressed();
			return;
		}

		List<ExchangeAccessor> accessors = new ArrayList<ExchangeAccessor>();
		for (Combo combo : comboSorters) {
			if (!combo.isVisible() || combo.getSelectionIndex() < 0)
				continue;
			accessors.add(getAccessor(combo.getSelectionIndex()));
		}
		exchangesView.getComparator().addAccessors(accessors.toArray(new ExchangeAccessor[accessors.size()]));
		if (accessors.size() > 0) {
			exchangesView.getComparator().setAscending(btAsc.getSelection());
			exchangesView.refreshTable(true);
		}
		super.okPressed();
	}

	private ExchangeAccessor getAccessor(int index) {
		switch (index) {
		case 0:
			return new StatusCodeExchangeAccessor();

		case 1:
			return new TimeExchangeAccessor();

		case 2:
			return new RuleExchangeAccessor();

		case 3:
			return new MethodExchangeAccessor();

		case 4:
			return new PathExchangeAccessor();
		case 5:
			return new ClientExchangeAccessor();

		case 6:
			return new ServerExchangeAccessor();

		case 7:
			return new RequestContentTypeExchangeAccessor();

		case 8:
			return new RequestContentLengthExchangeAccessor();

		case 9:
			return new ResponseContentTypeExchangeAccessor();

		case 10:
			return new ResponseContentLengthExchangeAccessor();

		case 11:
			return new DurationExchangeAccessor();

		}
		throw new RuntimeException("Invalid selection index. No accessor found.");
	}

	private void setControlsEnabled(boolean enabled) {
		for (Combo combo : comboSorters) {
			combo.setEnabled(enabled);
		}
		for (Button bt : addButtons) {
			bt.setEnabled(enabled);
		}
	}

	private void makeControlsInvisible(final int index) {
		for (int i = index + 1; i < comboSorters.size(); i++) {
			comboSorters.get(i).setVisible(false);
			if (i < addButtons.size()) {
				addButtons.get(i).setSelection(false);
				addButtons.get(i).setVisible(false);
				addButtons.get(i).notifyListeners(SWT.Selection, null);
			}
		}
	}

}