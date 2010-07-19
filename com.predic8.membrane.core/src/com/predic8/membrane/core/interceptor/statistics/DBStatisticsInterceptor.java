package com.predic8.membrane.core.interceptor.statistics;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.statistics.util.DBTableConstants;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;

public class DBStatisticsInterceptor extends AbstractInterceptor {

	private String url;

	private String userName = "";

	private String password = "";

	private String driverClassName;
	
	private boolean driverLoaded;

	private DataSource dataSource;
	
	private PreparedStatement prepSt;
	
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		if (!driverLoaded) {
			return Outcome.CONTINUE;
		}

		if (dataSource == null)
			dataSource = getDataSource();
		
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
		ResultSet rs = meta.getTables("", JDBCUtil.getDatabaseName(url), DBTableConstants.TABLE_NAME, null);
		return rs.next();
	}

	public void setDriverClassName(String driverClassName) {
		try {
			Class.forName(driverClassName);
			driverLoaded = true;
			this.driverClassName = driverClassName;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPassword() {
		return password;
	}

	private DataSource getDataSource() {
		BasicDataSource ds = new BasicDataSource();
		ds.setDriverClassName(driverClassName);
		ds.setUsername(userName);
		ds.setPassword(password);
		ds.setUrl(url);
		return ds;
	}

}
