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
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Monitor;
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

		GridLayout layout = new GridLayout();
		layout.marginTop = 25;
		layout.marginLeft = 25;
		layout.marginBottom = 25;
		layout.marginRight = 25;
		layout.numColumns = 1;
		container.setLayout(layout);

		
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
		
		Label lbDummy1 = new Label(container, SWT.NONE);
		lbDummy1.setText("");
		
		childComp = new Composite(container, SWT.BORDER);
		childComp.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		GridData gdChildComp = new GridData();
		gdChildComp.grabExcessHorizontalSpace = true;
		gdChildComp.grabExcessVerticalSpace = true;
		gdChildComp.widthHint = 370;
		gdChildComp.heightHint = 300;
		childComp.setLayoutData(gdChildComp);
		
		GridLayout layoutChild = new GridLayout();
		layoutChild.marginTop = 15;
		layoutChild.marginLeft = 15;
		layoutChild.marginBottom = 15;
		layoutChild.marginRight = 15;
		layoutChild.numColumns = 2;
		layoutChild.verticalSpacing = 12;
		
		childComp.setLayout(layoutChild);
		
		
		Label lbComboSorters = new Label(childComp, SWT.NONE);
		lbComboSorters.setText("Sort By "); 
		lbComboSorters.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		
		comboSorters = new Combo(childComp, SWT.DROP_DOWN);
		comboSorters.setItems(sortNames);
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
				switch (comboSorters.getSelectionIndex()) {
				case 0:
					accessor1 = new StatusCodeExchangeAccessor();
					break;
					
				case 1:
					accessor1 = new TimeExchangeAccessor();
					break;
					
				case 2:
					accessor1 = new RuleExchangeAccessor();
					break;
					
				case 3:
					accessor1 = new MethodExchangeAccessor();
					break;
					
				case 4:
					accessor1 = new PathExchangeAccessor();
					break;
					
				case 5:
					accessor1 = new ClientExchangeAccessor();
					break;
					
				case 6:
					accessor1 = new ServerExchangeAccessor();
					break;
					
				case 7:
					accessor1 = new RequestContentTypeExchangeAccessor();
					break;
					
				case 8:
					accessor1 = new RequestContentLengthExchangeAccessor();
					break;
					
				case 9:
					accessor1 = new ResponseContentLengthExchangeAccessor();
					break;
					
				case 10:
					accessor1 = new DurationExchangeAccessor();
					break;
					
					default:
						return;
				} 
			}
		});
		
		btAdd1 = new Button(childComp, SWT.CHECK| SWT.LEFT);
		btAdd1.setText("more");
		btAdd1.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btAdd1.setVisible(false);
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
		
		comboSorters2 = new Combo(childComp, SWT.DROP_DOWN);
		comboSorters2.setItems(sortNames);
		comboSorters2.setVisible(false);
		comboSorters2.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				
				switch (comboSorters2.getSelectionIndex()) {
				case 0:
					accessor2 = new StatusCodeExchangeAccessor();
					break;
					
				case 1:
					accessor2 = new TimeExchangeAccessor();
					break;
					
				case 2:
					accessor2 = new RuleExchangeAccessor();
					break;
					
				case 3:
					accessor2 = new MethodExchangeAccessor();
					break;
					
				case 4:
					accessor2 = new PathExchangeAccessor();
					break;
					
				case 5:
					accessor2 = new ClientExchangeAccessor();
					break;
					
				case 6:
					accessor2 = new ServerExchangeAccessor();
					break;
					
				case 7:
					accessor2 = new RequestContentTypeExchangeAccessor();
					break;
					
				case 8:
					accessor2 = new RequestContentLengthExchangeAccessor();
					break;
					
				case 9:
					accessor1 = new ResponseContentLengthExchangeAccessor();
					break;
					
				case 10:
					accessor2 = new DurationExchangeAccessor();
					break;
					
					default:
						return;
				} 
				
				if (comboSorters2.getSelectionIndex() >= 0) {
					btAdd2.setVisible(true);
				}
				
			}
		});
		
		btAdd2 = new Button(childComp, SWT.CHECK | SWT.LEFT);
		btAdd2.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btAdd2.setText("more");
		btAdd2.setVisible(false);
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
		
		comboSorters3 = new Combo(childComp, SWT.DROP_DOWN);
		comboSorters3.setItems(sortNames);
		comboSorters3.setVisible(false);
		comboSorters3.addSelectionListener(new SelectionAdapter() {
		
			@Override
			public void widgetSelected(SelectionEvent e) {
				switch (comboSorters3.getSelectionIndex()) {
				case 0:
					accessor3 = new StatusCodeExchangeAccessor();
					break;
					
				case 1:
					accessor3 = new TimeExchangeAccessor();
					break;
					
				case 2:
					accessor3 = new RuleExchangeAccessor();
					break;
					
				case 3:
					accessor3 = new MethodExchangeAccessor();
					break;
					
				case 4:
					accessor3 = new PathExchangeAccessor();
					break;
					
				case 5:
					accessor3 = new ClientExchangeAccessor();
					break;
					
				case 6:
					accessor3 = new ServerExchangeAccessor();
					break;
					
				case 7:
					accessor3 = new RequestContentTypeExchangeAccessor();
					break;
					
				case 8:
					accessor3 = new RequestContentLengthExchangeAccessor();
					break;
					
				case 9:
					accessor3 = new ResponseContentLengthExchangeAccessor();
					break;
					
				case 10:
					accessor3 = new DurationExchangeAccessor();
					break;
					
					default:
						return;
				} 
			}
		});
		
		
		
		Composite compositeX = new Composite(container, SWT.BORDER);
		compositeX.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		
		
		GridData gdCompX = new GridData();
		gdCompX.grabExcessHorizontalSpace = true;
		gdCompX.grabExcessVerticalSpace = true;
		gdCompX.widthHint = 370;
		gdCompX.heightHint = 24;
		compositeX.setLayoutData(gdCompX);
		
		Label lbDummy3 = new Label(compositeX, SWT.NONE);
		lbDummy3.setText(" ");
		lbDummy3.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		Label lbDummy4 = new Label(compositeX, SWT.NONE);
		lbDummy4.setText(" ");
		lbDummy4.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		Label lbDummy5 = new Label(compositeX, SWT.NONE);
		lbDummy5.setText(" ");
		lbDummy5.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		Label lbDummy6 = new Label(compositeX, SWT.NONE);
		lbDummy6.setText(" ");
		lbDummy6.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		btAsc = new Button(compositeX, SWT.RADIO);
		btAsc.setText("Asc");
		btAsc.setSelection(true);
		btAsc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btAsc.setVisible(false);
		
		btDesc = new Button(compositeX, SWT.RADIO);
		btDesc.setText("Desc");
		btDesc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btDesc.setVisible(false);
		
		FillLayout fillLayout = new FillLayout(SWT.HORIZONTAL);
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

	@Override
	protected void initializeBounds() {
		super.initializeBounds();
		Shell shell = this.getShell();
		Monitor primary = shell.getMonitor();
		Rectangle bounds = primary.getBounds();
		Rectangle rect = shell.getBounds();
		int x = bounds.x + (bounds.width - rect.width) / 2;
		int y = bounds.y + (bounds.height - rect.height) / 2;
		shell.setLocation(x, y);
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
	
	
}