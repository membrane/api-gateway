package com.predic8.plugin.membrane.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.LineStyleListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

public class JSONHighlitingStylelistener implements LineStyleListener {

	private StyleRangeComparator comparator = new StyleRangeComparator();
	
	private static final Pattern patternKey = Pattern
	.compile("(['\"].*?['\"])(?=(\\s*:))");
	
//	private static final Pattern patternValueString = Pattern
//	.compile("(?<=(:\\s{0,20}))(['\"].*?['\"])");
	
	private static final Pattern patternValueString2 = Pattern
	.compile("(?<=(:\\s{0,20}))((\".*?\")|('.*?'))");
	
	private static final Pattern patternValueNull = Pattern
	.compile("(?<=(:\\s{0,20}))(null)");
	
	private static final Pattern patternValueNumber = Pattern
	.compile("(?<=(:\\s{0,20}))([+-]?(\\d+(\\.)?(\\d+))|(\\d+))");
	
	private static final Color colorKey = new Color(Display
			.getCurrent(), 0, 0, 255);

	private static final Color colorValue = new Color(Display
			.getCurrent(), 0, 128, 0);
	
	private static final Color colorValueNull = Display.getCurrent().getSystemColor(SWT.COLOR_RED);
	
	public void lineGetStyle(LineStyleEvent event) {
		
		List<StyleRange> styles = new ArrayList<StyleRange>();
		
		Matcher mKey = patternKey.matcher(event.lineText);
		while (mKey.find()) {
			for (int i = 1; i <= mKey.groupCount(); i++) {
				styles.add(getStyle(event.lineOffset + mKey.start(i), mKey.end(i) - mKey.start(i) , colorKey));
			}
		}
		
		Matcher mValString = patternValueString2.matcher(event.lineText);
		while (mValString.find()) {
			for (int i = 1; i <= mValString.groupCount(); i++) {
				styles.add(getStyle(event.lineOffset + mValString.start(i) + 1, mValString.end(i) - mValString.start(i), colorValue));
			}
		}
		
		Matcher mValNull = patternValueNull.matcher(event.lineText);
		while (mValNull.find()) {
			for (int i = 1; i <= mValNull.groupCount(); i++) {
				styles.add(getStyle(event.lineOffset + mValNull.start(i) + 1, mValNull.end(i) - mValNull.start(i), colorValueNull));
			}
		}
		
		Matcher mValNumber= patternValueNumber.matcher(event.lineText);
		while (mValNumber.find()) {
			for (int i = 1; i <= mValNumber.groupCount(); i++) {
				styles.add(getStyle(event.lineOffset + mValNumber.start(i), mValNumber.end(i) - mValNumber.start(i), colorValue));
			}
		}

		Collections.sort(styles, comparator);
		event.styles = styles.toArray(new StyleRange[0]);
	}

	private static StyleRange getStyle(int start, int length, Color color) {
		return new StyleRange(start, length, color, null, SWT.NORMAL);
	}
	
}
