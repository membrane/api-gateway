/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.plugin.membrane.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.LineStyleListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/**
 * @author course
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class HighligtingLineStyleListner implements LineStyleListener {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.swt.custom.LineStyleListener#lineGetStyle(org.eclipse.swt.custom.LineStyleEvent)
	 */
	private static final Pattern patternElement = Pattern
			.compile("<(\\S+?)[\\s/>]");

	private static final Pattern patternAttribute = Pattern
			.compile("(\\S+?=['\"])(.*?)(['\"])");

	private static final Color colorElement = new Color(Display.getCurrent(),
			255, 0, 0);

	private static final Color colorAttributeName = new Color(Display
			.getCurrent(), 0, 0, 255);

	private static final Color colorAttributeValue = new Color(Display
			.getCurrent(), 0, 128, 0);

	@SuppressWarnings("unchecked")
	public void lineGetStyle(LineStyleEvent event) {
		List styles = new ArrayList();
		Matcher m = patternElement.matcher(event.lineText);

		while (m.find()) {
			styles.add(getElementStyle(event.lineOffset + m.start(1), m.end(1)
					- m.start(1)));
		}

		Matcher m1 = patternAttribute.matcher(event.lineText);

		while (m1.find()) {
			for (int i = 1; i <= m1.groupCount(); i++) {
				if (i == 2)
					styles.add(getAttributeValueStyle(event.lineOffset
							+ m1.start(i), m1.end(i) - m1.start(i)));
				else
					styles.add(getAttributeNameStyle(event.lineOffset
							+ m1.start(i), m1.end(i) - m1.start(i)));
			}
		}
		Collections.sort(styles, new Comparator(){
			public int compare(Object obj1, Object obj2) {
				int s1 = ((StyleRange) obj1).start;
				int s2 = ((StyleRange) obj2).start;
				if (s1 < s2)
					return -1;
				if (s1 == s2)
					return 0;
				return 1;

			}
		});
		event.styles = (StyleRange[]) styles.toArray(new StyleRange[0]);
	}

	private static StyleRange getAttributeValueStyle(int start, int length) {
		return new StyleRange(start, length, colorAttributeValue, null,
				SWT.NORMAL);
	}

	private static StyleRange getAttributeNameStyle(int start, int length) {
		return new StyleRange(start, length, colorAttributeName, null,
				SWT.NORMAL);
	}

	private static StyleRange getElementStyle(int start, int length) {
		return new StyleRange(start, length, colorElement, null, SWT.NORMAL);
	}
}