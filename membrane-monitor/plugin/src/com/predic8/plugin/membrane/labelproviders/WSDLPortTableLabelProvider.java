package com.predic8.plugin.membrane.labelproviders;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.graphics.Image;

import com.predic8.wsdl.Port;

public class WSDLPortTableLabelProvider extends LabelProvider implements ITableLabelProvider {


	public WSDLPortTableLabelProvider() {
		
	}
	
	@Override
	public Image getColumnImage(Object element, int columnIndex) {		
		return null;
	}

	@Override
	public String getColumnText(Object element, int columnIndex) {
		
		Port port = (Port)element;
		
		switch (columnIndex) {
		case 1:
			return port.getName();
			
		case 2:
			return (String)port.getBinding().getProtocol();
			
		case 3:
			return port.getAddress().getLocation();
			
		default:
			break;
		}
		
		return null;
	}

}
