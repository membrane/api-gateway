package com.predic8.membrane.core.interceptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;

public class SoapCacheInterceptor extends AbstractInterceptor {

	
	private String directoryName = "c:/temp/cache";
	
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		
		FileOutputStream ooo = new FileOutputStream(new File(directoryName + "/malkhaz.txt"));
		String hashCode = geMD5tHashCode(exc);
		
		ooo.write("HashCode: ".getBytes());
		ooo.write(hashCode.getBytes());
		ooo.write("\n".getBytes());
		ooo.write("File Names Found: ".getBytes());
		ooo.flush();
		
		String[] files = new File(directoryName).list();
		for (String fileName : files) {
			ooo.write(fileName.getBytes());
			ooo.flush();
			if (fileName.equals(hashCode + ".xml")) {
				Response response = new Response();
				
				byte[] cc = getFileContent(directoryName + "/" + fileName);
				response.setBodyContent(cc);
				response.setStatusCode(200);
				response.setStatusMessage("OK");
				response.getHeader().setContentType("text/xml;charset=UTF-8");
				response.getHeader().setConnection("close");
				exc.setResponse(response);
				return Outcome.ABORT;
			}
		}
		
		return super.handleRequest(exc);
	}


	private byte[] getFileContent(String fileName) throws FileNotFoundException, IOException {
		FileInputStream io = new FileInputStream(new File(fileName));
		byte[] content = new byte[io.available()];
		for (int i = 0; i < content.length; i ++) {
			content[i] = (byte)io.read();
		}
		
		return content;
	}
	
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		
		File file = new File(directoryName + "/" + geMD5tHashCode(exc) + ".xml");
		file.createNewFile();
		FileOutputStream out = new FileOutputStream(file);
				
		InputStream io = exc.getResponse().getBodyAsStream();
		
		try {
			int c = io.read();
			while (c >= 0) {
				out.write(c);
				c = io.read();
			}
			out.flush();
		} catch (Exception e) {
			throw e;
		} finally {
			out.close();
		}

		return Outcome.CONTINUE;
	}

	private String geMD5tHashCode(Exchange exc) throws IOException {
		byte[] defaultBytes = exc.getRequest().getBody().getContent();
		try{
			MessageDigest algorithm = MessageDigest.getInstance("MD5");
			algorithm.reset();
			algorithm.update(defaultBytes);
			byte messageDigest[] = algorithm.digest();
		            
			StringBuffer hexString = new StringBuffer();
			for (int i=0;i<messageDigest.length;i++) {
				hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
			}
			
			return hexString.toString();
		}catch(NoSuchAlgorithmException nsae){
		            
		}
		return "";
	}
	
}
