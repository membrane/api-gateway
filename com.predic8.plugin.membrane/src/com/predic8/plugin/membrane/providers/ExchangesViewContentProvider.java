package com.predic8.plugin.membrane.providers;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

public class ExchangesViewContentProvider implements IStructuredContentProvider {

	public Object[] getElements(Object inputElement) {
		if (inputElement == null)
			return new Object[0];
		
		return (Object[])inputElement;
	}

	public void dispose() {
		
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		
	}

}
