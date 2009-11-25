package com.predic8.plugin.membrane.components;

import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.eclipse.swt.widgets.TabFolder;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.ByteUtil;

public class JSONTabComposite extends BodyTextTabComposite {

	public static final String TAB_TITLE = "JSON";
	
	public JSONTabComposite(TabFolder parent) {
		super(parent, TAB_TITLE);
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
		
		
		if (msg.isGzip()) {
			try {
				InputStream stream = new GZIPInputStream(msg.getBodyAsStream());
				byte[] decompressedData = ByteUtil.getByteArrayData(stream);
				setBodyText(new String(decompressedData));
			    return;
			} catch (Exception e) {
				setBodyText(new String(bodyContent));
				e.printStackTrace();
				return;
			}
		} else if (msg.isDeflate()) {
			try {
				byte[] decompressedData = ByteUtil.getDecompressedData(bodyContent);
				if (decompressedData != null) {
					setBodyText(new String(decompressedData));
				}
			    return;
			} catch (Exception e) {
				setBodyText(new String(bodyContent));
				e.printStackTrace();
				return;
			}
			
		}
		setBodyText(new String(bodyContent));
		
		
		setBodyText(new String(bodyContent));
	}
	
	
}
