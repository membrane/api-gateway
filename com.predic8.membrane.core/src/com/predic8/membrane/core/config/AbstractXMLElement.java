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

package com.predic8.membrane.core.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Router;

public class AbstractXMLElement implements XMLElement {

	/**
	 * Needed to resolve interceptor IDs into interceptor beans
	 */
	protected Router router;
	 
	public XMLElement parse(XMLStreamReader token) throws XMLStreamException {
	    parseAttributes(token);
	    while(token.hasNext()) {
	      if(token.isStartElement()) {
	        parseChildren(token, token.getName().getLocalPart());
	      }
	      if (token.isCharacters()) {
	    	  parseCharacters(token);
	      }
	      if(token.isEndElement() && token.getName().getLocalPart() == getElementName())
	    	  break;
	      if(token.hasNext()) 
	    	  token.next();
	    }
	    return this;
	  }

	  protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		  
	  }

	  protected void parseCharacters(XMLStreamReader token) throws XMLStreamException {
		  
	  }
	  
	  protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		  
	  }

	  protected String getElementName(){
		  return null;
	  }
	  
	  /* (non-Javadoc)
	 * @see com.predic8.membrane.core.config.XMLElement#write(javax.xml.stream.XMLStreamWriter)
	 */
	public void write(XMLStreamWriter out) throws XMLStreamException {
		  
	 }
	
	public void setRouter(Router router) {
		this.router = router;
	}
	
}
