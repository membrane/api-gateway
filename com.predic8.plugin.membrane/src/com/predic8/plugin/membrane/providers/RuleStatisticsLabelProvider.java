package com.predic8.plugin.membrane.providers;

import java.text.NumberFormat;

import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.statistics.RuleStatistics;


public class RuleStatisticsLabelProvider extends LabelProvider implements ITableLabelProvider, ITableColorProvider  {

	
	NumberFormat nf = NumberFormat.getInstance();
	
	public RuleStatisticsLabelProvider() {
		nf.setMaximumFractionDigits(3);
	}
	
	public Image getColumnImage(Object element, int columnIndex) {
		
		return null;
	}

	public String getColumnText(Object element, int columnIndex) {
		Rule rule = (Rule)element;
		
		RuleStatistics statistics = Router.getInstance().getExchangeStore().getStatistics(rule.getRuleKey());
		String min = (statistics.getMin() < 0) ? "" : "" +statistics.getMin(); 
		String max = (statistics.getMax() < 0) ? "" : "" +statistics.getMax(); 
		String avg = (statistics.getAvg() < 0) ? "" : nf.format(statistics.getAvg()); 
		String error = (statistics.getCountError() == 0) ? "" : "" + statistics.getCountError(); 
		String bytesSent = (statistics.getBytesSent() == 0) ? "" : "" + statistics.getBytesSent(); 
		String bytesReceived = (statistics.getBytesReceived() == 0) ? "" : "" + statistics.getBytesReceived(); 
		
		switch (columnIndex) {
		case 0:
			return rule.toString();
		case 1:
			return "" + statistics.getCountTotal();
		case 2:
			return min;
		case 3:
			return max;
		case 4:
			return avg;	
		case 5:
			return bytesSent;
		case 6:
			return bytesReceived;
		case 7:
			return error;	
		default:
			throw new RuntimeException("Rule table viewer must have only 6 columns");
		}
	}

	public Color getBackground(Object element, int columnIndex) {
		
		return null;
	}

	public Color getForeground(Object element, int columnIndex) {
		
		return null;
	}

}
