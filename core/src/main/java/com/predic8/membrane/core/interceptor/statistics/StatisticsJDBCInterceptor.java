/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;

import javax.mail.internet.ContentType;
import javax.mail.internet.ParseException;
import javax.sql.DataSource;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;

@MCInterceptor(name="statisticsJDBC", xsd="" +
		"					<xsd:sequence />\r\n" + 
		"					<xsd:attribute name=\"postMethodOnly\" type=\"xsd:boolean\" default=\"false\"/>\r\n" + 
		"					<xsd:attribute name=\"soapOnly\" type=\"xsd:boolean\" default=\"false\"/>\r\n" + 
		"					<xsd:attribute name=\"dataSource\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"")
public class StatisticsJDBCInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(StatisticsJDBCInterceptor.class.getName());
	
	private DataSource dataSource;
	
	private boolean postMethodOnly;
	private boolean soapOnly;
	private boolean idGenerated;
	private String statString;
	private String dataSourceBeanId; 
	
	public StatisticsJDBCInterceptor() {
		name = "JDBC Logging";
	}
	
	public void init() {
		dataSource = router.getBean(dataSourceBeanId, DataSource.class);
		Connection con = null;
		try {
			con = dataSource.getConnection();
			idGenerated = JDBCUtil.isIdGenerated(con.getMetaData());	
			statString = JDBCUtil.getPreparedInsertStatement(idGenerated);
			logDatabaseMetaData(con.getMetaData());
			createTableIfNecessary(con);
		} catch (Exception e) {
			throw new RuntimeException("Init for StatisticsJDBCInterceptor failed: " + e.getMessage());
		} finally {
			closeConnection(con);
		}
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		Connection con = null;
		try {
			con = dataSource.getConnection();
			saveExchange(con, exc);
		} catch (Exception e) {
			log.warn("Could not save statistics.", e);
		} finally {
			closeConnection(con);
		}
		return Outcome.CONTINUE;
	}

	private void saveExchange(Connection con, Exchange exc) throws Exception {
		if ( ignoreGetMethod(exc) ) return;
		if ( ignoreNotSoap(exc) ) return;
		PreparedStatement stat = con.prepareStatement(statString);
		JDBCUtil.setData(exc, stat, idGenerated);
		stat.executeUpdate();	
	}
	
	private boolean ignoreNotSoap(Exchange exc) {
		ContentType ct;
		try {
			ct = exc.getRequest().getHeader().getContentTypeObject();
		} catch (ParseException e) {
			return false;
		}
		return soapOnly &&
				ct != null &&
				!ct.getBaseType().equalsIgnoreCase(MimeType.APPLICATION_SOAP) &&
				!ct.getBaseType().equalsIgnoreCase(MimeType.TEXT_XML);
	}

	private boolean ignoreGetMethod(Exchange exc) {
		return postMethodOnly && !Request.METHOD_POST.equals(exc.getRequest().getMethod());
	}
	
	private void createTableIfNecessary(Connection con) throws Exception {
		if (JDBCUtil.tableExists(con, JDBCUtil.TABLE_NAME))
			return;

		Statement st = con.createStatement();
		try {
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
		} finally {
			closeConnection(st);
		}
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
		out.writeAttribute("dataSourceBeanId", dataSourceBeanId);
		
		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		postMethodOnly = Boolean.parseBoolean(token.getAttributeValue("", "postMethodOnly"));
		soapOnly = Boolean.parseBoolean(token.getAttributeValue("", "soapOnly"));
		dataSourceBeanId = token.getAttributeValue("", "dataSource");
	}
	
	private void logDatabaseMetaData(DatabaseMetaData metaData) throws Exception {
		log.debug("Database metadata:");
		log.debug("Name: "+metaData.getDatabaseProductName());
		log.debug("Version: "+metaData.getDatabaseProductVersion());
		log.debug("idGenerated: "+idGenerated);
		log.debug("statString: "+statString);
	}
	
	@Override
	public String getHelpId() {
		return "statistics-jdbc";
	}

	private void closeConnection(Connection con) {
		try {
			if (con != null ) con.close();
		} catch (Exception e) {
			log.warn("Could not close JDBC connection", e);
		}
	}
	
	private void closeConnection(Statement con) {
		try {
			if (con != null ) con.close();
		} catch (Exception e) {
			log.warn("Could not close Statement", e);
		}
	}
	
}
