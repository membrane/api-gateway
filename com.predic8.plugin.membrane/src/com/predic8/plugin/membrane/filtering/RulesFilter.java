package com.predic8.plugin.membrane.filtering;

import java.util.HashSet;
import java.util.Set;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.RuleKey;

public class RulesFilter extends AbstractExchangesFilter {

	private Set<RuleKey> displayedItems = new HashSet<RuleKey>();
	
	public RulesFilter() {
		showAll = true;
	}

	public Set<RuleKey> getDisplayedRules() {
		return displayedItems;
	}

	public void setDisplayedRules(Set<RuleKey> displayedRules) {
		this.displayedItems = displayedRules;
	}

	public boolean filter(Exchange exc) {
		if (showAll)
			return true;
		
		if (displayedItems.contains(exc.getRule().getRuleKey()))
			return true;
		
		return false;
	}

	public boolean isDeactivated() {
		if (showAll)
			return true;
		if (displayedItems.isEmpty())
			return true;
		return false;
	}

}
