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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.statistics.util.DBTableConstants;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;

public class StatisticsJDBCInterceptor extends AbstractInterceptor {

	private DataSource dataSource;
	
	private PreparedStatement prepSt;
	
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {

		Connection con = dataSource.getConnection();
		
		try {
			createTableIfNecessary(con);

			saveExchange(con, exc);

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			closeConnection(con);
		}

		return Outcome.CONTINUE;
	}

	private void saveExchange(Connection con, Exchange exc) throws Exception {
		prepSt = con.prepareStatement(JDBCUtil.getPreparedInsert());
		JDBCUtil.readData(exc, prepSt);
		prepSt.executeUpdate();	
	}

	private void closeConnection(Connection con) {
		try {
			con.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void createTableIfNecessary(Connection con) throws Exception {
		if (tableExists(con))
			return;

		Statement st = con.createStatement();
		st.execute(JDBCUtil.CREATE_TABLE_QUERY);
	}

	private boolean tableExists(Connection con) throws SQLException {
		DatabaseMetaData meta = con.getMetaData();
		ResultSet rs = meta.getTables("", null, DBTableConstants.TABLE_NAME, null);
		return rs.next();
	}

	public DataSource getDataSource() {
		return dataSource;
	}
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
}
