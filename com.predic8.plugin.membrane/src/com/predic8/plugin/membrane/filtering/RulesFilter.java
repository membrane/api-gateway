package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.RuleKey;

public class RulesFilter implements ExchangesFilter {

	
	private boolean showAllRules;
	
	private Set<RuleKey> displayedRules = new HashSet<RuleKey>();
	
	public RulesFilter() {
		showAllRules = true;
	}

	public boolean isShowAllRules() {
		return showAllRules;
	}

	public void setShowAllRules(boolean showAllRules) {
		this.showAllRules = showAllRules;
	}

	public Set<RuleKey> getDisplayedRules() {
		return displayedRules;
	}

	public void setDisplayedRules(Set<RuleKey> displayedRules) {
		this.displayedRules = displayedRules;
	}

	public boolean filter(Exchange exc) {
		if (showAllRules)
			return true;
		
		if (displayedRules.contains(exc.getRule().getRuleKey()))
			return true;
		
		return false;
	}

	public boolean isDeactivated() {
		if (showAllRules)
			return true;
		if (displayedRules.isEmpty())
			return true;
		return false;
	}

}
