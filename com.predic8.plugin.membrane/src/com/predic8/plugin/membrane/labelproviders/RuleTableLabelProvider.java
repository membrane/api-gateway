package com.predic8.plugin.membrane.labelproviders;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;


public class RuleTableLabelProvider extends LabelProvider implements ITableLabelProvider {

	public Image getColumnImage(Object element, int columnIndex) {
		
		return null;
	}

	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof ForwardingRule) {
			ForwardingRule rule = (ForwardingRule)element;
			switch (columnIndex) {
			case 0:
				return rule.getRuleKey().getHost();
			case 1:
				return rule.getRuleKey().getPort() + "";
			case 2:
				return rule.getRuleKey().getMethod();
			case 3:
				return rule.getRuleKey().getPath();
			case 4:
				return rule.getTargetHost();
			case 5:
				return rule.getTargetPort() + "";
			default:
				throw new RuntimeException("Rule table viewer must have only 6 columns for ForwardingRule");
			}
		} else if (element instanceof ProxyRule) {
			ProxyRule rule = (ProxyRule)element;
			switch (columnIndex) {
			case 0:
				return rule.getRuleKey().getHost();
			case 1:
				return rule.getRuleKey().getPort() + "";
			case 2:
				return rule.getRuleKey().getMethod();
			case 3:
				return rule.getRuleKey().getPath();
			case 4:
				return "";
			case 5:
				return "";
			default:
				throw new RuntimeException("Rule table viewer must have only 6 columns for ProxyRule");
			}
		}
		
		throw new RuntimeException("Unknown object type: labels are provided only for ProxyRule or ForwardingRule.");
	}

	

}
