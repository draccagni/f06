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

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.ExportedPackage;

import f06.util.ManifestEntry;

class FragmentBundle extends AbstractBundle {
	
	public FragmentBundle(Framework framework) {
		super(framework);
    }    
	
	public URL getResource(String name) {
		/*
		 * 6.1.4.17  If this bundle is a fragment bundle then null is returned.
		 */
		
		return null;
	}

	public Enumeration getResources(String name) throws IOException {
		/*
		 * 6.1.4.17  If this bundle is a fragment bundle then null is returned.
		 */
		
		return null;
	}

	public Class loadClass(String name) throws ClassNotFoundException {
		
		/*
		 * 6.1.4.22  If the bundle is a fragment bundle then this method must throw a ClassNot-FoundException.
		 */
		
		throw new ClassNotFoundException("Class " + name + " not found.");
	}

	public void start(int options) throws BundleException {
		throw new BundleException("A fragment bundle cannot be started.");
	}

	public void stop(int options) throws BundleException {
		throw new BundleException("A fragment bundle cannot be stopped");
	}

	public void uninstall() throws BundleException {
		/* 
		 * JavaDoc  If this bundle's state is UNINSTALLED then an IllegalStateException
		 * is thrown.
		 */
		if (getState() == UNINSTALLED) {
			throw new IllegalStateException(new StringBuilder(this.toString()).append(" has been uninstalled.").toString());
		}
		
		Bundle host = null;
		
		if (getState() == RESOLVED) {
			host = framework.getHosts(this)[0];
		}
		
		/*
         * 4.8.1.1
         */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			AdminPermission adminPermission;
			
			if (framework.equals(host)) {
				adminPermission = new AdminPermission(framework, org.osgi.framework.AdminPermission.EXTENSIONLIFECYCLE);
			} else {
				adminPermission = new AdminPermission(this, AdminPermission.LIFECYCLE);
			}
			
			securityManager.checkPermission(adminPermission);
		}
		
		/*
		 * JavaDoc  This bundle's state is set to UNINSTALLED.
		 */
		setState(UNINSTALLED);

		/*
		 * 4.3.8  the Framework must remove any resources related to the bundle.
		 * 
		 * JavaDoc  This bundle and any persistent storage area provided for
		 * this bundle by the Framework are removed.
		 */
		framework.setRemovalPending(this);
		
		/* 
		 * JavaDoc  A bundle event of type BundleEvent.UNINSTALLED is fired.
		 */
		BundleEvent bundleEvent = new BundleEvent(BundleEvent.UNINSTALLED, this);
		framework.postBundleEvent(bundleEvent);
		
		boolean removable = true;
		
    	try {
    		if (host != null) {
				ManifestEntry[] entries = ManifestEntry.parse(getHeaders().get(Constants.EXPORT_PACKAGE));
				
				ExportedPackage[] exportedPackages = framework.getExportedPackages(host);
				if (exportedPackages != null) {
					for (int j = 0; j < exportedPackages.length; j++) {
						ExportedPackage exportedPackage = exportedPackages[j];
	
						for (int k = 0; k < entries.length; k++) {
							ManifestEntry entry = entries[k];
							String specificationVersion = null;
							if (entry.hasAttribute(Constants.PACKAGE_SPECIFICATION_VERSION)) {
								specificationVersion = entry.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION);
							}

							String versionAttribute = null;
							if (entry.hasAttribute(Constants.VERSION_ATTRIBUTE)) {
								versionAttribute = entry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
							}
							
							if (specificationVersion != null && versionAttribute != null && !specificationVersion.equals(versionAttribute)) {
								throw new Exception(new StringBuilder("Bundle(").append(host.getBundleId()).append(") exports a Package(name=").append(entry.getName()).append(") with specification-version attribute not equal to version attribute.").toString());
							}
							if (
									entry.getName().equals(exportedPackage.getName()) && 
									Version.parseVersion(versionAttribute).equals(exportedPackage.getVersion())
								) {
								if (exportedPackage.getImportingBundles() != null) {
									removable = false;
								}
								
								((ExportedPackageImpl) exportedPackage).setRemovalPending0(true);
								
								break;
							}
						}							
					}
				}
    		}
	
			if (removable) {
				framework.remove(this);
			}
		} catch (Exception e) {
			throw new BundleException(e.getMessage(), e);
		}
	}
		
	public void update(InputStream is) throws BundleException {
		BundleException bundleException = null;
        /*
         * 4.8.1.1
         */
		try {
			SecurityManager securityManager = System.getSecurityManager();
			ManifestEntry[] entries = ManifestEntry.parse(getHeaders().get(Constants.FRAGMENT_HOST));
			if (entries != null) {
			    String hostSymbolicName = entries[0].getName();
				if (hostSymbolicName.equals(Constants.SYSTEM_BUNDLE_SYMBOLICNAME)) {
					if (securityManager != null) {
						securityManager.checkPermission(new AdminPermission(this, org.osgi.framework.AdminPermission.EXTENSIONLIFECYCLE));
					}
				}
			} else {
				if (securityManager != null) {
					securityManager.checkPermission(new AdminPermission(this, AdminPermission.LIFECYCLE));
				}
			}
		} catch (Exception e1) {
			bundleException = new BundleException(e1.getMessage());
		}
		
		if (bundleException != null) {
			throw bundleException;
		}
		
		/* 
		 * JavaDoc  If this bundle's state is UNINSTALLED then an IllegalStateException
		 * is thrown.
		 */
		if (getState() == UNINSTALLED) {
			throw new IllegalStateException(new StringBuilder(this.toString()).append(" has been uninstalled.").toString());
		}		
				
		/* 
		 * JavaDoc  The new version of this bundle is installed.
		 */ 
		try {
			framework.update((Bundle) this, is);

			/* 
			 * JavaDoc  If the bundle has declared an Bundle-RequiredExecutionEnvironment
			 * header, then the listed execution environments must be verified against the installed
			 * execution environments. If they do not all match, the original version of this bundle
			 * must be restored and a BundleException must be thrown after completion of the remaining
			 * steps.
			 * 
			 * TODO
			 */			
		} catch (BundleException e) {
			/* 
			 * JavaDoc  If the Framework is unable to install the new version of this 
			 * bundle, the original version of this bundle must be restored and a BundleException
			 * must be thrown after completion of the remaining steps.
			 */

			bundleException = e;
		}
		
		/* 
		 * JavaDoc  This bundle's state is set to INSTALLED.
		 */
		setState(INSTALLED);
		
		String exportPackage = (String) getHeaders().get(Constants.EXPORT_PACKAGE);
		if (exportPackage != null) {
			try {
				ManifestEntry[] entries = ManifestEntry.parse(exportPackage);
				for (int i = 0; i < entries.length; i++) {
					ManifestEntry e = entries[i];
					ExportedPackage[] oldExportedPackages = framework.getExportedPackages(e.getName());
				    if (oldExportedPackages != null) {
					    for (int j = 0; j < oldExportedPackages.length; j++) {
					    	((ExportedPackageImpl) oldExportedPackages[j]).setRemovalPending0(true);
					    }
				    }
				}
			} catch (Exception e) {
				framework.log(LogService.LOG_ERROR, e.getMessage(), e);
			}
		}		
		
		/* 
		 * JavaDoc  If the new version of this bundle was successfully installed, a
		 * bundle event of type BundleEvent.UPDATED is fired.
		 */
		BundleEvent bundleEvent = new BundleEvent(BundleEvent.UPDATED, this);
		framework.postBundleEvent(bundleEvent);
		
		if (bundleException != null) {
			throw bundleException;
		}
	}
	
	public ServiceReference[] getServicesInUse() {
		return null;
	}

	public BundleContext getBundleContext() {
		return null;
	}
}
