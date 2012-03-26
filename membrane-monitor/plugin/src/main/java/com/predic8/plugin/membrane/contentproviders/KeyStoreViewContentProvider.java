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

package com.predic8.plugin.membrane.contentproviders;

import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.predic8.plugin.membrane.preferences.KeyData;

public class KeyStoreViewContentProvider implements IStructuredContentProvider {

	private String password;
	
	public KeyStoreViewContentProvider(String password) {
		this.password = password;
	}
	
	public Object[] getElements(Object inputElement) {
		KeyStore keyStore = (KeyStore)inputElement;
		
		return getKeyDataList(keyStore);
	}

	public void dispose() {
		
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		
	}

	
	private Object[] getKeyDataList(KeyStore store) {
		
		List<Object> list = new ArrayList<Object>();
		
		try {
			Enumeration<String> aliases = store.aliases();
			
			while(aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				KeyData data = new KeyData(alias);
				
				if (store.isKeyEntry(alias)) {
					KeyStore.Entry entry = store.getEntry(alias, new KeyStore.PasswordProtection(password.toCharArray())); 
					
					if (entry instanceof KeyStore.PrivateKeyEntry) {
						KeyStore.PrivateKeyEntry key = (KeyStore.PrivateKeyEntry) entry;
						data.setKind("Private Key");
				        data.setAlgorithm(key.getPrivateKey().getAlgorithm());
				        Certificate cert = key.getCertificate();
						setIssuerAndSubject(data, cert);
						
					} 
				
				} else if (store.isCertificateEntry(alias)) {
					KeyStore.TrustedCertificateEntry entry = (KeyStore.TrustedCertificateEntry)store.getEntry(alias, null);
					Certificate cert = entry.getTrustedCertificate();
					setIssuerAndSubject(data, cert);
					PublicKey key = cert.getPublicKey();
					data.setKind("Public Key");
					data.setAlgorithm(key.getAlgorithm());
				}
				
				list.add(data);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return list.toArray();
	}

	private void setIssuerAndSubject(KeyData data, Certificate cert) {
		if (cert instanceof X509Certificate) {
			X509Certificate xCert = (X509Certificate)cert;
			data.setSubject(xCert.getSubjectDN().getName());
			data.setIssuer(xCert.getIssuerDN().getName());
			data.setSerialNumber(xCert.getSerialNumber().toString());
		}
	}
	
}
