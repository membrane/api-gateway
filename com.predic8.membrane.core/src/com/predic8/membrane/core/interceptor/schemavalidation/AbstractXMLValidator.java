package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.schema.Schema;

public abstract class AbstractXMLValidator implements IValidator {
	private static Log log = LogFactory.getLog(AbstractXMLValidator.class.getName());

	private ArrayBlockingQueue<List<Validator>> validators;
	protected final String location;
	protected final Router router;

	public AbstractXMLValidator(String location, Router router) throws Exception {
		this.location = location;
		this.router = router;
		int concurrency = Runtime.getRuntime().availableProcessors() * 2;
		validators = new ArrayBlockingQueue<List<Validator>>(concurrency);
		for (int i = 0; i < concurrency; i++)
			validators.add(createValidators());
	}
	
	public Outcome validateMessage(Exchange exc, Message msg) throws Exception {
		List<Exception> exceptions = new ArrayList<Exception>();
		List<Validator> vals = validators.take();
		try {
			for (Validator validator: vals) {
				validator.validate(getMessageBody(msg.getBodyAsStream()));
				SchemaValidatorErrorHandler handler = (SchemaValidatorErrorHandler)validator.getErrorHandler();
				// the message must be valid for one schema embedded into WSDL 
				if (handler.noErrors()) {
					return Outcome.CONTINUE;
				}
				exceptions.add(handler.getException());
				handler.reset();
			}
		} finally {
			validators.put(vals);
		}
		exc.setResponse(createErrorResponse(getErrorMsg(exceptions)));
		return Outcome.ABORT;
	}
	
	protected List<Validator> createValidators() throws Exception {
		SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
		List<Validator> validators = new ArrayList<Validator>();
		for (Schema schema : getSchemas()) {
			log.info("Creating validator for schema: " + schema);
			StreamSource ss = new StreamSource(new StringReader(schema.getAsString()));
			ss.setSystemId(location);
			Validator validator = sf.newSchema(ss).newValidator();
			validator.setErrorHandler(new SchemaValidatorErrorHandler());
			validators.add(validator);
		}
		return validators;
	}

	private String getErrorMsg(List<Exception> excs) {
		StringBuffer buf = new StringBuffer();
		buf.append("Validation failed: ");
		for (Exception e : excs) {
			buf.append(e);
			buf.append("; ");
		}
		return buf.toString();
	}

	protected abstract List<Schema> getSchemas();
	protected abstract Source getMessageBody(InputStream input) throws Exception;
	protected abstract Response createErrorResponse(String message);

}
