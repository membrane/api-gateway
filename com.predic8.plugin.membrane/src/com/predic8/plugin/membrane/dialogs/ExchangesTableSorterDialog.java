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

package com.predic8.plugin.membrane.dialogs;

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

import com.predic8.membrane.core.exchange.ExchangeComparator;
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
	
	private Combo[] comboSorters = new Combo[3];
	
	private Button btDisable;
	
	private Button btEnable;
	
	private Composite childComp;
	
	private ExchangeComparator comparator;
	
	private Button[] addButtons = new Button[2];
	
	private ExchangeAccessor[] accessors = new ExchangeAccessor[3];
	
	private Button btAsc;
	
	private Button btDesc;
	
	
	private String[] sortNames = {
		StatusCodeExchangeAccessor.ID,
		TimeExchangeAccessor.ID,
		RuleExchangeAccessor.ID,
		MethodExchangeAccessor.ID,
		PathExchangeAccessor.ID,
		ClientExchangeAccessor.ID,
		ServerExchangeAccessor.ID,
		RequestContentTypeExchangeAccessor.ID,
		RequestContentLengthExchangeAccessor.ID,
		ResponseContentTypeExchangeAccessor.ID,
		ResponseContentLengthExchangeAccessor.ID,
		DurationExchangeAccessor.ID
	};
	
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
		
		comparator = exchangesView.getComparator();
		
		createDisableButton(container);
		 
		createEnableButton(container);
		
		new Label(container, SWT.NONE).setText("");
		
		createChildComposite(container);
		
		
		Label lbComboSorters = new Label(childComp, SWT.NONE);
		lbComboSorters.setText("Sort By "); 
		lbComboSorters.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		
		createComboSorters1();
		
		addButtons[0] = createAddButton();
		addButtons[0].addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (addButtons[0].getSelection()) {
					comboSorters[1].setText("");
					comboSorters[1].setVisible(true);
					comboSorters[1].setEnabled(true);
				} else {
					accessors[1] = null;
					
					comboSorters[1].setText("");
					comboSorters[1].setVisible(false);
					
					comboSorters[2].setText("");
					comboSorters[2].setVisible(false);
					
					addButtons[1].setSelection(false);
					addButtons[1].setVisible(false);
					addButtons[1].notifyListeners(SWT.Selection, null);
				}
			}
		});
		
		createComboSorters2();
		
		addButtons[1] = createAddButton();
		addButtons[1].addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (addButtons[1].getSelection()) {
					comboSorters[2].setText("");
					comboSorters[2].setVisible(true);
					comboSorters[2].setEnabled(true);
				} else {
					accessors[2] = null;
					comboSorters[2].setText("");
					comboSorters[2].select(-1);
					comboSorters[2].setVisible(false);
				}
			}
		});
		
		createComboSorters3();
		
	
		Composite compOrder = createOrderButtonComposite(container);
				
		btAsc = new Button(compOrder, SWT.RADIO);
		btAsc.setText("Ascending");
		btAsc.setSelection(true);
		
		btDesc = new Button(compOrder, SWT.RADIO);
		btDesc.setText("Descending");
		
		
		setVisibleOrderButtons(false);
		
		
		
		if (!comparator.isEmpty()) {
			btEnable.setSelection(true);
			btDisable.setSelection(false);
			btAsc.setVisible(true);
			btDesc.setVisible(true);
			if (comparator.isAscending()) {
				btAsc.setSelection(true);
				btDesc.setSelection(false);
			} else {
				btAsc.setSelection(false);
				btDesc.setSelection(true);
			}
		}
		
		switch (comparator.getAccessors().size()) {
		case 1:
			activateComboSorter(0);
			break;
		case 2:
			activateComboSorter(0);
			addButtons[0].setSelection(true);
			activateComboSorter(1);
			break;
			
		case 3:
			activateComboSorter(0);
			addButtons[0].setSelection(true);
			activateComboSorter(1);
			addButtons[1].setSelection(true);
			activateComboSorter(2);
			break;
		}
		
		return container;
	}

	private void createChildComposite(Composite container) {
		childComp = new Composite(container, SWT.BORDER);
		childComp.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		GridData gdChildComp = new GridData();
		gdChildComp.grabExcessHorizontalSpace = true;
		gdChildComp.grabExcessVerticalSpace = true;
		gdChildComp.widthHint = 370;
		gdChildComp.heightHint = 300;
		childComp.setLayoutData(gdChildComp);
		
		childComp.setLayout(createChildLayout());
	}

	private Composite createOrderButtonComposite(Composite container) {
		Composite compOrder = new Composite(container, SWT.BORDER);
		compOrder.setLayout(new FillLayout(SWT.VERTICAL));
		
		GridData orderGridData = new GridData();
		orderGridData.grabExcessHorizontalSpace = true;
		orderGridData.grabExcessVerticalSpace = true;
		orderGridData.widthHint = 370;
		orderGridData.heightHint = 40;
		compOrder.setLayoutData(orderGridData);
		return compOrder;
	}

	private void activateComboSorter(int index) {
		comboSorters[index].setEnabled(true);
		comboSorters[index].setText(comparator.getAccessors().get(index).getId());
		accessors[index] = comparator.getAccessors().get(index);
		addButtons[index].setVisible(true);
	}

	private void createComboSorters3() {
		comboSorters[2] = createComboSorters(false);
		comboSorters[2].addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				accessors[2] = getAccessor(comboSorters[2].getSelectionIndex());
			}
		});
	}

	private void createComboSorters2() {
		comboSorters[1] = createComboSorters(false);
		comboSorters[1].addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				accessors[1] = getAccessor(comboSorters[1].getSelectionIndex());
				if (comboSorters[1].getSelectionIndex() >= 0) {
					addButtons[1].setVisible(true);
				}
				
			}
		});
	}

	private void createComboSorters1() {
		comboSorters[0] = createComboSorters(true);
		comboSorters[0].addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				accessors[0] = getAccessor(comboSorters[0].getSelectionIndex()); 
				if (comboSorters[0].getSelectionIndex() >= 0) {
					addButtons[0].setVisible(true);
					setVisibleOrderButtons(true);
				} else {
					addButtons[0].setSelection(false);
					addButtons[0].setVisible(false);
					setVisibleOrderButtons(false);
				}
			}
		});
	}

	private void createEnableButton(Composite container) {
		btEnable = new Button(container, SWT.RADIO);
		btEnable.setText("Enable exchange sorting");
		btEnable.addSelectionListener(new SelectionAdapter() {
		
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btEnable.getSelection()) {
					comboSorters[0].setEnabled(true);
				}
			}
		
		});
		btEnable.setSelection(!comparator.isEmpty());
	}

	private void createDisableButton(Composite container) {
		btDisable = new Button(container, SWT.RADIO);
		btDisable.setText("Disable exchange sorting");
		btDisable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btDisable.getSelection()) {
					
					accessors[0] = null;
					accessors[1] = null;
					accessors[2] = null;
					
					comboSorters[0].setText("");
					comboSorters[0].select(-1);
					comboSorters[0].setEnabled(false);
					
					btAsc.setSelection(true);
					btDesc.setSelection(false);
					
					setVisibleOrderButtons(false);
					
					deactivateAddButtons();
				} 
			}
		});
		btDisable.setSelection(comparator.isEmpty());
	}

	private void deactivateAddButtons() {
		addButtons[0].setSelection(false);
		addButtons[0].notifyListeners(SWT.Selection, null);
		addButtons[0].setVisible(false);
		
		addButtons[1].setSelection(false);
		addButtons[1].notifyListeners(SWT.Selection, null);
		addButtons[1].setVisible(false);
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
		Button bt = new Button(childComp, SWT.CHECK| SWT.LEFT);
		bt.setText("and");
		bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		bt.setVisible(false);
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
		comparator.removeAllAccessors();
		if (!btDisable.getSelection()) {
			comparator.addAccessors(accessors);
			if (accessors[0] != null)
				comparator.setAscending(btAsc.getSelection());
		}
		exchangesView.setComperator(comparator);
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

	private void setVisibleOrderButtons(boolean show) {
		btAsc.setVisible(show);
		btDesc.setVisible(show);
	}
	
	
}