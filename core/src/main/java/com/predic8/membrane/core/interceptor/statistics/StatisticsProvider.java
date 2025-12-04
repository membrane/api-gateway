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

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonFactory;
import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.statistics.util.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.context.*;

import javax.sql.*;
import java.io.*;
import java.sql.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name="statisticsProvider")
public class StatisticsProvider extends AbstractInterceptor implements ApplicationContextAware {
	private static final Logger log = LoggerFactory.getLogger(StatisticsProvider.class
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
		name = "provides caller statistics as json";
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
        Connection con;
        try {
            con = dataSource.getConnection();
        } catch (SQLException e) {
			internal(router.isProduction(),getDisplayName())
					.detail("Could not connect to database")
					.exception(e)
					.buildAndSetResponse(exc);
			return ABORT;
        }

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
		String order = "desc".equalsIgnoreCase(iOrder)?"desc":"asc";

		//injection protection
		String sort;
        sort = sortNameColmnMapping.getOrDefault(iSort, "id");

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

	private void createJson(Exchange exc, ResultSet r, int offset, int max, int total) throws SQLException {

		StringWriter jsonTxt = new StringWriter();

		try (JsonGenerator jsonGen = jsonFactory.createGenerator(jsonTxt)) {
			jsonGen.writeStartObject();

			jsonGen.writeName("statistics");
			jsonGen.writeStartArray();

			int size = 0;
			r.absolute(offset + 1); // jdbc doesn't support paginating. This can be inefficient.
			while (size < max && !r.isAfterLast()) {
				size++;
				writeRecord(r, jsonGen);
				r.next();
			}
			jsonGen.writeEndArray();

			jsonGen.writeName("total");
			jsonGen.writeNumber(total);

			jsonGen.writeEndObject();
		}

		createResponse(exc, jsonTxt);
	}

	private void writeRecord(ResultSet r, JsonGenerator jsonGen) throws SQLException {

		jsonGen.writeStartObject();

		jsonGen.writeName("statusCode");
		jsonGen.writeNumber(r.getInt(JDBCUtil.STATUS_CODE));

		jsonGen.writeName("time");
		jsonGen.writeString(r.getString(JDBCUtil.TIME));

		jsonGen.writeName("rule");
		jsonGen.writeString(r.getString(JDBCUtil.RULE));

		jsonGen.writeName("method");
		jsonGen.writeString(r.getString(JDBCUtil.METHOD));

		jsonGen.writeName("path");
		jsonGen.writeString(r.getString(JDBCUtil.PATH));

		jsonGen.writeName("client");
		jsonGen.writeString(r.getString(JDBCUtil.CLIENT));

		jsonGen.writeName("server");
		jsonGen.writeString(r.getString(JDBCUtil.SERVER));

		jsonGen.writeName("reqContentType");
		jsonGen.writeString(r.getString(JDBCUtil.REQUEST_CONTENT_TYPE));

		jsonGen.writeName("reqContentLenght");
		jsonGen.writeNumber(r.getInt(JDBCUtil.REQUEST_CONTENT_LENGTH));

		jsonGen.writeName("respContentType");
		jsonGen.writeString(r.getString(JDBCUtil.RESPONSE_CONTENT_TYPE));

		jsonGen.writeName("respContentLenght");
		jsonGen.writeNumber(r.getInt(JDBCUtil.RESPONSE_CONTENT_LENGTH));

		jsonGen.writeName("duration");
		jsonGen.writeNumber(r.getInt(JDBCUtil.DURATION));

		jsonGen.writeName("msgFilePath");
		jsonGen.writeString(r.getString(JDBCUtil.MSG_FILE_PATH));

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
	@Deprecated
	public void setDataSourceBeanId(String dataSourceBeanId) {
		this.dataSourceBeanId = dataSourceBeanId;
	}

	@Override
	public void init() {
		super.init();
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
