package com.predic8.plugin.membrane.listeners;

import java.util.Comparator;

import org.eclipse.swt.custom.StyleRange;

public class StyleRangeComparator implements Comparator<StyleRange> {

	public int compare(StyleRange r1, StyleRange r2) {
		if (r1.start < r2.start)
			return -1;
		if (r1.start == r2.start)
			return 0;
		return 1;
	}
	
}
