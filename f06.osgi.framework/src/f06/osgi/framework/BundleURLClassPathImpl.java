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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import f06.util.TextUtil;

/*
 * BundleContent is defined for each bundle (host and fragment bundle) and permits
 * to access to jar file content
 */

class BundleURLClassPathImpl implements BundleURLClassPath {
	
	private class UnclosableInputStream extends InputStream {
		
		InputStream delegate;
		
		public UnclosableInputStream (InputStream delegate) throws IOException {
			this.delegate = delegate;
		}

		public void close () throws IOException {
			// XXX do nothing
		}
		
		public int read() throws IOException {
			return delegate.read();
		}
		
		public int available() throws IOException {
			return delegate.available();
		}
		
		public int hashCode() {
			return delegate.hashCode();
		}
		
		public synchronized void mark(int readlimit) {
			delegate.mark(readlimit);
		}
		
		public boolean markSupported() {
			return delegate.markSupported();
		}
		
		public int read(byte[] b) throws IOException {
			return delegate.read(b);
		}
		
		public int read(byte[] b, int off, int len) throws IOException {
			return delegate.read(b, off, len);
		}
		
		public synchronized void reset() throws IOException {
			delegate.reset();
		}
		
		public long skip(long n) throws IOException {
			return delegate.skip(n);
		}
	}
	
	private class BundleEntry {
		
		private int port;
		
		private ZipEntry zipEntry;
		
		public BundleEntry(int port, ZipEntry zipEntry) {
			this.port = port;
			
			this.zipEntry = zipEntry;
		}
		
		public URL getURL() {
			URL url = null;
			
			try {
				String name = this.zipEntry.getName();

				String host = new StringBuilder(Long.toString(bundle.getBundleId())).append('.').append(version.toString()).toString();
				
				url = new URL(BundleURLStreamHandlerService.BUNDLE_PROTOCOL, host, port, new StringBuilder("/").append(name).toString(), null);
			} catch (MalformedURLException e) {
				// XXX
			}

			return url;
		} 

		public InputStream getInputStream() throws IOException {
			JarFile jarFile = (JarFile) jarFilesByClassPath.get(classPaths[port - 1]);
			
			InputStream is = jarFile.getInputStream(zipEntry); 
			if (is == null) {
				throw new IOException(new StringBuilder("Unable to open stream to: ").append(getURL()).toString());
			}
			
			return new UnclosableInputStream(is);
		}
	}
	
	private Bundle bundle;
	
	private Version version;
	
	private String[] classPaths;

	private Map jarFilesByClassPath;
	
	// XXX really necessary?
	private Map entries;
	
	public BundleURLClassPathImpl(Bundle bundle, Version version, String[] classPaths, File cacheDir) throws IOException {
		this.bundle = bundle;
		
		this.version = version;
		
		this.classPaths = classPaths;
		
		this.jarFilesByClassPath = new HashMap();
		
		for (int i = 0; i < classPaths.length; i++) {
			File file;
			
			String classPath = classPaths[i];
			if (classPath.equals(".") || classPath.endsWith("/")) {
				file = new File(cacheDir, Storage.BUNDLE_FILE);
			} else {
				file = new File(cacheDir, classPath);
			}

			jarFilesByClassPath.put(classPath, new JarFile(file));
		}
		
		this.entries = new HashMap();
	}
	
	private BundleEntry getEntry(int port, String name) {

		BundleEntry entry = null;
		if (port == -1) {
			for (int i = 0; i < this.classPaths.length; i++) {
				entry = getEntry(i + 1, name);
				if (entry != null) {
					break;
				}
			}
		} else {
			if (name.startsWith("/")) {
				name = name.substring(1);
			}
			
			String key = new StringBuilder(Integer.toString(port)).append('/').append(name).toString();
			
			entry = (BundleEntry) entries.get(key);
			if (entry == null) {
				String classPath = classPaths[port - 1];
				JarFile jarFile = (JarFile) jarFilesByClassPath.get(classPath);
				if (!classPath.endsWith(".jar") && !classPath.equals(".")) {
					if (!classPath.endsWith("/")) {
						classPath = new StringBuilder(classPath).append('/').toString();
					}
					name = new StringBuilder(classPath).append(name).toString();
				}
				ZipEntry zipEntry = jarFile.getEntry(name);
				if (zipEntry != null) {
					entry = new BundleEntry(port, zipEntry);
	
					this.entries.put(key, entry);
				}
			}
		}
		
		return entry;
	}

	public synchronized URL getEntry(String name) {
		URL u = null;
		BundleEntry entry = getEntry(-1, name);
		if (entry != null) {
			u = entry.getURL();
		}
		
		return u;
	}

	public synchronized Enumeration findEntries(String path, final String filePattern, boolean recurse) {
		if (path == null) {
			throw new NullPointerException();
		}

		StringBuilder builder = new StringBuilder();

		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		builder.append(path);

		if (!path.endsWith("/") && path.length() > 0) {
			builder.append('/');
		}

		/*
		 * JavaDoc  recurse If true, recurse into subdirectories. Otherwise only return entries 
		 * from the specified path.
		 */
		if (recurse) {
			builder.append("*/");			
		}
		
		if (filePattern != null) {
			if (filePattern.startsWith("/")) {
				builder.append(filePattern.substring(1));
			} else {
				builder.append(filePattern);					
			}
		}

		String ws = builder.toString();
		
		String host = new StringBuilder(Long.toString(bundle.getBundleId())).append('.').append(version.toString()).toString();
		
		Collection c = new ArrayList();

		try {
			if (ws.indexOf('*') == -1) {
				// Apache Felix ModuleImpl.java
				//
				// Special case "/" so that it returns a root URLs for
				// each bundle class path entry...this isn't very
				// clean or meaningful, but the Spring guys want it.
				if (ws.equals("/")) {
					for (int i = 0; i < this.classPaths.length; i++) {
						c.add(new URL(BundleURLStreamHandlerService.BUNDLE_PROTOCOL, host, i + 1, "/", null));
					}
				} else {
					String path0 = ws;
					if (!path0.startsWith("/")) {
						path0 = new StringBuilder("/").append(path0).toString();
					}
					
					for (int i = 0; i < this.classPaths.length; i++) {
						BundleEntry entry = getEntry(i + 1, ws);
						if (entry != null) {
							c.add(new URL(BundleURLStreamHandlerService.BUNDLE_PROTOCOL, host, i + 1, path0, null));
						}
					}
				}
			} else {
				for (int i = 0; i < this.classPaths.length; i++) {
					JarFile jarFile = (JarFile) jarFilesByClassPath.get(classPaths[i]);
					Enumeration e = jarFile.entries();
					while (e.hasMoreElements()) {
						ZipEntry zipEntry = (ZipEntry) e.nextElement();
						String name = zipEntry.getName();
						if (TextUtil.wildcardCompare(ws, name) == 0) {
							c.add(new URL(BundleURLStreamHandlerService.BUNDLE_PROTOCOL, host, i + 1, new StringBuilder("/").append(name).toString(), null));
						}
					}
				}
			}
		} catch (MalformedURLException e) {
			// XXX
		}

		return c.isEmpty() ? null : Collections.enumeration(c);
	}

	public synchronized Enumeration getEntryPaths(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		
		Collection c = new ArrayList();

		for (int i = 0; i < this.classPaths.length; i++) {
			JarFile jarFile = (JarFile) jarFilesByClassPath.get(classPaths[i]);
			Enumeration e = jarFile.entries();
			while (e.hasMoreElements()) {
				ZipEntry zipEntry = (ZipEntry) e.nextElement();
				String name = zipEntry.getName();
				if (name.startsWith(path) && name.indexOf('/', path.length()) == -1) {
					c.add(name);
				}
			}
		}

		return c.isEmpty() ? null : Collections.enumeration(c);
	}

	public InputStream getEntryAsStream(int port, String name) throws IOException {
		InputStream is = null;
		BundleEntry entry = getEntry(port, name);
		if (entry != null) {
			is = entry.getInputStream();
		}
		
		return is;
	}
	
	public Bundle getBundle() {
		return this.bundle;
	}
	
	public Version getVersion() {
		return this.version;
	}
}
