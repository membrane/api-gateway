package com.predic8.membrane.core.transport;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.model.IMenuViewerListener;

public class Transport {

	protected Set<IMenuViewerListener> menuListeners = new HashSet<IMenuViewerListener>();
	
	private List<Interceptor> interceptors = new Vector<Interceptor>();

	public void addMenuViewerListener(IMenuViewerListener mViewer) {
		menuListeners.add(mViewer);
	
	}

	public void removeMenuViewerListener(IMenuViewerListener mViewer) {
		menuListeners.remove(mViewer);
	
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}
	
}
