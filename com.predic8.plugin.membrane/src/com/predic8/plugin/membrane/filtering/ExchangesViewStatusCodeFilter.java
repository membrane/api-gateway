package com.predic8.plugin.membrane.filtering;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import com.predic8.membrane.core.exchange.Exchange;

public class ExchangesViewStatusCodeFilter extends ViewerFilter {

	private int statusCode = 0;

	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		if (statusCode == 0)
			return true;
		
		if (((Exchange) element).getResponse() == null)
			return false;
		
		return ((Exchange) element).getResponse().getStatusCode() >= statusCode && ((Exchange) element).getResponse().getStatusCode() < (statusCode + 100);

	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	

}
