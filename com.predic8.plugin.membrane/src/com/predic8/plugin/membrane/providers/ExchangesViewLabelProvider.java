package com.predic8.plugin.membrane.providers;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Header;

public class ExchangesViewLabelProvider extends LabelProvider implements
		ITableLabelProvider, ITableColorProvider {

	NumberFormat nf = NumberFormat.getInstance();
	SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
			"yyyy.MM.dd hh:mm:ss");

	public ExchangesViewLabelProvider() {
		nf.setMaximumFractionDigits(3);
	}

	public Image getColumnImage(Object element, int columnIndex) {

		return null;
	}

	public String getColumnText(Object element, int columnIndex) {

		try {
			if (element instanceof HttpExchange) {

				HttpExchange exchange = (HttpExchange) element;

				switch (columnIndex) {

				case 0:
					if (exchange.getTime() == null)
						return "unknown";
					return simpleDateFormat
							.format(exchange.getTime().getTime());

				case 1:
					return exchange.getRule().toString();

				case 2:
					return exchange.getRequest().getMethod();

				case 3:
					return exchange.getRequest().getUri();

				case 4:
					return (String)exchange.getProperty(Header.HOST);
					
				case 5:
					return exchange.getRequest().getHeader().getHost();

				case 6:
					String contentType = (String) exchange.getRequest().getHeader().getContentType();
					if (contentType == null)
						contentType = "";
					int index = contentType.indexOf(";");
					if (index > 0) {
						contentType = contentType.substring(0, index);
					}
					return contentType;

				case 7:
					return "" + exchange.getResponse().getStatusCode();

				case 8:
					return "" + exchange.getRequest().getHeader().getContentLength();

				case 9:
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
