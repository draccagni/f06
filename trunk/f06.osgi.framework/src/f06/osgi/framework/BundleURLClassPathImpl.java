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

	
	private Bundle bundle;
	
	private Version version;
	
	private String[] classPaths;

	private Map jarFilesByClassPath;
	
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
	}
	
	private Object find(Class clazz, int port, String name) throws IOException {

		Object o = null;
		if (port == -1) {
			for (int i = 0; i < this.classPaths.length; i++) {
				o = find(clazz, i + 1, name);
				if (o != null) {
					break;
				}
			}
		} else {
			String path0 = name;
			if (path0.startsWith("/")) {
				path0 = path0.substring(1);
			}
			
			String classPath = classPaths[port - 1];
			JarFile jarFile = (JarFile) jarFilesByClassPath.get(classPath);
			if (!classPath.endsWith(".jar") && !classPath.equals(".")) {
				if (!classPath.endsWith("/")) {
					classPath = new StringBuilder(classPath).append('/').toString();
				}
				name = new StringBuilder(classPath).append(name).toString();
			}
			String host = new StringBuilder(Long.toString(bundle.getBundleId())).append('.').append(version.toString()).toString();

			ZipEntry zipEntry = jarFile.getEntry(path0);
			if (zipEntry != null) {
				if (clazz == URL.class) {
					String path1 = name;
					if (!path1.startsWith("/")) {
						path1 = new StringBuilder("/").append(path1).toString();
					}
					 o = new URL(BundleURLStreamHandlerService.BUNDLE_PROTOCOL, host, port, path1, null);
				} else if (clazz == InputStream.class) {
					 o = jarFile.getInputStream(zipEntry);
				} else {
					throw new IllegalArgumentException(new StringBuilder("Cannot find instance of ").append(clazz.getName()).toString());
				}
			}
		}
		
		return o;
	}

	public synchronized URL getEntry(String name) {
		URL u = null;
		try {
			u = (URL) find(URL.class, -1, name);
		} catch (IOException e) {
			e.printStackTrace();
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
						try {
							URL url = (URL) find(URL.class, i + 1, ws);
							if (url != null) {
								c.add(url);
							}
						} catch (IOException e) {
							e.printStackTrace();
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
		InputStream is = (InputStream) find(InputStream.class, port, name);
		
		if (is != null) {
			is = new UnclosableInputStream(is);
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
