package com.predic8.plugin.membrane.providers;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;

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
	SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss");

	
	private Image imgPending;
	
	private Image imgFailed;
	
	private Image imgArrowUndo;
	
	private Image imgThumbDown;
	
	private Image imgBug;
	
	private Image imgCompleted;
	
	public ExchangesViewLabelProvider() {
		nf.setMaximumFractionDigits(3);
		imgPending = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_PENDING).createImage();
		imgFailed = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FAILED).createImage();
		imgArrowUndo = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_ARROW_UNDO).createImage();
		imgThumbDown = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_THUMB_DOWN).createImage();
		imgBug = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_BUG).createImage();
		imgCompleted =  MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_COMPLETED).createImage();
	}

	public Image getColumnImage(Object element, int columnIndex) {
		try {
			if (element instanceof HttpExchange) {
				HttpExchange exchange = (HttpExchange) element;
				Image result = null;
				switch (exchange.getStatus()) {
				case STARTED:
					result = imgPending;
					break;
				case FAILED:
					result = imgFailed;
					break;
				case COMPLETED:
					if (((Exchange) element).getResponse().isRedirect()) {
						result = imgArrowUndo;
					} else if (((Exchange) element).getResponse().getStatusCode() >= 400 && ((Exchange) element).getResponse().getStatusCode() < 500) {
						result = imgThumbDown;
					} else if (((Exchange) element).getResponse().getStatusCode() > 500) {
						result = imgBug;
					} else {
						result = imgCompleted;
					}
					break;

				case SENT:
					result = imgPending;
					break;

				case RECEIVED:
					result = imgPending;
					break;

				default:
					throw new RuntimeException("Unknown status");
				}
				
				switch (columnIndex) {
				case 0:
					return result;
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
