package com.predic8.plugin.membrane.contentproviders;

import java.util.*;

import org.eclipse.jface.viewers.*;

import com.predic8.wsdl.*;

public class WSDLPortTableContentProvider implements IStructuredContentProvider {

	@Override
	public void dispose() {
		
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		
		
	}

	@Override
	public Object[] getElements(Object inputElement) {
		Definitions defs = (Definitions)inputElement;
		List<Port> ports = new ArrayList<Port>();
		List<Service> services = defs.getServices();
		for (Service service : services) {
			ports.addAll(service.getPorts());
		}
		return ports.toArray();
	}

}
