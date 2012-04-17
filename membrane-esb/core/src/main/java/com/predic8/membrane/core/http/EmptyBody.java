/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import java.io.IOException;

public class EmptyBody extends AbstractBody {

	@Override
	public int getLength() {
		return 0;
	}
	
	@Override
	public byte[] getContent() {
		return new byte[0];
	}

	@Override
	protected void readLocal() throws IOException {
		//ignore
	}

	@Override
	protected void writeAlreadyRead(AbstractBodyWriter out) throws IOException {
		//ignore
	}

	@Override
	protected void writeNotRead(AbstractBodyWriter out) throws IOException {
		//ignore
	}

	@Override
	protected byte[] getRawLocal() throws IOException {
		return new byte[0];
	}
	
	@Override
	public void write(AbstractBodyWriter out) throws IOException {
		
	}
	
	@Override
	public boolean isRead() {
		return true;
	}
}
