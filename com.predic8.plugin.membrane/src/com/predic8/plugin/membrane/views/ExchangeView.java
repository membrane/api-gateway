package com.predic8.plugin.membrane.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.plugin.membrane.providers.ExchangeViewerContentProvider;
import com.predic8.plugin.membrane.viewers.ExchangeViewer;

public class ExchangeView extends ViewPart {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.ExchangeView";

	private ExchangeViewer exchangeViewer;

	public ExchangeView() {
		
	}

	@Override
	public void createPartControl(Composite parent) {
		exchangeViewer = new ExchangeViewer(parent, SWT.NONE);
		exchangeViewer.setContentProvider(new ExchangeViewerContentProvider(exchangeViewer));
	}

	@Override
	public void setFocus() {
		exchangeViewer.setFocus();
	}

	public ExchangeViewer getExchangeViewer() {
		return exchangeViewer;
	}

	public void setExchange(Exchange exchange) {
		exchangeViewer.setInput(exchange);
		exchangeViewer.updateUIStatus();
	}
}
