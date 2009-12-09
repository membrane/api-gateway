package com.predic8.plugin.membrane.providers;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class ExchangesViewLabelProvider extends LabelProvider implements
		ITableLabelProvider, ITableColorProvider {

	NumberFormat nf = NumberFormat.getInstance();
	SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
			"yyyy.MM.dd hh:mm:ss");

	public ExchangesViewLabelProvider() {
		nf.setMaximumFractionDigits(3);
	}

	public Image getColumnImage(Object element, int columnIndex) {
		try {
			if (element instanceof HttpExchange) {
				HttpExchange exchange = (HttpExchange) element;
				ImageDescriptor descriptor = null;
				switch (exchange.getStatus()) {
				case STARTED:
					descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_PENDING);
					break;
				case FAILED:
					descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FAILED);
					break;
				case COMPLETED:
					if (((Exchange) element).getResponse().isRedirect()) {
						descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_ARROW_UNDO);
					} else if (((Exchange) element).getResponse().getStatusCode() >= 400 && ((Exchange) element).getResponse().getStatusCode() < 500) {
						descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_THUMB_DOWN);
					} else if (((Exchange) element).getResponse().getStatusCode() > 500) {
						descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_BUG);
					} else {
						descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_COMPLETED);
					}
					break;

				case SENT:
					descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_PENDING);
					break;

				case RECEIVED:
					descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_PENDING);
					break;

				default:
					throw new RuntimeException("Unknown status");
				}
				
				switch (columnIndex) {
				case 0:
					return descriptor.createImage();
				default:
					break;
				}
				
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public String getColumnText(Object element, int columnIndex) {

		try {
			if (element instanceof HttpExchange) {

				HttpExchange exchange = (HttpExchange) element;

				switch (columnIndex) {

				case 0:
					if (exchange.getResponse() == null)
						return "";
					return "" + exchange.getResponse().getStatusCode();
				
				case 1:
					if (exchange.getTime() == null)
						return "unknown";
					return simpleDateFormat.format(exchange.getTime().getTime());

				case 2:
					return exchange.getRule().toString();

				case 3:
					return exchange.getRequest().getMethod();

				case 4:
					return exchange.getRequest().getUri();

				case 5:
					return (String)exchange.getProperty(Header.HOST);
					
				case 6:
					return exchange.getRequest().getHeader().getHost();

				case 7:
					String contentType = (String) exchange.getRequest().getHeader().getContentType();
					if (contentType == null)
						contentType = "";
					int index = contentType.indexOf(";");
					if (index > 0) {
						contentType = contentType.substring(0, index);
					}
					return contentType;
					
				case 8:
					return "" + exchange.getRequest().getHeader().getContentLength();

				case 9:
					if (exchange.getResponse() == null)
						return "";
					return "" + exchange.getResponse().getHeader().getContentLength();
					
				case 10:
					
					return "" + (exchange.getTimeResReceived() - exchange.getTimeReqSent());
					
				default:
					return "";

				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return "";
	}

	public Color getBackground(Object element, int columnIndex) {

		return null;
	}

	public Color getForeground(Object element, int columnIndex) {

		return null;
	}

}
