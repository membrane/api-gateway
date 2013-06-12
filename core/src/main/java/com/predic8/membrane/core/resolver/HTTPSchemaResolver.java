/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.resolver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import com.google.common.collect.Lists;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.xml.util.ResourceDownloadException;

@MCElement(name="httpSchemaResolver")
public class HTTPSchemaResolver implements SchemaResolver {

	private HttpClientConfiguration httpClientConfig;
	
	private HttpClient httpClient;
	
	public synchronized HttpClient getHttpClient() {
		if (httpClient == null) {
			httpClient = new HttpClient(httpClientConfig);
		}
		return httpClient;
	}
	
	@Override
	public List<String> getSchemas() {
		return Lists.newArrayList("http", "https");
	}
	
	public InputStream resolve(String url) {
		try {
		    Exchange exc = new Request.Builder().method(Request.METHOD_GET).url(url).header(Header.USER_AGENT, Constants.PRODUCT_NAME + " " + Constants.VERSION).buildExchange();
		    Response response = getHttpClient().call(exc);
		    try {
		    	if(response.getStatusCode() != 200) {
		    		DownloadException rde = new DownloadException("could not get resource " + url + " by HTTP");
		    		rde.setStatus(response.getStatusCode());
		    		rde.setUrl(url);
		    		throw rde;
		    	}
		    	return new ByteArrayInputStream(ByteUtil.getByteArrayData(response.getBodyAsStreamDecoded()));
		    } finally {
		    	if (exc.getTargetConnection() != null)
		    		exc.getTargetConnection().close();
		    }
		} catch (ResourceDownloadException e) {
			throw e;
		} catch (Exception e) {
			DownloadException rde = new DownloadException(e);
			rde.setUrl(url);
			throw rde;
		}
	}

	@Override
	public List<String> getChildren(String url) {
		return null;
	}
	
	@Override
	public long getTimestamp(String url) {
		return 0;
	}
	
	public HttpClientConfiguration getHttpClientConfig() {
		return httpClientConfig;
	}
	
	public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
		this.httpClientConfig = httpClientConfig;
	}
}
