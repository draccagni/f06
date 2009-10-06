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
import java.util.Enumeration;

import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/*
 * BundleContent is defined for each bundle (host and fragment bundle) and permits
 * to access to jar file content
 */
class SystemBundleURLClassPath implements BundleURLClassPath {

	private Framework framework;
	
	private Version version;
	
	public SystemBundleURLClassPath(Framework framework) throws IOException {
		this.framework = framework;
		
		this.version = framework.getVersion();
	}

	public synchronized URL getEntry(String name) {
		return framework.getClass().getClassLoader().getResource(name);
	}
	
	public synchronized Enumeration findEntries(String path, final String filePattern, boolean recurse) {
		if (path == null)
			throw new NullPointerException();

		StringBuilder builder = new StringBuilder();

		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		builder.append(path);

		if (!path.endsWith("/") && path.length() > 0) {
			builder.append('/');
		}
		
		if (filePattern.indexOf('*') != -1) {
			return null;
			//throw new IllegalArgumentException("Cannot use wild cards with findEntries for System Bundle.");			
		}
		builder.append(filePattern);					

		/*
		 * JavaDoc  recurse If true, recurse into subdirectories. Otherwise only return entries 
		 * from the specified path.
		 */
		if (recurse) {
			throw new IllegalArgumentException("Cannot apply recursion to findEntries for System Bundle.");			
		}

		String ws = builder.toString();

		Enumeration e = null;
		try {
			e = framework.getClass().getClassLoader().getResources(ws);
			if (!e.hasMoreElements()) {
				return null;
			}
		} catch (IOException e1) {
		}
		
		return e;
	}
	
	public synchronized Enumeration getEntryPaths(String path) {
		return null;
	}
	
	public InputStream getEntryAsStream(int port, String name) throws IOException {
		return framework.getClass().getClassLoader().getResourceAsStream(name);
	}
	
	public Bundle getBundle() {
		return framework;
	}
	
	public Version getVersion() {
		return this.version;
	}
}
