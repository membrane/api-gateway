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

package com.predic8.membrane.core.multipart;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Writer;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.codec.binary.Base64;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Header;

public class Part {
	
	private Header header;
	private byte[] data;
	
	public Part(Header header, byte[] data) {
		this.header = header;
		this.data = data;
	}
	
	public String getContentID() {
		return header.getFirstValue("Content-ID");
	}
	
	public Header getHeader() {
		return header;
	}
	
	public InputStream getInputStream() {
		return new ByteArrayInputStream(data);
	}
	
	public XMLEvent asXMLEvent() {
		return new Characters() {
			
			@Override
			public void writeAsEncodedUnicode(Writer writer) throws XMLStreamException {
				throw new RuntimeException("not implemented");
			}
			
			@Override
			public boolean isStartElement() {
				return false;
			}
			
			@Override
			public boolean isStartDocument() {
				return false;
			}
			
			@Override
			public boolean isProcessingInstruction() {
				return false;
			}
			
			@Override
			public boolean isNamespace() {
				return false;
			}
			
			@Override
			public boolean isEntityReference() {
				return false;
			}
			
			@Override
			public boolean isEndElement() {
				return false;
			}
			
			@Override
			public boolean isEndDocument() {
				return false;
			}
			
			@Override
			public boolean isCharacters() {
				return true;
			}
			
			@Override
			public boolean isAttribute() {
				return false;
			}
			
			@Override
			public QName getSchemaType() {
				return null;
			}
			
			@Override
			public Location getLocation() {
				return null;
			}
			
			@Override
			public int getEventType() {
				return CHARACTERS;
			}
			
			@Override
			public StartElement asStartElement() {
				return null;
			}
			
			@Override
			public EndElement asEndElement() {
				return null;
			}
			
			@Override
			public Characters asCharacters() {
				return this;
			}

			@Override
			public String getData() {
				return new String(Base64.encodeBase64(data), Constants.UTF_8_CHARSET);
			}

			@Override
			public boolean isWhiteSpace() {
				return false;
			}

			@Override
			public boolean isCData() {
				return false;
			}

			@Override
			public boolean isIgnorableWhiteSpace() {
				return false;
			}
		};
	}

}
