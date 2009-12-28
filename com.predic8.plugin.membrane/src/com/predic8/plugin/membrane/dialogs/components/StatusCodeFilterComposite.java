package com.predic8.plugin.membrane.dialogs.components;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.plugin.membrane.filtering.StatusCodeFilter;

public class StatusCodeFilterComposite extends Composite {

	private List<Button> buttons = new ArrayList<Button>();
	
	private StatusCodeFilter statusCodeFilter;
	
	private Button btShowAllStatusCodes;

	private Button btShowSelectedStatusCodesOnly;

	
	public StatusCodeFilterComposite(Composite parent, StatusCodeFilter filter) {
		super(parent, SWT.NONE);
		statusCodeFilter = filter;
		
		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		setLayout(layout);

		Group rulesGroup = new Group(this, SWT.NONE);
		rulesGroup.setText("Show Servers");
		rulesGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4RuleGroup = new GridLayout();
		gridLayout4RuleGroup.marginTop = 10;
		gridLayout4RuleGroup.marginLeft = 10;
		gridLayout4RuleGroup.marginRight = 10;
		rulesGroup.setLayout(gridLayout4RuleGroup);

		btShowAllStatusCodes = new Button(rulesGroup, SWT.RADIO);
		btShowAllStatusCodes.setText("Display exchanges with any status code");
		btShowAllStatusCodes.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				if (btShowAllStatusCodes.getSelection()) {
					btShowSelectedStatusCodesOnly.setSelection(false);
					for (Button button : buttons) {
						button.setEnabled(false);
						statusCodeFilter.setShowAllStatusCodes(true);
					}
				}
			}
		});

		btShowSelectedStatusCodesOnly = new Button(rulesGroup, SWT.RADIO);
		btShowSelectedStatusCodesOnly.setText("Display exchanges with selected status codes only");
		btShowSelectedStatusCodesOnly.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (btShowSelectedStatusCodesOnly.getSelection()) {
					Set<Integer> toDisplay = statusCodeFilter.getDisplayedStatusCodes();
					for (Button button : buttons) {
						button.setEnabled(true);
						if (toDisplay.contains(button.getData())) {
							button.setSelection(true);
						} else {
							button.setSelection(false);
						}
					}
					statusCodeFilter.setShowAllStatusCodes(false);
				}
			}
		});

		Composite rulesComposite = new Composite(rulesGroup, SWT.BORDER);
		rulesComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		GridData rulesGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
		rulesComposite.setLayoutData(rulesGridData);

		GridLayout rulesLayout = new GridLayout();
		rulesComposite.setLayout(rulesLayout);

		Object[] excanges = Router.getInstance().getExchangeStore().getAllExchanges();
		Set<Integer> statusCodes = new HashSet<Integer>();
		if (excanges != null && excanges.length > 0) {
			for (Object object : excanges) {
				try {
					Exchange exc = (Exchange)object;
					if (exc.getResponse() == null)
						continue;
					statusCodes.add(exc.getResponse().getStatusCode());
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
		}
		
		
		for (Integer statusCode : statusCodes) {
			final Button bt = new Button(rulesComposite, SWT.CHECK);
			bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			bt.setText(Integer.toString(statusCode));
			bt.setData(statusCode);
			if (statusCodeFilter.getDisplayedStatusCodes().contains(statusCode)) {
				bt.setSelection(true);
			}

			bt.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (bt.getSelection()) {
						statusCodeFilter.getDisplayedStatusCodes().add((Integer) bt.getData());
					} else {
						statusCodeFilter.getDisplayedStatusCodes().remove((Integer) bt.getData());
					}
				}
			});
			buttons.add(bt);
		}

		if (statusCodeFilter.isShowAllStatusCodes()) {
			btShowAllStatusCodes.setSelection(true);
			btShowAllStatusCodes.notifyListeners(SWT.Selection, null);
		} else {
			btShowSelectedStatusCodesOnly.setSelection(true);
			btShowSelectedStatusCodesOnly.notifyListeners(SWT.Selection, null);
		}

	}


	public StatusCodeFilter getStatusCodeFilter() {
		return statusCodeFilter;
	}


	public void showAllStatusCodes() {
		btShowAllStatusCodes.setSelection(true);
		btShowAllStatusCodes.notifyListeners(SWT.Selection, null);
	}


}
