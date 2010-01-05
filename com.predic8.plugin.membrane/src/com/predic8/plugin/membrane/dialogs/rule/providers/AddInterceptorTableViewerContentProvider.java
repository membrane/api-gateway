package com.predic8.plugin.membrane.dialogs.rule.providers;

import java.util.Collection;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.predic8.membrane.core.interceptor.Interceptor;

public class AddInterceptorTableViewerContentProvider implements IStructuredContentProvider {

	public Object[] getElements(Object inputElement) {
		Collection<Interceptor> collection = (Collection<Interceptor>)inputElement;
		return collection.toArray();
	}

	public void dispose() {
		
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		
	}

}
