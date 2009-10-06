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

import org.osgi.framework.BundleContext;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.service.url.URLStreamHandlerSetter;

import sun.net.util.IPAddressUtil;

class BundleURLStreamHandlerService extends URLStreamHandler implements URLStreamHandlerService {
	
	public final static String BUNDLE_PROTOCOL = "bundle";

	private Framework framework;

	public BundleURLStreamHandlerService(BundleContext context) {
		this.framework = (Framework) context.getBundle();
	}

	public int getDefaultPort() {
		return 0;
	}

	public URLConnection openConnection(URL url) throws IOException {
		BundleURLConnection conn = new BundleURLConnection(framework, url);
		
		return conn;
	}

	/*
	 *  Example 
	 *  
	 *  		bundle://2.0.9.0:1/META-INF/MANIFEST.MF
	 */	
	public void parseURL(URLStreamHandlerSetter handler, URL u, String spec, int start, int limit) {
        // These fields may receive context content if this was relative URL
		String protocol = u.getProtocol();
		String authority = u.getAuthority();
		String userInfo = u.getUserInfo();
		String host = u.getHost();
		int port = u.getPort();
		String path = u.getPath();
		String query = u.getQuery();

		// This field has already been parsed
		String ref = u.getRef();

		boolean isRelPath = false;
		boolean queryOnly = false;

		// FIX: should not assume query if opaque
		// Strip off the query part
		if (start < limit) {
			int queryStart = spec.indexOf('?');
			queryOnly = queryStart == start;
			if ((queryStart != -1) && (queryStart < limit)) {
				query = spec.substring(queryStart + 1, limit);
				if (limit > queryStart)
					limit = queryStart;
				spec = spec.substring(0, queryStart);
			}
		}

		int i = 0;
		// Parse the authority part if any
		boolean isUNCName = (start <= limit - 4) && (spec.charAt(start) == '/')
				&& (spec.charAt(start + 1) == '/')
				&& (spec.charAt(start + 2) == '/')
				&& (spec.charAt(start + 3) == '/');
		if (!isUNCName && (start <= limit - 2) && (spec.charAt(start) == '/')
				&& (spec.charAt(start + 1) == '/')) {
			start += 2;
			i = spec.indexOf('/', start);
			if (i < 0) {
				i = spec.indexOf('?', start);
				if (i < 0)
					i = limit;
			}

			host = authority = spec.substring(start, i);

			int ind = authority.indexOf('@');
			if (ind != -1) {
				userInfo = authority.substring(0, ind);
				host = authority.substring(ind + 1);
			} else {
				userInfo = null;
			}
			if (host != null) {
				// If the host is surrounded by [ and ] then its an IPv6
				// literal address as specified in RFC2732
				if (host.length() > 0 && (host.charAt(0) == '[')) {
					if ((ind = host.indexOf(']')) > 2) {

						String nhost = host;
						host = nhost.substring(0, ind + 1);
						if (!IPAddressUtil.isIPv6LiteralAddress(host.substring(
								1, ind))) {
							throw new IllegalArgumentException("Invalid host: "
									+ host);
						}

						port = -1;
						if (nhost.length() > ind + 1) {
							if (nhost.charAt(ind + 1) == ':') {
								++ind;
								// port can be null according to RFC2396
								if (nhost.length() > (ind + 1)) {
									port = Integer.parseInt(nhost
											.substring(ind + 1));
								}
							} else {
								throw new IllegalArgumentException(
										"Invalid authority field: " + authority);
							}
						}
					} else {
						throw new IllegalArgumentException(
								"Invalid authority field: " + authority);
					}
				} else {
					ind = host.indexOf(':');
					port = -1;
					if (ind >= 0) {
						// port can be null according to RFC2396
						if (host.length() > (ind + 1)) {
							port = Integer.parseInt(host.substring(ind + 1));
						}
						host = host.substring(0, ind);
					}
				}
			} else {
				host = "";
			}
			if (port < -1)
				throw new IllegalArgumentException("Invalid port number :"
						+ port);
			start = i;
			// If the authority is defined then the path is defined by the
			// spec only; See RFC 2396 Section 5.2.4.
			if (authority != null && authority.length() > 0)
				path = "";
		}

		if (host == null) {
			host = "";
		}

		// Parse the file path if any
		if (start < limit) {
			if (spec.charAt(start) == '/') {
				path = spec.substring(start, limit);
			} else if (path != null && path.length() > 0) {
				isRelPath = true;
				int ind = path.lastIndexOf('/');
				String seperator = "";
				if (ind == -1 && authority != null)
					seperator = "/";
				path = path.substring(0, ind + 1) + seperator
						+ spec.substring(start, limit);

			} else {
				String seperator = (authority != null) ? "/" : "";
				path = seperator + spec.substring(start, limit);
			}
		} else if (queryOnly && path != null) {
			int ind = path.lastIndexOf('/');
			if (ind < 0)
				ind = 0;
			path = path.substring(0, ind) + "/";
		}
		if (path == null)
			path = "";

		if (isRelPath) {
			// Remove embedded /./
			while ((i = path.indexOf("/./")) >= 0) {
				path = path.substring(0, i) + path.substring(i + 2);
			}
			// Remove embedded /../ if possible
			i = 0;
			while ((i = path.indexOf("/../", i)) >= 0) {
				/*
				 * A "/../" will cancel the previous segment and itself, unless
				 * that segment is a "/../" itself i.e. "/a/b/../c" becomes
				 * "/a/c" but "/../../a" should stay unchanged
				 */
				if (i > 0 && (limit = path.lastIndexOf('/', i - 1)) >= 0
						&& (path.indexOf("/../", limit) != 0)) {
					path = path.substring(0, limit) + path.substring(i + 3);
					i = 0;
				} else {
					i = i + 3;
				}
			}
			// Remove trailing .. if possible
			while (path.endsWith("/..")) {
				i = path.indexOf("/..");
				if ((limit = path.lastIndexOf('/', i - 1)) >= 0) {
					path = path.substring(0, limit + 1);
				} else {
					break;
				}
			}
			// Remove starting .
			if (path.startsWith("./") && path.length() > 2)
				path = path.substring(2);

			// Remove trailing .
			if (path.endsWith("/."))
				path = path.substring(0, path.length() - 1);
		}

		handler.setURL(u, protocol, host, port, authority, userInfo, path,
				query, ref);
    }
	
	public String toExternalForm(URL u) {
		return super.toExternalForm(u);
	}
	
	public int hashCode(URL u) {
		return super.hashCode(u);
	}
	
	public boolean sameFile(URL u1, URL u2) {
		return super.sameFile(u1, u2);
	}
	
	public boolean equals(URL u1, URL u2) {
		return super.equals(u1, u2);
	}
	
	public synchronized InetAddress getHostAddress(URL u) {
		return super.getHostAddress(u);
	}
	
	public boolean hostsEqual(URL u1, URL u2) {
		return super.hostsEqual(u1, u2);
	}
	
	public static void main(String[] args) throws IOException {
		new URL("http://test:8090/path");
	}
}
