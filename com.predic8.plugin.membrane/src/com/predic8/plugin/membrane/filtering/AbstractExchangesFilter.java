package com.predic8.plugin.membrane.filtering;


public abstract class AbstractExchangesFilter implements ExchangesFilter {

	protected boolean showAll;
	
	public boolean isShowAll() {
		return showAll;
	}

	public void setShowAll(boolean showAll) {
		this.showAll = showAll;
	}

}
