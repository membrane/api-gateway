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

package com.predic8.plugin.membrane.providers;

import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.http.HeaderField;


public class HeaderTableLabelProvider implements ITableLabelProvider ,IColorProvider{

	
	public Image getColumnImage(Object element, int columnIndex) {
		
		return null;
	}

	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof HeaderField) {
			HeaderField headerField = (HeaderField) element;
			if (columnIndex == 0)
				return headerField.getHeaderName().toString();
			if (columnIndex == 1)
				return headerField.getValue();
		}
//		if (element instanceof DummyHeaderField) {
//			DummyHeaderField dummyHeaderField = (DummyHeaderField) element;
//			if (columnIndex == 0)
//				return dummyHeaderField.getDummyHeaderName();
//			if (columnIndex == 1)
//				return dummyHeaderField.getDummyValue();
//		}		
		return "";
	}

	public void addListener(ILabelProviderListener listener) {

	}

	public void dispose() {
	}

	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	public void removeListener(ILabelProviderListener listener) {
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorProvider#getForeground(java.lang.Object)
	 */
	public Color getForeground(Object element) {
//		if (element instanceof DummyHeaderField) 
//			return new Color(Display.getDefault(),128,128,128);
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorProvider#getBackground(java.lang.Object)
	 */
	public Color getBackground(Object element) {
		if (element instanceof HeaderField) 
			return new Color(Display.getDefault(),238,239,247);
		return null;
	}

}