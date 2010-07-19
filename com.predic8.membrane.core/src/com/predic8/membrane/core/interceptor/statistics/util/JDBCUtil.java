package com.predic8.membrane.core.interceptor.statistics.util;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;


public class JDBCUtil {

	public static final String CREATE_TABLE_QUERY = "CREATE TABLE " + DBTableConstants.TABLE_NAME  +  " (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
	
		DBTableConstants.STATUS_CODE  + " INT, " +
		
		DBTableConstants.TIME  + " VARCHAR(155), " +

		DBTableConstants.RULE  + " VARCHAR(255), " +
	
		DBTableConstants.METHOD  + " VARCHAR(50), " +
	
		DBTableConstants.PATH  + " VARCHAR(1000), " +
		
		DBTableConstants.CLIENT  + " VARCHAR(255), " +
		
		DBTableConstants.SERVER  + " VARCHAR(255), " +
		
		DBTableConstants.REQUEST_CONTENT_TYPE + " VARCHAR(100), " +
		
		DBTableConstants.REQUEST_CONTENT_LENGTH + " INT, " +
		
		DBTableConstants.RESPONSE_CONTENT_TYPE + " VARCHAR(100), " +
		
		DBTableConstants.RESPONSE_CONTENT_LENGTH + " INT, " +
		
		DBTableConstants.DURATION + " LONG " +
		
	")";
	
	public static String getPreparedInsert() {
		return "INSERT INTO " + DBTableConstants.TABLE_NAME + " (" +
		
		DBTableConstants.STATUS_CODE + "," + 
		
		DBTableConstants.TIME + "," + 
		
		DBTableConstants.RULE + "," + 
		
		DBTableConstants.METHOD + "," + 
		
		DBTableConstants.PATH + "," + 
		
		DBTableConstants.CLIENT + "," + 
		
		DBTableConstants.SERVER + "," + 
		
		DBTableConstants.REQUEST_CONTENT_TYPE + "," + 
		
		DBTableConstants.REQUEST_CONTENT_LENGTH + "," + 
		
		DBTableConstants.RESPONSE_CONTENT_TYPE + "," + 
		
		DBTableConstants.RESPONSE_CONTENT_LENGTH + "," + 
		
		DBTableConstants.DURATION  + 
		
		")" + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";  
	}
	
	public static void readData(Exchange exc, PreparedStatement prepSt) throws SQLException {
		prepSt.setInt(1, exc.getResponse().getStatusCode());
		prepSt.setString(2, ExchangesUtil.getTime(exc));
		prepSt.setString(3, exc.getRule().toString());
		prepSt.setString(4, exc.getRequest().getMethod());
		prepSt.setString(5, exc.getRequest().getUri());
		prepSt.setString(6, exc.getSourceHostname());
		prepSt.setString(7, exc.getServer());
		prepSt.setString(8,exc.getRequestContentType());
		prepSt.setInt(9, exc.getRequestContentLength());
		prepSt.setString(10,exc.getResponseContentType());
		prepSt.setInt(11, exc.getResponseContentLength());
		prepSt.setLong(12, (exc.getTimeResReceived() - exc.getTimeReqSent()));
	}
	
	public static String getDatabaseName(String url) {
		int i = url.lastIndexOf("/");
		return url.substring(i + 1);
	}
}
