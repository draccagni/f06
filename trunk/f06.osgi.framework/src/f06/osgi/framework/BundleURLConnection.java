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
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

public class BundleURLConnection extends URLConnection {
	
	private Framework framework;
	
	private InputStream is;
	
	public BundleURLConnection(Framework framework, URL url) {
		super(url);
		
		this.framework = framework;
	}
	
	public void connect() throws IOException {
		if (!connected) {
			String host = url.getHost();
			
			Bundle bundle;
			
			String version;
			
			int i = host.indexOf('.');
			if (i == -1) {
				long bundleId = Long.parseLong(host);
				bundle = framework.getBundle(bundleId);
				version = (String) bundle.getHeaders().get(Constants.BUNDLE_VERSION);
			} else {
				long bundleId = Long.parseLong(host.substring(0, i));
				bundle = framework.getBundle(bundleId);
				version = host.substring(i + 1);
			}
			
			BundleURLClassPath classPath = framework.getBundleURLClassPath(bundle, Version.parseVersion(version));
			
			int port = url.getPort();
			
			String path = url.getPath();
			
			is = classPath.getEntryAsStream(port, path.substring(1)); // + 1 : / 
		}
	}

	public InputStream getInputStream() throws IOException {
		if (!connected) {
			connect();
		}

		return is; 
	}
}
