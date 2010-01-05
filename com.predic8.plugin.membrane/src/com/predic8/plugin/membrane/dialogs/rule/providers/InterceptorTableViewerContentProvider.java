package com.predic8.plugin.membrane.dialogs.rule.providers;

import java.util.List;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.predic8.membrane.core.interceptor.Interceptor;

public class InterceptorTableViewerContentProvider implements IStructuredContentProvider {

	public Object[] getElements(Object inputElement) {
		List<Interceptor> list = (List<Interceptor>)inputElement;
		return list.toArray();
	}

	public void dispose() {
		
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		
	}

}
