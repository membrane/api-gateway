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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import jakarta.mail.internet.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.context.*;

import javax.sql.*;
import java.sql.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil.*;

/**
 * @description Writes statistics (time, status code, hostname, URI, etc.) about exchanges passing through into a
 * database (one row per exchange).
 * @topic 5. Monitoring, Logging and Statistics
 */
@MCElement(name="statisticsJDBC")
public class StatisticsJDBCInterceptor extends AbstractInterceptor implements ApplicationContextAware {
	private static final String DATASOURCE_BEAN_ID_ATTRIBUTE_CANNOT_BE_USED = "datasource bean id attribute cannot be used";

	private static final Logger log = LoggerFactory.getLogger(StatisticsJDBCInterceptor.class.getName());

	private ApplicationContext applicationContext;
	private DataSource dataSource;

	private boolean postMethodOnly;
	private boolean soapOnly;
	private boolean idGenerated;
	private String statString;
	private String dataSourceBeanId = DATASOURCE_BEAN_ID_ATTRIBUTE_CANNOT_BE_USED;
	boolean createTable = true;

	public StatisticsJDBCInterceptor() {
		name = "JDBC Logging";
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void init() {
		if (!Objects.equals(dataSourceBeanId, DATASOURCE_BEAN_ID_ATTRIBUTE_CANNOT_BE_USED))
			dataSource = applicationContext.getBean(dataSourceBeanId, DataSource.class);
		Connection con = null;
		try {
			con = dataSource.getConnection();
			idGenerated = isIdGenerated(con.getMetaData());
			statString = getPreparedInsertStatement(idGenerated);
			logDatabaseMetaData(con.getMetaData());
			if(createTable)
				createTableIfNecessary(con);
		} catch (Exception e) {
			throw new RuntimeException("Init for StatisticsJDBCInterceptor failed: " + e.getMessage());
		} finally {
			closeConnection(con);
		}
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
		try (Connection con = dataSource.getConnection()) {
			saveExchange(con, exc);
		} catch (Exception e) {
			log.warn("Could not save statistics: {}", e.getMessage());
		}
		return CONTINUE;
	}

	private void saveExchange(Connection con, Exchange exc) throws Exception {
		if ( ignoreGetMethod(exc) ) return;
		if ( ignoreNotSoap(exc) ) return;
		PreparedStatement stat = con.prepareStatement(statString);
		setData(exc, stat, idGenerated);
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
			   !ct.getBaseType().equalsIgnoreCase(APPLICATION_SOAP) &&
			   !ct.getBaseType().equalsIgnoreCase(TEXT_XML);
	}

	private boolean ignoreGetMethod(Exchange exc) {
		return postMethodOnly && !METHOD_POST.equals(exc.getRequest().getMethod());
	}

	private void createTableIfNecessary(Connection con) throws Exception {
		if (tableExists(con, TABLE_NAME))
			return;

		Statement st = con.createStatement();
		try {
			if (isOracleDatabase(con.getMetaData())) {
				st.execute(getCreateTableStatementForOracle());
				st.execute(CREATE_SEQUENCE);
				st.execute(CREATE_TRIGGER);
			} else if (isMySQLDatabase(con.getMetaData())) {
				st.execute(getCreateTableStatementForMySQL());
			} else if (isDerbyDatabase(con.getMetaData())) {
				st.execute(getCreateTableStatementForDerby());
			} else {
				st.execute(getCreateTableStatementForOther());
			}
		} finally {
			closeConnection(st);
		}
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @description The spring bean ID of a data source bean.
	 */
	@Required
	@MCAttribute
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		dataSourceBeanId = DATASOURCE_BEAN_ID_ATTRIBUTE_CANNOT_BE_USED;
	}

	public boolean isPostMethodOnly() {
		return postMethodOnly;
	}

	/**
	 * @default false
	 */
	@MCAttribute
	public void setPostMethodOnly(boolean postMethodOnly) {
		this.postMethodOnly = postMethodOnly;
	}

	public boolean isCreateTable() {
		return createTable;
	}

	/**
	 * @description toggle to try automatic table creation or disable table creation altogether
	 * @default true
	 */
	@MCAttribute
	public void setCreateTable(boolean createTable) {
		this.createTable = createTable;
	}

	public boolean isSoapOnly() {
		return soapOnly;
	}

	/**
	 * @default false
	 */
	@MCAttribute
	public void setSoapOnly(boolean soapOnly) {
		this.soapOnly = soapOnly;
	}

	public String getDataSourceBeanId() {
		return dataSourceBeanId;
	}

	/**
	 * @deprecated use {@link #setDataSource(DataSource)} instead: Using
	 *             setDataSourceBeanId(String) from Spring works,
	 *             but does not create a Spring bean dependency.
	 */
	@Deprecated
	public void setDataSourceBeanId(String dataSourceBeanId) {
		this.dataSourceBeanId = dataSourceBeanId;
	}

	private void logDatabaseMetaData(DatabaseMetaData metaData) throws Exception {
		log.debug("Database metadata:");
		log.debug("Name: {}",metaData.getDatabaseProductName());
		log.debug("Version: {}",metaData.getDatabaseProductVersion());
		log.debug("idGenerated: {}",idGenerated);
		log.debug("statString: {}",statString);
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
