package com.predic8.plugin.membrane.dialogs.rule.providers;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

import com.predic8.membrane.core.interceptor.Interceptor;

public class InterceptorTableViewerLabelProvider implements ITableLabelProvider {

	public String getColumnText(Object element, int columnIndex) {
		Interceptor obj = (Interceptor) element;
		switch (columnIndex) {
		case 0:
			return obj.getDisplayName();
		case 1:
			return obj.getClass().getName();
		default:
			throw new RuntimeException("Interceptor table must have only two columns");
		}
	}

	public Image getColumnImage(Object element, int columnIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	public void addListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub

	}

	public void dispose() {
		// TODO Auto-generated method stub

	}

	public boolean isLabelProperty(Object element, String property) {
		// TODO Auto-generated method stub
		return false;
	}

	public void removeListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub

	}

}
