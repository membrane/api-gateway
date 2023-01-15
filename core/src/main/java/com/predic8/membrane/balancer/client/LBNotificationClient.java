/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.balancer.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.*;
import java.security.SecureRandom;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.cli.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.balancer.Balancer;
import com.predic8.membrane.core.interceptor.balancer.Cluster;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.MessageUtil;

import static java.nio.charset.StandardCharsets.*;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

public class LBNotificationClient {

	private static Logger log = LoggerFactory.getLogger(LBNotificationClient.class.getName());

	private String propertiesFile = "client.properties";
	private String cmd;
	private String host;
	private String port;
	private String cmURL;
	private String balancer;
	private String cluster;
	private SecretKeySpec skeySpec;

	public static void main(String[] args) throws Exception {
		new LBNotificationClient().run(args);
	}

	public void run(String[] args) throws Exception {
		CommandLine cl = new DefaultParser().parse(getOptions(), args, false);
		if (cl.hasOption('h') || args.length < 2) {
			printUsage();
			return;
		}
		parseArguments(cl);

		logArguments();

		Response res = notifiyClusterManager();

		if (res.getStatusCode() != 204) {
			throw new Exception("Got StatusCode: " + res.getStatusCode());
		}

		log.info("Sent " +cmd + " message to "+host+":"+port + (skeySpec!=null?" encrypted":""));
	}

	private Response notifiyClusterManager() throws Exception {
		HttpClient client = new HttpClient();
		Exchange exc = new Exchange(null);
		Request r = MessageUtil.getPostRequest(getRequestURL());
		r.setBodyContent(new byte[0]);
		exc.setRequest(r);
		exc.getDestinations().add(getRequestURL());
		return client.call(exc).getResponse();
	}

	private void parseArguments(CommandLine cl) throws Exception {
		if (!new File(propertiesFile).exists()) log.warn("no properties file found at: "+ new File(propertiesFile).getAbsolutePath());

		cmd = getArgument(cl, 0, '-', null, null,
				"No command up, down or takeout specified!");
		host = getArgument(cl, 1, 'H', null, null, "No host name specified!");
		port = getArgument(cl, 2, 'p', null, "80", "");
		balancer = getArgument(cl, -1, 'b', null, Balancer.DEFAULT_NAME, "");
		cluster = getArgument(cl, -1, 'c', null, Cluster.DEFAULT_NAME, "");
		cmURL = getArgument(cl, -1, 'u', "clusterManager", null,
				"No cluster manager location found!");
		String key = getArgument(cl, -1, '-', "key", "", null);
		if (!"".equals(key)) {
			skeySpec = new SecretKeySpec(Hex.decodeHex(key.toCharArray()), "AES");
		}
	}

	private String getArgument(CommandLine cl, int clArgPos, char option,
			String prop, String def, String errMsg) throws Exception {
		if (clArgPos != -1 && cl.getArgs().length > clArgPos) {
			return cl.getArgs()[clArgPos];
		}

		if (option != '-' && cl.hasOption(option)) {
			return cl.getOptionValue(option);
		}

		if (prop != null && new File(propertiesFile).exists()) {
			Properties props = new Properties();
			try (InputStream is = new FileInputStream(propertiesFile)) {
				props.load(is);
			}
			if (props.containsKey(prop)) {
				return props.getProperty(prop);
			}
		}

		if (def != null) {
			return def;
		}

		throw new MissingArgumentException(errMsg);
	}

	private String getQueryString() {
		String time = String.valueOf(System.currentTimeMillis());
		return "balancer=" + balancer + "&cluster=" + cluster + "&host=" + host + "&port=" + port
				+ "&time=" + time + "&nonce=" + new SecureRandom().nextLong();
	}

	private String getRequestURL() throws Exception {
		if (skeySpec!=null) {
			return cmURL + "/" + cmd + "?data="+
					URLEncoder.encode(getEncryptedQueryString(), UTF_8);
		}
		String time = String.valueOf(System.currentTimeMillis());
		return cmURL + "/" + cmd + "?balancer=" + balancer + "&cluster=" + cluster + "&host=" + host + "&port=" + port + "&time=" + time;
	}

	private String getEncryptedQueryString() throws Exception {
		Cipher cipher = Cipher.getInstance("AES");

		cipher.init(ENCRYPT_MODE, skeySpec);
		return new String(encodeBase64(cipher.doFinal(getQueryString().getBytes(UTF_8))), UTF_8);
	}

	private void logArguments() {
		log.debug("cmd: " + cmd);
		log.debug("host: " + host);
		log.debug("port: " + port);
		log.debug("cmURL: " + cmURL);
		log.debug("cluster: " + cluster);
		log.debug("balancer: " + balancer);
	}

	private Options getOptions() {
		Options options = new Options();
		options.addOption("h", "help", false, "print usage.");
		options.addOption("b", "balancer", true,
				"Sets the balancer name for the operation. (Default:" + Balancer.DEFAULT_NAME + ")");
		options.addOption("c", "cluster", true,
				"Sets the cluster name for the operation. (Default:" + Cluster.DEFAULT_NAME + ")");
		options.addOption("H", "host", true,
				"Sets the host name for the operation.");
		options.addOption("p", "port", true,
				"Sets the port for the operation. (Default:80)");
		options.addOption("u", "clusterManager", true,
				"Sets the url of the cluster manager.");
		options.addOption("e", "useEncryption", false,
				"When set the parameters will be encrypted.");
		return options;
	}

	private void printUsage() {
		final String usage = "(up|down|takeout) (host|-h host) [port|-p port] [args] ";
		final String header = "Creats signed up and down messages for the load balancer push interface.";

		HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.setWidth(80);
		helpFormatter.printHelp(usage, header, getOptions(), null);
	}
}
