/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication.session;

import com.floreysoft.jmte.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import jakarta.mail.*;
import jakarta.mail.Message.*;
import jakarta.mail.internet.*;
import org.slf4j.*;

import java.util.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * @explanation A <i>token provider</i> sending a randomly generated numeric token
 *              to the user via email.
 * @description <p>
 *              The <i>emailTokenProvider</i> randomly generates a 6-digit token after the user entered her correct
 *              password.
 *              </p>
 *              <p>
 *              The token is then sent to the user via email. The user's attribute <i>email</i> is used as the
 *              recipient email address. If this attribute has not been provided by the <i>user data provider</i>, the
 *              login attempt fails.
 *              </p>
 *              <p>
 *              The email is sent using the SMTP protocol via the <i>smtpHost</i>. Optionally, <i>ssl</i> and <i>smptPort</i> can
 *              be set to configure the type of connection. Optionally, <i>smtpUser</i> and <i>smtpPassword</i> can be used to
 *              use sender authentification.
 *              </p>
 *              <p>
 *              The email is assembled using <i>sender</i>, <i>recipient</i>, <i>subject</i> and <i>body</i>. All of these values
 *              may contain properties in the form of <tt>${propertyname}</tt>.
 *              </p>
 *              <p>
 *              The properties will be replaced by the corresponding user attributes set by the <i>user data provider</i>, or <tt>token</tt>
 *              will be replaced by the numeric token value.
 *              </p>
 */
@MCElement(name="emailTokenProvider", topLevel=false)
public class EmailTokenProvider extends NumericTokenProvider {

	private static final Logger log = LoggerFactory.getLogger(EmailTokenProvider.class.getName());

	private boolean simulate = false;

	private String recipient;
	private String body;
	private String sender;
	private String subject;

	private String smtpHost;
	private String smtpUser;
	private int smtpPort = 25;
	private String smtpPassword;

	private boolean ssl = true;

	@Override
	public void init(Router router) {
	}


	@Override
	public void requestToken(Map<String, String> userAttributes) {

		HashMap<String, Object> model = new HashMap<>(userAttributes);
		model.put("token", generateToken(userAttributes));

		Engine engine = new Engine();
		String recipient = engine.transform(this.recipient, model);
		String body = engine.transform(this.body, model);
		String sender = engine.transform(this.sender, model);
		String subject = engine.transform(this.subject, model);

		if (simulate)
			log.error("Send Email '" + subject + "' '" + body + "' from " + sender + " to " + recipient);
		else
			sendEmail(sender, recipient, subject, body);
	}


	private void sendEmail(String sender, String recipient, String subject, String text) {
		try {
			Properties props = System.getProperties();
			props.put("mail.smtp.host", smtpHost);
			props.put("mail.smtp.port", "" + smtpPort);
			props.put("mail.smtp.socketFactory.port", "" + smtpPort);
			if (ssl) {
				props.put("mail.smtp.starttls.enable", "true");
				props.put("mail.smtp.starttls.required", "true");
			}
			if (smtpUser != null) {
				props.put("mail.smtp.auth", "true");
			}

			Session session = Session.getInstance(props, new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(smtpUser, smtpPassword);
				}
			});

			final MimeMessage msg = getMimeMessage(sender, recipient, subject, text, session);

			Transport.send(msg, msg.getAllRecipients());
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
	}

	private static MimeMessage getMimeMessage(String sender, String recipient, String subject, String text, Session session) throws MessagingException {
		final MimeMessage msg = new MimeMessage(session);
		msg.setFrom(new InternetAddress(sender));
		msg.setRecipients(RecipientType.TO, InternetAddress.parse(recipient, false));
		msg.setSubject(subject);
		msg.setText(text, UTF_8.name());
		msg.setSentDate(new Date());
		return msg;
	}

	public boolean isSimulate() {
		return simulate;
	}

	/**
	 * @description don't actually send emails, if set to true
	 */
	@MCAttribute
	public void setSimulate(boolean simulate) {
		this.simulate = simulate;
	}

	public String getRecipient() {
		return recipient;
	}

	/**
	 * @description the recipient email address (templated)
	 */
	@Required
	@MCAttribute
	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}

	public String getBody() {
		return body;
	}

	/**
	 * @description the email body (templated)
	 */
	@Required
	@MCAttribute
	public void setBody(String body) {
		this.body = body;
	}

	public String getSender() {
		return sender;
	}

	/**
	 * @description the sender email address (templated)
	 */
	@Required
	@MCAttribute
	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getSubject() {
		return subject;
	}

	/**
	 * @description the email subject (templated)
	 */
	@Required
	@MCAttribute
	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getSmtpHost() {
		return smtpHost;
	}

	/**
	 * @description SMTP host
	 */
	@Required
	@MCAttribute
	public void setSmtpHost(String smtpHost) {
		this.smtpHost = smtpHost;
	}

	public String getSmtpUser() {
		return smtpUser;
	}

	/**
	 * @description SMTP user to use for sender authentication
	 * @default don't authenticate
	 */
	@MCAttribute
	public void setSmtpUser(String smtpUser) {
		this.smtpUser = smtpUser;
	}

	public int getSmtpPort() {
		return smtpPort;
	}

	/**
	 * @description the SMTP port to use
	 * @default 25
	 */
	@MCAttribute
	public void setSmtpPort(int smtpPort) {
		this.smtpPort = smtpPort;
	}

	public String getSmtpPassword() {
		return smtpPassword;
	}

	/**
	 * @description the SMTP password to use for sender authentication
	 */
	@MCAttribute
	public void setSmtpPassword(String smtpPassword) {
		this.smtpPassword = smtpPassword;
	}

	public boolean isSsl() {
		return ssl;
	}

	/**
	 * @description whether to use SMTP over SSL
	 * @default true
	 */
	@MCAttribute
	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

}
