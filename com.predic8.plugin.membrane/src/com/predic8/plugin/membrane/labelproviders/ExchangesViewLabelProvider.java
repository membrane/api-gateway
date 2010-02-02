/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.plugin.membrane.labelproviders;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class ExchangesViewLabelProvider extends LabelProvider implements ITableLabelProvider {

	private static final NumberFormat FORMATTER = NumberFormat.getInstance();
	private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss");

	private Image imgPending;

	private Image imgFailed;

	private Image imgArrowUndo;

	private Image imgThumbDown;

	private Image imgBug;

	private Image imgCompleted;

	public ExchangesViewLabelProvider() {
		FORMATTER.setMaximumFractionDigits(3);
		createImages();
	}

	public Image getColumnImage(Object element, int columnIndex) {

		switch (columnIndex) {
		case 0:
			return selectImage(element);
		default:
			return null;
		}
	}

	private Image selectImage(Object element) {
		HttpExchange exc = (HttpExchange) element;
		switch (exc.getStatus()) {
		case STARTED:
			return imgPending;
		case FAILED:
			return imgFailed;
		case COMPLETED:
			if (exc.getResponse().isRedirect())
				return imgArrowUndo;
			
			if (exc.getResponse().isUserError())
				return imgThumbDown;
			
			if (exc.getResponse().isServerError())
				return imgBug;
			
			return imgCompleted;
			
		case SENT:
			return imgPending;

		case RECEIVED:
			return imgPending;

		default:
			throw new RuntimeException("Unknown status");
		}
	}

	public String getColumnText(Object element, int columnIndex) {

		HttpExchange exchange = (HttpExchange) element;

		switch (columnIndex) {

		case 0:
			if (exchange.getResponse() == null)
				return "";
			return "" + exchange.getResponse().getStatusCode();

		case 1:
			if (exchange.getTime() == null)
				return "unknown";
			return DATE_FORMATTER.format(exchange.getTime().getTime());

		case 2:
			return exchange.getRule().toString();

		case 3:
			return exchange.getRequest().getMethod();

		case 4:
			return exchange.getRequest().getUri(); // path

		case 5:
			return (String) exchange.getProperty(HttpTransport.SOURCE_HOSTNAME); // client

		case 6:
			return getServer(exchange);
		case 7:
			return getContentType(exchange);

		case 8:
			if (exchange.getRequest().getHeader().getContentLength() == -1)
				return "unknown";
			return "" + exchange.getRequest().getHeader().getContentLength();

		case 9:
			if (exchange.getResponse() == null || exchange.getResponse().getHeader().getContentType() == null)
				return "N/A";
			return "" + exchange.getResponse().getHeader().getContentType();

		case 10:
			if (exchange.getResponse() == null)
				return "";
			return "" + exchange.getResponse().getHeader().getContentLength();

		case 11:
			return "" + (exchange.getTimeResReceived() - exchange.getTimeReqSent());
		}
		
		return "";
	}

	private String getServer(HttpExchange exchange) {
		if (exchange.getRule() instanceof ProxyRule) {
			return exchange.getRequestUri();
		} else if (exchange.getRule() instanceof ForwardingRule) {
			return ((ForwardingRule) exchange.getRule()).getTargetHost();
		}
		return "";
	}

	private String getContentType(HttpExchange exchange) {
		String contentType = (String) exchange.getRequest().getHeader().getContentType();
		if (contentType == null)
			return "";
		int index = contentType.indexOf(";");
		if (index > 0) {
			return contentType.substring(0, index);
		}
		return contentType;
	}

	private void createImages() {
		imgPending = createImage(ImageKeys.IMAGE_PENDING);
		imgFailed = createImage(ImageKeys.IMAGE_FAILED);
		imgArrowUndo = createImage(ImageKeys.IMAGE_ARROW_UNDO);
		imgThumbDown = createImage(ImageKeys.IMAGE_THUMB_DOWN);
		imgBug = createImage(ImageKeys.IMAGE_BUG);
		imgCompleted = createImage(ImageKeys.IMAGE_COMPLETED);
	}

	private Image createImage(String imageId) {
		return MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(imageId).createImage();
	}
}
