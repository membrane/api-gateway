package com.predic8.plugin.membrane.views.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;

import com.predic8.membrane.core.http.Message;

public class MessageSaver {

	private String latestSavePath;
	
	private String getFileName(Message message) {
		FileDialog fd = new FileDialog(Display.getCurrent().getActiveShell(), SWT.SAVE);
		fd.setText("Save Message");
		if (latestSavePath != null && !latestSavePath.equals("")) {
			fd.setFilterPath(latestSavePath);
		} else {
			fd.setFilterPath("C:/");
		}

		fd.setFilterExtensions(new String[] { "*." + getExtension(message) });
		return fd.open();
	}

	private String getExtension(Message message) {
		if (message.getHeader().getContentType() == null)
			return "txt";

		if (message.isCSS()) {
			return "css";
		} else if (message.isHTML()) {
			return "html";
		} else if (message.isJavaScript()) {
			return "js";
		} else if (message.isXML()) {
			return "xml";
		} else if (message.isImage()) {
			String contentType = message.getHeader().getContentType();
			if (contentType.contains("jpeg")) {
				return "jpg";
			} else if (contentType.contains("gif")) {
				return "gif";
			} else if (contentType.contains("png")) {
				return "png";
			} else if (contentType.contains("bmp")) {
				return "bmp";
			} else {
				return "bmp";
			}
		}
		return "txt";
	}
	
	public void saveMessage(Message message) throws Exception {
		String selected = getFileName(message);
		if (selected == null || selected.equals(""))
			return;

		OutputStream os = null;
		try {
			File file = new File(selected);
			if (!file.exists()) {
				file.createNewFile();
			}

			latestSavePath = file.getParent();

			os = new FileOutputStream(file);
			if (message.isBodyEmpty()) {
				PrintWriter printer = new PrintWriter(os);
				printer.write(message.getHeader().toString());
				printer.flush();
			} else {
				os.write(message.getBody().getContent());
				os.flush();
			}
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					throw e;
				}
			}
		}

	}

	
}
