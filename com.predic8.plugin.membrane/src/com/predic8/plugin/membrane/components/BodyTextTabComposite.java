package com.predic8.plugin.membrane.components;

import java.io.InputStream;
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
import com.predic8.plugin.membrane.listeners.HighligtingLineStyleListner;

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
		bodyText.addLineStyleListener(new HighligtingLineStyleListner());
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
	public void update(Message msg) {
		if (msg == null)
			return;
		if (msg.getBody() == null)
			return;
		byte[] bodyContent = msg.getBody().getContent();
		if (bodyContent == null)
			return;
		
		
		try {
			if (msg.isGzip()) {
				InputStream stream = new GZIPInputStream(msg.getBodyAsStream());
				displayData(ByteUtil.getByteArrayData(stream));
			    return;
			} else if (msg.isDeflate()) {
				displayData(ByteUtil.getDecompressedData(bodyContent));
			    return;
			}
		} catch (Exception e) {
			setBodyText(new String(bodyContent));
			e.printStackTrace();
			return;
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
