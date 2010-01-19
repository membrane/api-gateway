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
	
	private Combo comboSorters;
	
	private Combo comboSorters2;
	
	private Combo comboSorters3;
	
	private Button btDisable;
	
	private Button btEnable;
	
	private Composite childComp;
	
	private ExchangeComparator comparator;
	
	private Button btAdd1, btAdd2;
	
	private ExchangeAccessor accessor1;
	
	private ExchangeAccessor accessor2;
	
	private ExchangeAccessor accessor3;
	
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
		
		btDisable = new Button(container, SWT.RADIO);
		btDisable.setText("Disable exchange sorting");
		btDisable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btDisable.getSelection()) {
					
					accessor1 = null;
					accessor2 = null;
					accessor3 = null;
					
					comboSorters.setText("");
					comboSorters.select(-1);
					comboSorters.setEnabled(false);
					
					btAsc.setSelection(true);
					btDesc.setSelection(false);
					btAsc.setVisible(false);
					btDesc.setVisible(false);
					
					btAdd1.setSelection(false);
					btAdd1.notifyListeners(SWT.Selection, null);
					
					btAdd2.setSelection(false);
					btAdd2.notifyListeners(SWT.Selection, null);
					
					btAdd1.setVisible(false);
					btAdd2.setVisible(false);
					
				} 
			}
		});
		btDisable.setSelection(comparator.isEmpty());
		 
		btEnable = new Button(container, SWT.RADIO);
		btEnable.setText("Enable exchange sorting");
		btEnable.addSelectionListener(new SelectionAdapter() {
		
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btEnable.getSelection()) {
					comboSorters.setEnabled(true);
				}
			}
		
		});
		btEnable.setSelection(!comparator.isEmpty());
		
		new Label(container, SWT.NONE).setText("");
		
		childComp = new Composite(container, SWT.BORDER);
		childComp.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		GridData gdChildComp = new GridData();
		gdChildComp.grabExcessHorizontalSpace = true;
		gdChildComp.grabExcessVerticalSpace = true;
		gdChildComp.widthHint = 370;
		gdChildComp.heightHint = 300;
		childComp.setLayoutData(gdChildComp);
		
		childComp.setLayout(createChildLayout());
		
		
		Label lbComboSorters = new Label(childComp, SWT.NONE);
		lbComboSorters.setText("Sort By "); 
		lbComboSorters.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		
		comboSorters = createComboSorters();
		comboSorters.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (comboSorters.getSelectionIndex() >= 0) {
					btAdd1.setVisible(true);
					btAsc.setVisible(true);
					btDesc.setVisible(true);
				} else {
					btAdd1.setSelection(false);
					btAdd1.setVisible(false);
					btAsc.setVisible(false);
					btDesc.setVisible(false);
				}
				accessor1 = getAccessor(comboSorters.getSelectionIndex()); 
			}
		});
		
		btAdd1 = createAddButton();
		btAdd1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btAdd1.getSelection()) {
					comboSorters2.setText("");
					comboSorters2.setVisible(true);
				} else {
					accessor2 = null;
					comboSorters2.setText("");
					comboSorters2.setVisible(false);
					comboSorters3.setText("");
					comboSorters3.setVisible(false);
					btAdd2.setSelection(false);
					btAdd2.setVisible(false);
					btAdd2.notifyListeners(SWT.Selection, null);
				}
			}
		});
		
		comboSorters2 = createComboSorters();
		comboSorters2.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				accessor2 = getAccessor(comboSorters2.getSelectionIndex());
				if (comboSorters2.getSelectionIndex() >= 0) {
					btAdd2.setVisible(true);
				}
				
			}
		});
		
		btAdd2 = createAddButton();
		btAdd2.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btAdd2.getSelection()) {
					comboSorters3.setText("");
					comboSorters3.setVisible(true);
				} else {
					accessor3 = null;
					comboSorters3.setText("");
					comboSorters3.select(-1);
					comboSorters3.setVisible(false);
				}
			}
		});
		
		comboSorters3 = createComboSorters();
		comboSorters3.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				accessor3 = getAccessor(comboSorters3.getSelectionIndex());
			}
		});
		
		
		
		Composite compositeX = new Composite(container, SWT.BORDER);
		
		
		GridData gdCompX = new GridData();
		gdCompX.grabExcessHorizontalSpace = true;
		gdCompX.grabExcessVerticalSpace = true;
		gdCompX.widthHint = 370;
		gdCompX.heightHint = 40;
		compositeX.setLayoutData(gdCompX);
				
		btAsc = new Button(compositeX, SWT.RADIO);
		btAsc.setText("Ascending");
		btAsc.setSelection(true);
		btAsc.setVisible(false);
		
		btDesc = new Button(compositeX, SWT.RADIO);
		btDesc.setText("Descending");
		btDesc.setVisible(false);
		
		FillLayout fillLayout = new FillLayout(SWT.VERTICAL);
		compositeX.setLayout(fillLayout);
		
		
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
			comboSorters.setEnabled(true);
			comboSorters.setText(comparator.getAccessors().get(0).getId());
			accessor1 = comparator.getAccessors().get(0);
			btAdd1.setVisible(true);
			break;
		case 2:
			comboSorters.setEnabled(true);
			comboSorters.setText(comparator.getAccessors().get(0).getId());
			accessor1 = comparator.getAccessors().get(0);
			btAdd1.setVisible(true);
			btAdd1.setSelection(true);
			comboSorters2.setVisible(true);
			comboSorters2.setText(comparator.getAccessors().get(1).getId());
			accessor2 = comparator.getAccessors().get(1);
			btAdd2.setVisible(true);
			break;
			
		case 3:
			comboSorters.setText(comparator.getAccessors().get(0).getId());
			accessor1 = comparator.getAccessors().get(0);
			btAdd1.setVisible(true);
			btAdd1.setSelection(true);
			comboSorters2.setVisible(true);
			comboSorters2.setText(comparator.getAccessors().get(1).getId());
			accessor2 = comparator.getAccessors().get(1);
			btAdd2.setVisible(true);
			btAdd2.setSelection(true);
			comboSorters3.setVisible(true);
			comboSorters3.setText(comparator.getAccessors().get(2).getId());
			accessor3 = comparator.getAccessors().get(2);
			break;
		default:
			break;
		}
		
		return container;
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

	private Combo createComboSorters() {
		Combo combo = new Combo(childComp, SWT.READ_ONLY);
		combo.setItems(sortNames);
		combo.setEnabled(false);
		return combo;
	}

	@Override
	protected void okPressed() {
		
		comparator.removeAllAccessors();
		
		if (!btDisable.getSelection()) {
			comparator.addAccessor(accessor1);
			comparator.addAccessor(accessor2);
			comparator.addAccessor(accessor3);
			if (accessor1 != null)
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
	
	
}