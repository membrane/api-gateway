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

package com.predic8.plugin.membrane.components.composites.tabmanager;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.ByteUtil;

public class BodyTextTabComposite extends BodyTabComposite {

	protected StyledText bodyText;
	
	public BodyTextTabComposite(TabFolder parent, String tabTitle) {
		super(parent, tabTitle);
		bodyText = new StyledText(this, SWT.BORDER | SWT.BEGINNING | SWT.H_SCROLL | SWT.MULTI | SWT.V_SCROLL);

		bodyText.setFont(new Font(Display.getCurrent(), "Courier", 10, SWT.NORMAL));
		bodyText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				bodyModified = true;
				bodyText.redraw();
			}
		});
		
	}

	@Override
	public String getBodyText() {
		return bodyText.getText();
	}
	
	@Override
	public void setBodyText(String string) {
		if (string == null)
			return;
		bodyText.setText(string);
	}
	
	@Override
	public void setBodyTextEditable(boolean bool) {
		bodyText.setEditable(bool);
	}
	
	@Override
	public void updateInternal(Message msg) {		
		
		byte[] bodyContent;
		try {
			bodyContent = msg.getBody().getContent();
		} catch (IOException e1) {
			e1.printStackTrace();
			bodyContent = ("Could not get body content: " + e1.getMessage()).getBytes(); 
		}
		
		try {
			if (msg.isGzip()) {
				displayData(ByteUtil.getByteArrayData(new GZIPInputStream(msg.getBodyAsStream())));
			    return;
			} else if (msg.isDeflate()) {
				displayData(ByteUtil.getDecompressedData(bodyContent));
			    return;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		displayData(bodyContent);
	}

	private void displayData(byte[] content) {
		if (content == null) 
			return;
		if (isBeautifyBody()) {
			beautify(content);
		} else {
			setBodyText(new String(content));
		}
	}
	
	protected boolean isBeautifyBody() {
		return false;
	}
	
}
