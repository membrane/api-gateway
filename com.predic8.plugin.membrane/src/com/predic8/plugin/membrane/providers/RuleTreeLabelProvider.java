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

package com.predic8.plugin.membrane.providers;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class RuleTreeLabelProvider implements ILabelProvider {

	static Log log = LogFactory.getLog(RuleTreeLabelProvider.class.getName());

	private Map<ImageDescriptor, Image> imageCache = new HashMap<ImageDescriptor, Image>(4);

	public Image getImage(Object element) {
		ImageDescriptor descriptor = null;
		if (element instanceof Rule) {
			descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_RULE);
		} else if (element instanceof Exchange) {
			switch (((Exchange) element).getStatus()) {
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
		} else {
			throw new RuntimeException("Unknown type of element " + element.getClass().getName());
		}

		// obtain the cached image corresponding to the descriptor
		Image image = imageCache.get(descriptor);
		if (image == null) {
			image = descriptor.createImage();
			// Make it transparent.
			ImageData icondata = image.getImageData();
			icondata.transparentPixel = icondata.getPixel(0, icondata.height - 1);
			image = new Image(Display.getDefault(), icondata, icondata.getTransparencyMask());
			imageCache.put(descriptor, image);
		}
		return image;
	}

	public String getText(Object element) {
		if (element instanceof Rule) {
			return ((Rule) element).toString();
		}
		if (element instanceof Exchange) {
			Exchange exchange = (Exchange) element;

			return exchange.getRequest().getMethod() + " " + exchange.getRequest().getUri() + " " + exchange.getTime().get(Calendar.HOUR_OF_DAY) + ":" + exchange.getTime().get(Calendar.MINUTE) + ":" + exchange.getTime().get(Calendar.SECOND);

		}
		throw new RuntimeException("Unknown type of element " + element.getClass().getName());
	}

	public void addListener(ILabelProviderListener listener) {

	}

	public void dispose() {

	}

	public boolean isLabelProperty(Object element, String property) {

		return false;
	}

	public void removeListener(ILabelProviderListener listener) {

	}

}