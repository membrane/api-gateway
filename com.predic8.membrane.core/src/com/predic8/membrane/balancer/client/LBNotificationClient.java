package com.predic8.membrane.balancer.client;

import java.io.FileInputStream;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.cli.*;
import org.apache.commons.codec.binary.*;
import org.apache.commons.logging.*;
//import org.apache.http.HttpResponse;
//import org.apache.http.client.methods.HttpGet;
//import org.apache.http.impl.client.DefaultHttpClient;

public class LBNotificationClient {

	private static Log log = LogFactory.getLog(LBNotificationClient.class
			.getName());

	private String propertiesFile = "client.properties";
	private String cmd;
	private String host;
	private String port;
	private String cmURL;
	private String cluster;
	private SecretKeySpec skeySpec;

	public static void main(String[] args) throws Exception {
		new LBNotificationClient().run(args);
	}

	public void run(String[] args) throws Exception {
		CommandLine cl = new BasicParser().parse(getOptions(), args, false);
		if (cl.hasOption('h') || args.length < 2) {
			printUsage();
			return;
		}
		parseArguments(cl);

		logArguments();

//		log.debug("URL: " + getRequestURL());
//		HttpResponse res = new DefaultHttpClient().execute(new HttpGet(
//				getRequestURL()));
//
//		if (getStatusCode(res) != 204) {
//			throw new Exception("Got StatusCode: " + getStatusCode(res));
//		}
//		log.info("Sent " +cmd + " message to "+host+":"+port + (skeySpec!=null?" encrypted":""));
	}

	private void parseArguments(CommandLine cl) throws Exception {
		cmd = getArgument(cl, 0, '-', null, null,
				"No command up, down or takeout specified!");
		host = getArgument(cl, 1, 'H', null, null, "No host name specified!");
		port = getArgument(cl, 2, 'p', null, "80", "");
		cluster = getArgument(cl, -1, 'c', null, "Default", "");
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

		if (prop != null) {
			Properties props = new Properties();
			props.load(new FileInputStream(propertiesFile));
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
		return "cluster=" + cluster + "&host=" + host + "&port=" + port
				+ "&time=" + time + "&nonce=" + new SecureRandom().nextLong();
	}

	private String getRequestURL() throws Exception {
		if (skeySpec!=null) {			
			return cmURL + "/" + cmd + "?data="+
			   URLEncoder.encode(getEncryptedQueryString(),"UTF-8");
		}
		String time = String.valueOf(System.currentTimeMillis()); 
		return cmURL + "/" + cmd + "?cluster=" + cluster + "&host=" + host + "&port=" + port + "&time=" + time;		 
	}
	
	private String getEncryptedQueryString() throws Exception {
		Cipher cipher = Cipher.getInstance("AES");

		cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
		//return Base64.encodeBase64String(cipher.doFinal(getQueryString().getBytes("UTF-8")));
		return "";
	}

//	private int getStatusCode(HttpResponse res) {
//		return res.getStatusLine().getStatusCode();
//	}

	private void logArguments() {
		log.debug("cmd: " + cmd);
		log.debug("host: " + host);
		log.debug("port: " + port);
		log.debug("cmURL: " + cmURL);
		log.debug("cluster: " + cluster);
	}

	private Options getOptions() {
		Options options = new Options();
		options.addOption("h", "help", false, "print usage.");
		options.addOption("c", "cluster", true,
				"Sets the cluster name for the operation. (Default:Default)");
		options.addOption("H", "host", true,
				"Sets the host name for the operation.");
		options.addOption("p", "port", true,
				"Sets the port for the operation. (Default:80)");
		options.addOption("c", "clusterManager", true,
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
