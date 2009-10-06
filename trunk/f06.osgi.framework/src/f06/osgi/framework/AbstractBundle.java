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
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.PackageAdmin;

import f06.util.CaseSensitiveDictionary;

abstract class AbstractBundle implements Bundle {

	protected Framework framework;
	
	protected volatile int state;
	
	protected volatile boolean stale;

	final static int STOPPED          = 0xFFFFFFFF;

	final static int STARTED_DECLARED = 0x00000002;

	final static int STARTED_EAGER    = 0x00000000;

	public AbstractBundle(Framework framework) {
		this.framework = framework;
		
		this.state = INSTALLED;
	}
	
	public long getBundleId() {
		return framework.getBundleId(this);
	}

	/*
	 * 4.3.12
	 * 
	 * JavaDoc  Returns entries in this bundle and its attached fragments. This bundle's
	 * classloader is not used to search for entries. Only the contents of this bundle 
	 * and its attached fragments are searched for the specified entries.
	 */
	public Enumeration findEntries(final String path, final String filePattern, final boolean recurse) {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			try {
				securityManager.checkPermission(new AdminPermission(this, AdminPermission.RESOURCE));
			} catch (SecurityException e1) {
				return null;
			}			
		}

		/*
		 * If this bundle's state is INSTALLED, this method must attempt to resolve this bundle 
		 * before attempting to find entries.
		 */
		
		if (getState() == INSTALLED) {
			framework.resolveBundles(new Bundle[] { this });
		}
		
		BundleURLClassPath classPath = framework.getBundleURLClassPath(this);
		
		Enumeration e = classPath.findEntries(path, filePattern, recurse);
		
		/*
		 * JavaDoc  If this bundle is a fragment, then only matching entries in this fragment are returned.
		 */
	    if (framework.getBundleType(this) != PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
	    	Collection c = new ArrayList();
	    	if (e != null) {
		    	c.addAll(Collections.list(e));
	    	}
	    	
	    	Bundle[] fragments = framework.getFragments(this);
			if (fragments != null) {
				for (int i = 0; i < fragments.length; i++) {
					e = fragments[i].findEntries(path, filePattern, recurse);
					if (e != null) {
				    	c.addAll(Collections.list(e));
					}
				}
			}
			
			e = c.isEmpty() ? null : Collections.enumeration(c);
	    }
	    
	    return e;
	}
	
	/*
	 * 4.3.12  JAR File – The getEntry(String) and getEntryPaths(String) methods
	 * provide access to the resources in the bundle’s JAR file. No searching is
	 * involved, only the raw JAR file is taken into account. The purpose of
	 * these methods is to provide low-level access without requiring that the
	 * bundle is resolved.
	 */
	
	public URL getEntry(String name) {
		/*
		 * 6.1.4.9  Throws IllegalStateException – If this bundle has been uninstalled.
		 */
		if (getState() == Bundle.UNINSTALLED) {
			throw new IllegalStateException(new StringBuilder(this.toString()).append(" has been uninstalled.").toString());
		}
		
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.RESOURCE));			
		}

		if (name.startsWith("/")) {
			name = name.substring(1);
		}
		
		BundleURLClassPath classPath = framework.getBundleURLClassPath(this);
		
		return classPath.getEntry(name);
	}

	public Enumeration getEntryPaths(String path) {
		/*
		 * 6.1.4.10  Throws IllegalStateException – If this bundle has been uninstalled.
		 */
		if (getState() == Bundle.UNINSTALLED) {
			throw new IllegalStateException(new StringBuilder(this.toString()).append(" has been uninstalled.").toString());
		}
		
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.RESOURCE));
		}		

		/*
		 * JavaDoc  This bundle's classloader is not used to search for entries. Only the 
		 * contents of this bundle are searched. 
		 */
		BundleURLClassPath classPath = framework.getBundleURLClassPath(AbstractBundle.this);
		
		return classPath.getEntryPaths(path);
	}
	
	public Dictionary getHeaders() {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.METADATA));
		}		

		return framework.getHeaders(this);
	}
	
	public Dictionary getHeaders(String locale) {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.METADATA));
		}		

		Dictionary d = new CaseSensitiveDictionary(true);
		
		StringBuilder sb = new StringBuilder(Constants.BUNDLE_LOCALIZATION_DEFAULT_BASENAME);
		if (!locale.equals("")) {
			sb.append('_').append(locale);
		}
		
		sb.append(".properties");
		
		URL u = getEntry(sb.toString());
		
		Properties resources = new Properties();
		if (u != null) {
			try {
				resources.load(u.openStream());
			} catch (IOException e) {
				framework.log(LogService.LOG_ERROR, e.getMessage(), e);
			}
		}
		
		Dictionary headers = getHeaders();
		
		for (Enumeration e = headers.keys(); e.hasMoreElements();) {
			String key = (String) e.nextElement();
			
			String localeValue = (String) headers.get(key);
			
			int i;
			while ((i = localeValue.indexOf('%')) != -1) {
				i++;
				int j = i + 1;
				for (; j < localeValue.length(); j++) {
					if (";,= ".indexOf(localeValue.charAt(j)) != -1) {
						break;
					}
				}
				
				String localeEntry = localeValue.substring(i, j);
				
				String replacement = resources.getProperty(localeEntry);
				if (replacement != null) {
					localeValue = localeValue.replace(localeValue, replacement);
				}
			}
			
			d.put(new Attributes.Name(key), localeValue);
		}
		
		return d;
	}

	public long getLastModified() {
		return framework.getLastModified(this);
	}

	public String getLocation() {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.METADATA));
		}		

		return framework.getLocation(this);
	}

	public ServiceReference[] getRegisteredServices() {
		return framework.getRegisteredServices(this);
	}

	public abstract URL getResource(String name);

	public abstract Enumeration getResources(String name) throws IOException;

	public abstract ServiceReference[] getServicesInUse();

	public int getState() {
		return state;
	}
	
	void setState(int state) {
		this.state = state;
	}

	public String getSymbolicName() {
		String symbolicName = null;
		
		try {
			Dictionary headers = getHeaders();
			
			symbolicName = FrameworkUtil.getSymbolicName(headers);
		} catch (Exception e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
		
		return symbolicName;
	}

	public boolean hasPermission(Object permission) {
		if (permission instanceof Permission) {
			SecurityManager securityManager = System.getSecurityManager();
			try {
				if (securityManager != null) {
					securityManager.checkPermission((Permission) permission);
				}		
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		return false;
	}

	public abstract Class loadClass(String name) throws ClassNotFoundException;

	public void start() throws BundleException {
		start(0);
	}

	public abstract void start(int options) throws BundleException;

	public void stop() throws BundleException {
		stop(0);
	}

	public abstract void stop(int options) throws BundleException;
	
	public abstract void uninstall() throws BundleException;

	public void update() throws BundleException {
		try {
			/*
			 * JavaDoc The download location of the new version of this bundle
			 * is determined from either the bundle's BUNDLE_UPDATELOCATION
			 * Manifest header (if available) or the bundle's original location.
			 * 
			 * The location is interpreted in an implementation dependent
			 * manner, typically as a URL, and the new version of this bundle is
			 * obtained from this location.
			 */
			Dictionary headers = getHeaders();
			
			String updateLocation = (String) headers.get(Constants.BUNDLE_UPDATELOCATION);
			if (updateLocation == null) {
				updateLocation = getLocation();
			}

			InputStream is = new URL(updateLocation).openStream();

			update(is);
		} catch (IOException e) {
			throw new BundleException(e.getMessage());
		}
	}

	public abstract void update(InputStream in) throws BundleException;
	
	public abstract BundleContext getBundleContext();
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Bundle(id=");
		sb.append(getBundleId());
		sb.append(",name=");
		sb.append(getSymbolicName());
		
		sb.append(",version=");
		sb.append(getVersion());
		sb.append(')');
		
		return sb.toString();
	}
	
	public boolean equals(Object obj) {
		return obj == this;
	}
	
	boolean isStale0() {
		return stale;
	}
	
	void setStale0(boolean stale) {
		this.stale = stale;
	}
	
	public Version getVersion() {
		return Version.parseVersion((String) getHeaders().get(Constants.BUNDLE_VERSION));
	}

	public Map getSignerCertificates(int signersType) {
		// XXX not yet implemented
		return null;
	}
}