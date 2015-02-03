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

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;

@MCElement(name="statisticsProvider")
public class StatisticsProvider extends AbstractInterceptor implements ApplicationContextAware {
	private static Log log = LogFactory.getLog(StatisticsProvider.class
			.getName());

	private final JsonFactory jsonFactory = new JsonFactory(); // thread-safe
																// after
																// configuration
	private DataSource dataSource;
	private String dataSourceBeanId;
	private ApplicationContext applicationContext;

	private static final ImmutableMap<String, String> sortNameColmnMapping =
			   new ImmutableMap.Builder<String, String>()
			   	   .put("statusCode", JDBCUtil.STATUS_CODE)
			       .put("time", JDBCUtil.TIME)	
			       //TODO missing other colmns
			       .build();
			  

	public StatisticsProvider() {
		name = "Provides caller statistics as JSON";
	}
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Connection con = dataSource.getConnection();
		
		try {
			int offset = URLParamUtil.getIntParam(router.getUriFactory(), exc, "offset");
			int max = URLParamUtil.getIntParam(router.getUriFactory(), exc, "max");
			int total = getTotal(con);

			Statement s = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			ResultSet r = s.executeQuery(getOrderedStatistics(router.getUriFactory(), exc));
			createJson(exc, r, offset, max, total);
		} catch (Exception e) {
			log.warn("Could not retrieve statistics.", e);
			return Outcome.ABORT;
		} finally {
			closeConnection(con);
		}

		return Outcome.RETURN;
	}

	public static String getOrderedStatistics(URIFactory uriFactory, Exchange exc) throws Exception {
		String iOrder = URLParamUtil.getStringParam(uriFactory, exc, "order");
		String iSort = URLParamUtil.getStringParam(uriFactory, exc, "sort");
		
		//injection protection. Can't use prepared statements (in jdbc) when variables are within order by.
		String order = "desc".equals(iOrder.toLowerCase())?"desc":"asc";
		
		//injection protection
		String sort;
		if (!sortNameColmnMapping.containsKey(iSort)) {
			sort = "id";
		} else {
			sort = sortNameColmnMapping.get(iSort);
		}
		
		return	"select * from " +JDBCUtil.TABLE_NAME + " ORDER BY "+sort+" "+order;	
	}
			
	
	private int getTotal(Connection con) throws Exception {
		ResultSet r = con.createStatement().executeQuery(JDBCUtil.COUNT_ALL);
		r.next();
		return r.getInt(1);
	}

	private void createResponse(Exchange exc, StringWriter jsonTxt) {
		exc.setResponse(Response.ok()
				.body(jsonTxt.toString()).build());
	}

	private void createJson(Exchange exc, ResultSet r, int offset, int max, int total) throws IOException,
			JsonGenerationException, SQLException {

		StringWriter jsonTxt = new StringWriter();

		JsonGenerator jsonGen = jsonFactory.createJsonGenerator(jsonTxt);
		jsonGen.writeStartObject();
			jsonGen.writeArrayFieldStart("statistics");
				int size = 0;
				r.absolute(offset+1); //jdbc doesn't support paginating. This can be inefficient.
				while (size < max && !r.isAfterLast()) {
					size++;
					writeRecord(r, jsonGen);
					r.next();
				}		
			jsonGen.writeEndArray();
			jsonGen.writeNumberField("total", total);
		jsonGen.writeEndObject();
		jsonGen.flush();

		createResponse(exc, jsonTxt);
	}

	private void writeRecord(ResultSet r, JsonGenerator jsonGen)
			throws IOException, JsonGenerationException, SQLException {
		jsonGen.writeStartObject();
		jsonGen.writeNumberField("statusCode", r.getInt(JDBCUtil.STATUS_CODE));
		jsonGen.writeStringField("time", r.getString(JDBCUtil.TIME));
		jsonGen.writeStringField("rule", r.getString(JDBCUtil.RULE));
		jsonGen.writeStringField("method", r.getString(JDBCUtil.METHOD));
		jsonGen.writeStringField("path", r.getString(JDBCUtil.PATH));
		jsonGen.writeStringField("client", r.getString(JDBCUtil.CLIENT));
		jsonGen.writeStringField("server", r.getString(JDBCUtil.SERVER));
		jsonGen.writeStringField("reqContentType",
				r.getString(JDBCUtil.REQUEST_CONTENT_TYPE));
		jsonGen.writeNumberField("reqContentLenght",
				r.getInt(JDBCUtil.REQUEST_CONTENT_LENGTH));
		jsonGen.writeStringField("respContentType",
				r.getString(JDBCUtil.RESPONSE_CONTENT_TYPE));
		jsonGen.writeNumberField("respContentLenght",
				r.getInt(JDBCUtil.RESPONSE_CONTENT_LENGTH));
		jsonGen.writeNumberField("duration", r.getInt(JDBCUtil.DURATION));
		jsonGen.writeStringField("msgFilePath",
				r.getString(JDBCUtil.MSG_FILE_PATH));
		jsonGen.writeEndObject();
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	@Required
	@MCAttribute(attributeName="dataSourceBeanId")
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	public String getDataSourceBeanId() {
		return dataSourceBeanId;
	}
	
	/**
	 * @deprecated use {@link #setDataSource(DataSource)} instead: Using
	 *             {@link #setDataSourceBeanId(String)} from Spring works,
	 *             but does not create a Spring bean dependency.
	 */
	public void setDataSourceBeanId(String dataSourceBeanId) {
		this.dataSourceBeanId = dataSourceBeanId;
	}
	
	@Override
	public void init() throws Exception {
		if (dataSourceBeanId != null)
			dataSource = applicationContext.getBean(dataSourceBeanId, DataSource.class);
	}

	private void closeConnection(Connection con) {
		try {
			if (con != null)
				con.close();
		} catch (Exception e) {
			log.warn("Could not close JDBC connection", e);
		}
	}
}
