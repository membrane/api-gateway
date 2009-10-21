package com.predic8.membrane.core.transport;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.model.IMenuViewerListener;

public class Transport {

	protected Set<IMenuViewerListener> menuListeners = new HashSet<IMenuViewerListener>();
	
	private List<Interceptor> inInterceptors = new Vector<Interceptor>();

	private List<Interceptor> outInterceptors = new Vector<Interceptor>();
	
	public void addMenuViewerListener(IMenuViewerListener mViewer) {
		menuListeners.add(mViewer);
	
	}

	public void removeMenuViewerListener(IMenuViewerListener mViewer) {
		menuListeners.remove(mViewer);
	
	}

	public List<Interceptor> getInInterceptors() {
		return inInterceptors;
	}

	public void setInInterceptors(List<Interceptor> inInterceptors) {
		this.inInterceptors = inInterceptors;
	}

	public List<Interceptor> getOutInterceptors() {
		return outInterceptors;
	}

	public void setOutInterceptors(List<Interceptor> outInterceptors) {
		this.outInterceptors = outInterceptors;
	}

	

	
	
}
