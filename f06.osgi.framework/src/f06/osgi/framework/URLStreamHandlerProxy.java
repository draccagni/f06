/*
 * Copyright (c) Davide Raccagni (2006, 2009). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package f06.osgi.framework;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.service.url.URLStreamHandlerSetter;

class URLStreamHandlerProxy extends URLStreamHandler implements URLStreamHandlerSetter {

	private URLStreamHandlerService service;
	
	public URLStreamHandlerProxy(URLStreamHandlerService service) {
		this.service = service;
	}

	protected URLConnection openConnection(URL u) throws IOException {
		return service.openConnection(u);
	}

	public void setURL(URL u, String protocol, String host, int port, String authority, String userInfo, String path, String query, String ref) {
		super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
	}

	/**
	 * @see "java.net.URLStreamHandler.setURL(URL,String,String,int,String,String)"
	 * 
	 * @deprecated This method is only for compatibility with handlers written
	 *             for JDK 1.1.
	 */
	public void setURL(URL u, String protocol, String host, int port, String file, String ref) {
		super.setURL(u, protocol, host, port, null, null, file, null, ref);
	}

	protected boolean equals(URL u1, URL u2) {
		return service.equals(u1, u2);
	}

	protected synchronized InetAddress getHostAddress(URL u) {
		return service.getHostAddress(u);
	}

	protected int hashCode(URL u) {
		return service.hashCode(u);
	}

	protected boolean hostsEqual(URL u1, URL u2) {
		return service.hostsEqual(u1, u2);
	}

	protected void parseURL(URL u, String spec, int start, int limit) {
		service.parseURL(this, u, spec, start, limit);
	}

	protected boolean sameFile(URL u1, URL u2) {
		return service.sameFile(u1, u2);
	}

	protected String toExternalForm(URL u) {
		return service.toExternalForm(u);
	}
}
