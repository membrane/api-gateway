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
package com.predic8.membrane.core.interceptor.statistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import javax.sql.DataSource;
import javax.xml.stream.*;

import org.apache.commons.dbcp.BasicDataSource;

import com.predic8.membrane.core.config.GenericConfigElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;

public class StatisticsJDBCInterceptor extends AbstractInterceptor {

	private DataSource dataSource;
	
	private PreparedStatement stat;
	
	private boolean postMethodOnly = false;
	
	private boolean soapOnly = false;
	
	private boolean idGenerated;
	
	private String statString;
	
	private Connection con;

	private String dataSourceBeanId; 
	
	public StatisticsJDBCInterceptor() {
		priority = 500;
	}
	
	public void init() {
		Connection con = null;
		try {
			con = dataSource.getConnection();
			idGenerated = JDBCUtil.isIdGenerated(con.getMetaData());
			statString = JDBCUtil.getPreparedInsertStatement(idGenerated);
			createTableIfNecessary(con);
		} catch (Exception e) {
			throw new RuntimeException("Init for StatisticsJDBCInterceptor failed: " + e.getMessage());
		} finally {
			closeConnection(con);
		}
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {

		if (con == null || con.isClosed())
			con = dataSource.getConnection();
		
		try {
			createPreparedStatement(con);
			saveExchange(con, exc);
		} catch (Exception e) {
			e.printStackTrace();
			con.close();
		}
		return Outcome.CONTINUE;
	}

	private void saveExchange(Connection con, Exchange exc) throws Exception {
		if ( ignoreGetMethod(exc) ) return;
		if ( ignoreNotSoap(exc) ) return;
		JDBCUtil.setData(exc, stat, idGenerated);
		stat.executeUpdate();	
	}

	private boolean ignoreNotSoap(Exchange exc) {
		return soapOnly && 
			 !MimeType.APPLICATION_SOAP.equals(exc.getRequestContentType()) &&
			 !MimeType.TEXT_XML.equals(exc.getRequestContentType());
	}

	private boolean ignoreGetMethod(Exchange exc) {
		return postMethodOnly && !Request.METHOD_POST.equals(exc.getRequest().getMethod());
	}
	
	private void closeConnection(Connection con) {
		try {
			con.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void createTableIfNecessary(Connection con) throws Exception {
		if (JDBCUtil.tableExists(con, JDBCUtil.TABLE_NAME))
			return;

		Statement st = con.createStatement();
		
		if (JDBCUtil.isOracleDatabase(con.getMetaData())) {
			st.execute(JDBCUtil.getCreateTableStatementForOracle());
			st.execute(JDBCUtil.CREATE_SEQUENCE);
			st.execute(JDBCUtil.CREATE_TRIGGER);
		} else if (JDBCUtil.isMySQLDatabase(con.getMetaData())) {
			st.execute(JDBCUtil.getCreateTableStatementForMySQL());
		} else if (JDBCUtil.isDerbyDatabase(con.getMetaData())) {
			st.execute(JDBCUtil.getCreateTableStatementForDerby());
		} else {
			st.execute(JDBCUtil.getCreateTableStatementForOther());
		}
	}

	private void createPreparedStatement(Connection con) throws Exception {
		if (stat == null || stat.getConnection().isClosed() )
			stat = con.prepareStatement(statString);
	}
	
	public DataSource getDataSource() {
		return dataSource;
	}
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public boolean isPostMethodOnly() {
		return postMethodOnly;
	}

	public void setPostMethodOnly(boolean postMethodOnly) {
		this.postMethodOnly = postMethodOnly;
	}
	
	public boolean isSoapOnly() {
		return soapOnly;
	}

	public void setSoapOnly(boolean soapOnly) {
		this.soapOnly = soapOnly;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("statisticsJDBC");
		out.writeAttribute("postMethodOnly", ""+postMethodOnly);
		out.writeAttribute("soapOnly", ""+soapOnly);

		out.writeStartElement("dataSource");
		BasicDataSource ds = (BasicDataSource)dataSource;
		out.writeAttribute("driverClassName", ds.getDriverClassName());
		out.writeAttribute("url", ds.getUrl());
		out.writeAttribute("user", ds.getUsername());
		out.writeAttribute("password", ds.getPassword());
		out.writeEndElement();
		
		out.writeEndElement();
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if ( "dataSource".equals(child) ) {
			GenericConfigElement ce = new GenericConfigElement(); 
			ce.parse(token);
			BasicDataSource ds = new BasicDataSource();
			
			ds.setDriverClassName(ce.getAttribute("driverClassName"));
			ds.setUrl(ce.getAttribute("url"));
			ds.setUsername(ce.getAttribute("user"));
			ds.setPassword(ce.getAttribute("password"));
			dataSource = ds;
			init();
			return;
		}
			
		super.parseChildren(token, child);
	}
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		
		try {
			postMethodOnly = Boolean.parseBoolean(token.getAttributeValue("", "postMethodOnly"));
			soapOnly = Boolean.parseBoolean(token.getAttributeValue("", "soapOnly"));
			dataSourceBeanId = token.getAttributeValue("", "dataSource");
			dataSource = router.getBean(dataSourceBeanId, DataSource.class);
			init();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}	
}
