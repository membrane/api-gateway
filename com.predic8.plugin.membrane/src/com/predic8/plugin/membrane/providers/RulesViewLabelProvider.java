package com.predic8.plugin.membrane.providers;

import java.text.NumberFormat;

import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.statistics.RuleStatistics;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;


public class RulesViewLabelProvider extends LabelProvider implements ITableLabelProvider, ITableColorProvider  {
	
	private NumberFormat nf = NumberFormat.getInstance();
	
	private Image ruleProxyImage;
	
	private Image ruleForwardingImage;
	
	public RulesViewLabelProvider() {
		nf.setMaximumFractionDigits(3);
		ruleProxyImage =  MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_RULE_PROXY).createImage();
		ruleForwardingImage =  MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_RULE_REVERSE_PROXY).createImage();
	}
	
	public Image getColumnImage(Object element, int columnIndex) {
		if (columnIndex != 0)
			return null;
		if (element instanceof ForwardingRule) {
			return ruleForwardingImage;
		}
		return ruleProxyImage;
	}

	public String getColumnText(Object element, int columnIndex) {
		Rule rule = (Rule)element;
		
		RuleStatistics statistics = Router.getInstance().getExchangeStore().getStatistics(rule.getRuleKey());
		
		switch (columnIndex) {
		case 0:
			return rule.toString();
		case 1:
			return "" + statistics.getCountTotal();
		default:
			throw new RuntimeException("Table in rules view  must have only 2 columns");
		}
	}

	public Color getBackground(Object element, int columnIndex) {
		
		return null;
	}

	public Color getForeground(Object element, int columnIndex) {
		
		return null;
	}

}
