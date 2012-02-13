package com.predic8.membrane.core.interceptor.administration;

import java.util.List;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;

public class RuleUtil {
	public static String getRuleIdentifier(Rule rule) {
		return rule.toString() + ":"
				+ rule.getKey().getPort();
	}
	
	public static Rule findRuleByIdentifier(Router router, String name) throws Exception {
		List<Rule> rules = router.getRuleManager().getRules();
		for (Rule rule : rules) {
			if ( name.equals(getRuleIdentifier(rule))) return rule;
		}
		return null;
	}
	
}
