package com.predic8.plugin.membrane.filtering;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import com.predic8.membrane.core.exchange.Exchange;

public class ExchangesViewMethodFilter extends ViewerFilter {

	private String requestMethod = "";

	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		if (requestMethod.equals(""))
			return true;
		return ((Exchange) element).getRequest().getMethod().contains(requestMethod);

	}

	public String getRequestMethod() {
		return requestMethod;
	}

	public void setRequestMethod(String requestMethod) {
		if (requestMethod != null)
			this.requestMethod = requestMethod;
	}

}
