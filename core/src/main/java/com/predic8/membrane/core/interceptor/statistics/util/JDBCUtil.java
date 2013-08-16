/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.statistics.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.exchangestore.FileExchangeStore;

public class JDBCUtil {

	public static final String SEQUENCE_STATISTIC = "stat_seq"; 
	public static final String TRIGGER_STATISTIC = "stat_seq_trigger"; 
	
	public static final String TABLE_NAME = "statistic";

	public static final String ID = "id";
	
	public static final String STATUS_CODE = "status_code";
	
	public static final String TIME = "time";
	
	public static final String RULE = "rule";
	
	public static final String METHOD = "method";
	
	public static final String PATH = "path";
	
	public static final String CLIENT = "client";
	
	public static final String SERVER = "server";
	
	public static final String REQUEST_CONTENT_TYPE = "req_content_type";
	
	public static final String REQUEST_CONTENT_LENGTH = "req_content_length";
	
	public static final String RESPONSE_CONTENT_TYPE = "resp_content_type";
	
	public static final String RESPONSE_CONTENT_LENGTH = "resp_content_length";
	
	public static final String DURATION = "duration";
	
	public static final String MSG_FILE_PATH = "msgfilepath";
	
	public static String getCreateTableStatementForOracle() {
		return getCreateTableStatement("id INT PRIMARY KEY");
	}
	
	public static String getCreateTableStatementForMySQL() {
		return getCreateTableStatement("id INT NOT NULL AUTO_INCREMENT PRIMARY KEY");
	}
	
	public static String getCreateTableStatementForDerby() {
		return getCreateTableStatement("id INT GENERATED ALWAYS AS IDENTITY");
	}
	
	public static String getCreateTableStatementForOther() {
		return getCreateTableStatement("id BIGINT NOT NULL PRIMARY KEY");
	}
	
	public static String getCreateTableStatement(String idPart) {
		return "CREATE TABLE " + TABLE_NAME + " ( " + idPart + ", " +

		STATUS_CODE + " INT, " +

		TIME + " VARCHAR(155), " +

		RULE + " VARCHAR(255), " +

		METHOD + " VARCHAR(50), " +

		PATH + " VARCHAR(1000), " +

		CLIENT + " VARCHAR(255), " +

		SERVER + " VARCHAR(255), " +

		REQUEST_CONTENT_TYPE + " VARCHAR(100), " +

		REQUEST_CONTENT_LENGTH + " INT, " +

		RESPONSE_CONTENT_TYPE + " VARCHAR(100), " +

		RESPONSE_CONTENT_LENGTH + " INT, " +

		DURATION + " INT, " +

		MSG_FILE_PATH + " VARCHAR(255) " +
		
		")";
	}
	
	public static final String CREATE_SEQUENCE = "CREATE SEQUENCE " + SEQUENCE_STATISTIC;
	public static final String CREATE_TRIGGER = "CREATE TRIGGER " + TRIGGER_STATISTIC + " BEFORE INSERT ON " + TABLE_NAME + " FOR EACH ROW BEGIN IF (:new.id IS NULL) THEN SELECT " + SEQUENCE_STATISTIC + ".nextval INTO :new.id FROM DUAL; END IF; END; ";
	public static final String COUNT_ALL = "select count(*) from " +TABLE_NAME;
	
	public static String getPreparedInsertStatement(boolean idGenerated){
		
		return "INSERT INTO " + TABLE_NAME + " ( " + getPreparedInsertIntro(idGenerated) +

		STATUS_CODE + "," +

		TIME + "," +

		RULE + "," +

		METHOD + "," +

		PATH + "," +

		CLIENT + "," +

		SERVER + "," +

		REQUEST_CONTENT_TYPE + "," +

		REQUEST_CONTENT_LENGTH + "," +

		RESPONSE_CONTENT_TYPE + "," +

		RESPONSE_CONTENT_LENGTH + "," +

		DURATION + "," +

		MSG_FILE_PATH +
		
		") " +  getPreparedInsertProlog(idGenerated);
	}

	
	private static String getPreparedInsertIntro(boolean idGenerated) {
		if (idGenerated)
			return "";
		
		return ID + ",";
	}
	
	private static String getPreparedInsertProlog(boolean idGenerated) {
		String head = "VALUES(";
		String tail = "?,?,?,?,?,?,?,?,?,?,?,?,?)";
		if (idGenerated)
			return head + tail;
		
		return head + "?," + tail;
	}
	
	public static boolean isIdGenerated(DatabaseMetaData metaData) throws Exception {
		return isDerbyDatabase(metaData) || isMySQLDatabase(metaData) ||isOracleDatabase(metaData); 
	}
		
	public static void setData(AbstractExchange exc, PreparedStatement prepSt, boolean idGenerated) throws SQLException {
		int startIndex = 0;
		if (!idGenerated) {
			UUID id = UUID.randomUUID();
			prepSt.setLong(++ startIndex, id.getLeastSignificantBits());
		}
		prepSt.setInt(++ startIndex, exc.getResponse().getStatusCode());
		prepSt.setString(++ startIndex, ExchangesUtil.getTime(exc));
		prepSt.setString(++ startIndex, exc.getRule().toString());
		prepSt.setString(++ startIndex, exc.getRequest().getMethod());
		prepSt.setString(++ startIndex, exc.getRequest().getUri());
		prepSt.setString(++ startIndex, exc.getRemoteAddr());
		prepSt.setString(++ startIndex, exc.getServer());
		prepSt.setString(++ startIndex, exc.getRequestContentType());
		prepSt.setInt(++ startIndex, exc.getRequestContentLength());
		prepSt.setString(++ startIndex, exc.getResponseContentType());
		prepSt.setInt(++ startIndex, exc.getResponseContentLength());
		prepSt.setLong(++ startIndex, (exc.getTimeResReceived() - exc.getTimeReqSent()));
		
		prepSt.setString(++ startIndex, getFilePath(exc));
	}
	
	public static String getFilePath(AbstractExchange exc) {
		if (exc.getProperty(FileExchangeStore.MESSAGE_FILE_PATH) != null)
			return (String)exc.getProperty(FileExchangeStore.MESSAGE_FILE_PATH);
		
		return "";
	}

	public static boolean isOracleDatabase(DatabaseMetaData metaData) throws SQLException {
		return Pattern.matches(".*oracle.*", metaData.getDatabaseProductName().toLowerCase());
	}
	
	public static boolean isMySQLDatabase(DatabaseMetaData metaData) throws SQLException {
		return Pattern.matches(".*mysql.*", metaData.getDatabaseProductName().toLowerCase());
	}
	
	public static boolean isDerbyDatabase(DatabaseMetaData metaData) throws SQLException {
		return Pattern.matches(".*derby.*", metaData.getDatabaseProductName().toLowerCase());
	}
	
	public static boolean tableExists(Connection con, String table) throws SQLException {
		DatabaseMetaData meta = con.getMetaData();
		ResultSet rs = meta.getTables("", null, table, null);
		return rs.next();
	}
	
}
