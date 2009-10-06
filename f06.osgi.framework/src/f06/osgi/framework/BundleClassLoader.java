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
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;

import f06.util.ArrayUtil;
import f06.util.IOUtil;
import f06.util.ManifestEntry;
import f06.util.TextUtil;

class BundleClassLoader extends SecureClassLoader {

	protected Framework framework;
	protected Bundle host;
	protected BundleURLClassPath[] classPaths;
	protected boolean isActivationTriggered;
	
	protected final static Enumeration EMPTY_ENUMERATION = new Enumeration() {
		public boolean hasMoreElements() {
			return false;
		}
		
		public Object nextElement() {
			throw new NoSuchElementException();
		}
	};
	
	public BundleClassLoader(ClassLoader parent, Framework framework, Bundle host, BundleURLClassPath[] classPaths) {
		super(parent);

		this.framework = framework;
		this.host = host;
		this.classPaths = classPaths;
	}
	
	public Bundle getBundle() {
		return host;
	}

	// Waiting for Generics
	private Object find0(String name, Class tClazz) throws Exception {
		String pkgName = tClazz == Class.class ? FrameworkUtil.getClassPackage(name) : FrameworkUtil.getResourcePackage(name);
		
		/*
		 * 3.8.4  1. If the class or resource is in a java.* package, the request is
		 * delegated to the parent class loader; otherwise, the search continues with the next
		 * step. If the request is delegated to the parent class loader and the class or
		 * resource is not found, then the search terminates and the request fails.
		 */
		if (pkgName.startsWith("java.")) {
			if (tClazz == Class.class) {
				return getParent().loadClass(name);
			} else if (tClazz == URL.class) {
				return getParent().getResource(name);
			} else if (tClazz == Enumeration.class) {
				return getParent().getResources(name);
			} 
		}
		
		/*
		 * 3.8.4  2. If the class or resource is from a package included in the boot delegation
		 * list (org.osgi.framework.bootdelegation), then the request is delegated
		 * to the parent class loader. If the class or resource is found there, the
		 * search ends.
		 */
		if (framework.isBootDelegated(pkgName)) {
			if (tClazz == Class.class) {
				try {
					return getParent().loadClass(name);
				} catch (Exception e) {
					// do nothing
				}
			} else if (tClazz == URL.class) {
				URL u = getParent().getResource(name);
				if (u != null) {
					return u;
				}
			} else if (tClazz == Enumeration.class) {
				Enumeration e = getParent().getResources(name);
				if (e != null) {
					return e;
				}
			} 
		}

		/*
		 * 3.8.4  3. If the class or resource is in a package that is imported using
		 * Import-Package or was imported dynamically in a previous load, then the
		 * request is delegated to the exporting bundle’s class loader; otherwise the
		 * search continues with the next step. If the request is delegated to an
		 * OSGi Service Platform Release 4 55-268 Module Layer Version 1.3 Runtime Class Loading
		 * exporting class loader and the class or resource is not found, then the
		 * search terminates and the request fails.
		 */
		ExportedPackage[] exportedPackages = framework.getExportedPackages(pkgName);
		if (exportedPackages != null) {
			for (int i = 0; i < exportedPackages.length; i++) {
				ExportedPackage exportedPackage = exportedPackages[i];
				Bundle[] importingBundles = exportedPackage.getImportingBundles(); 
				
				if (importingBundles != null && ArrayUtil.contains(importingBundles, host)) {
					Bundle exportingBundle = exportedPackage.getExportingBundle();
					
					if (!exportingBundle.equals(host)) {							
						String[] exclude = ((ExportedPackageImpl) exportedPackage).getExclude();
						for (int j = 0; j < exclude.length; j++) {
							String filter = new StringBuilder(exportedPackage.getName()).append('.').append(exclude[j]).toString();
							if (TextUtil.wildcardCompare(filter, name) == 0) {
								return null;
							}
						}

						String[] include = ((ExportedPackageImpl) exportedPackage).getInclude();
						for (int j = 0; j < include.length; j++) {
							String filter = new StringBuilder(exportedPackage.getName()).append('.').append(include[j]).toString();
							if (TextUtil.wildcardCompare(filter, name) == 0) {
								/*
								* 3.8.4  3 If the class or resource is in a package that is imported using Import-
								* Package or was imported dynamically in a previous load, then the
								* request is delegated to the exporting bundle’s class loader; otherwise the
								* search continues with the next step. If the request is delegated to an
								* exporting class loader and the class or resource is not found, then the
								* search terminates and the request fails.
								*/
								BundleClassLoader classLoader = (BundleClassLoader) ((ExportedPackageImpl) exportedPackage).classLoader;
								
								if (tClazz == Class.class) {
									return classLoader.findClass(name);
								} else if (tClazz == URL.class) {
									URL u = classLoader.findResource(name);

									return u;
								} else { // if (tClazz == Enumeration.class) {
									Enumeration e = classLoader.findResources(name);

									return e;
								}
							}
						}
					}
				}
			}
		}
		
		return find1(name, tClazz);
	}
	
	// Waiting for Generics
	Object find1(String name, Class tClazz) throws Exception {
		/*
		 * 3.8.4  4. If the class or resource is in a package that is imported from one or more
		 * other bundles using Require-Bundle, the request is delegated to the class loaders of the other
		 * bundles, in the order in which they are specified in this bundle’s manifest. If the class or 
		 * resource is not found, then the search continues with the next step.
		 */
		
		ManifestEntry[] entries = ManifestEntry.parse(host.getHeaders().get(Constants.REQUIRE_BUNDLE));
		if (entries != null) {
			for (int i = 0; i < entries.length; i++) {
				ManifestEntry entry = entries[i];
				RequiredBundle[] requiredBundles = framework.getRequiredBundles(entry.getName());
				if (requiredBundles == null) {
					return null;
				}
				
				String versionAttribute = null;
				if (entry.hasAttribute(Constants.VERSION_ATTRIBUTE)) {
					versionAttribute = entry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
				}
				
				VersionRange versionRange = new VersionRange(versionAttribute);
				for (int j = 0; j < requiredBundles.length; j++) {
					RequiredBundle requiredBundle = requiredBundles[i];
					
					Bundle bundle = requiredBundle.getBundle();
					if (framework.getBundleType(bundle) == PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
						framework.log(LogService.LOG_WARNING, "Tried to use a fragment bundle as a required bundle.");
						continue;
					}
					
					if (versionRange.isIncluded(requiredBundle.getVersion())) {
						/*
						 * To use the proper BundleURLClassPath(s) (when update/uninstall) and apply the 
						 * associated permissions.
						 */
						if (tClazz == Class.class) {
							try {
								return bundle.loadClass(name);
							} catch (Exception e) {
								// do nothing
							}
						} else if (tClazz == URL.class) {
							URL u = bundle.getResource(name);
							if (u != null) {
								return u;
							}
						} else if (tClazz == Enumeration.class) {
							Enumeration e = bundle.getResources(name);
							if (e != null) {
								return e;
							}
						}
					}
				}
			}
		}	

		if (tClazz == Class.class) {
			try {
				return findClass(name);
			} catch (Exception e) {
				// nothing
			}
		} else if (tClazz == URL.class) {
			URL u = findResource(name);
			if (u != null) {
				return u;
			}
		} else if (tClazz == Enumeration.class) {
			Enumeration e = findResources(name);
			if (e != null) {
				return e;
			}
		} 

		/*
		 * 3.8.4  7. If the class or resource is in a package that is exported by the bundle or
		 * the package is imported by the bundle (using Import-Package or Require-
		 * Bundle), then the search ends and the class or resource is not found.
		 */
		String pkgName = tClazz == Class.class ? FrameworkUtil.getClassPackage(name) : FrameworkUtil.getResourcePackage(name);
		ExportedPackage[] exportedPackages = framework.getExportedPackages(pkgName);

		if (exportedPackages != null) {
			for (int i = 0; i < exportedPackages.length; i++) {
				ExportedPackage exportedPackage = exportedPackages[i];
				Bundle exportingBundle = exportedPackage.getExportingBundle();
	
				if (exportingBundle.equals(host)) {
					StringBuilder message = new StringBuilder(host.toString()).
						append(" exports Package(name=").
						append(pkgName).
						append(") but ");
					if (tClazz == Class.class) {
						message.append("Class");
					} else if (tClazz == URL.class) {
						message.append("Resource");
					} else if (tClazz == Enumeration.class) {
						message.append("Resources");
					} 					
					message.append("(name=").
						append(name).
						append(") is not available.");
					
					framework.log(LogService.LOG_DEBUG,	message.toString());
					if (tClazz == Class.class) {
						throw new ClassNotFoundException(message.toString());
					} else {
						return null;
					}
				}
			}
		}

		/*
		 * 3.8.4  8. Otherwise, if the class or resource is in a package that is imported using
		 * DynamicImport-Package, then a dynamic import of the package is now
		 * attempted. An exporter must conform to any implied package constraints.
		 * If an appropriate exporter is found, a wire is established so that
		 * future loads of the package are handled in Step 3. If a dynamic wire is not
		 * established, then the request fails.
		 * 
		 * 9. If the dynamic import of the package is established, the request is delegated
		 * to the exporting bundle’s class loader. If the request is delegated to
		 * an exporting class loader and the class or resource is not found, then the
		 * search terminates and the request fails.
		 */
		
		if (exportedPackages != null) {
			entries = ManifestEntry.parse(host.getHeaders().get(Constants.DYNAMICIMPORT_PACKAGE));
			if (entries != null) {
				for (int i = 0; i < entries.length; i++) {
					ManifestEntry entry = entries[i];
					if (TextUtil.wildcardCompare(entry.getName(), pkgName) == 0) {
						
						/*
						 * 3.8.2
						 */						
						Bundle[] bundles = null;
						if (entry.hasAttribute(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE)) {
							String symbolicName = entry.getAttributeValue(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE);
							// ???
							String versionRange = entry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
							
							bundles = framework.getBundles(symbolicName, versionRange);
						}
						
						for (int j = 0; j < exportedPackages.length; j++) {
							ExportedPackage exportedPackage = exportedPackages[j];
							if (
									bundles == null ||
									ArrayUtil.contains(bundles, exportedPackage.getExportingBundle())
								) {
								try {
									BundleClassLoader classLoader = (BundleClassLoader) ((ExportedPackageImpl) exportedPackage).classLoader;
									
									Object object = classLoader.find1(name, tClazz);
									if (object != null) {
										Bundle[] importingBundles = exportedPackage.getImportingBundles();
										if (importingBundles != null) {
											importingBundles = (Bundle[]) ArrayUtil.add(importingBundles, host);
										} else {
											importingBundles = new Bundle[] {
												host	
											};
										}
										
										// XXX see: Dependency Injection
										((ExportedPackageImpl) exportedPackage).setImportingBundles0(importingBundles);

										return object;
									}
								} catch (Exception e) {
									// do nothing
								}
							}
						}
					}
				}
			}
		}

	    return null;
	}

	protected String findLibrary(String libname) {
		String libraryPath = getLibraryPath(host, libname);
		
		if (libraryPath == null) {
			Bundle[] fragments = framework.getFragments(host);
			
			if (fragments != null) {
				for (int i = 0; i < fragments.length; i++) {
					Bundle fragment = fragments[i];
					libraryPath = getLibraryPath(fragment, libname);
					if (libraryPath != null) {
						break;
					}
				}
			}
		}
		
		return libraryPath;
	}


	/*
	 * 3.9
	 */

	String getLibraryPath(Bundle bundle, String libname) {
		String osName = System.getProperty(Constants.FRAMEWORK_OS_NAME);

		String osVersion = System.getProperty(Constants.FRAMEWORK_OS_VERSION);

		String processor = System.getProperty(Constants.FRAMEWORK_PROCESSOR);

		String language = System.getProperty(Constants.FRAMEWORK_LANGUAGE);

		String plafLibname = System.mapLibraryName(libname);

		Dictionary headers = bundle.getHeaders();
		
		try {
			ManifestEntry[] entries = ManifestEntry.parse(headers.get(Constants.BUNDLE_NATIVECODE));
			if (entries != null) {
				for (int i = 0; i < entries.length; i++) {
					ManifestEntry entry = entries[i];
					String libfilepath = entry.getName();
					if (libfilepath.charAt(0) == '/') {
						libfilepath = libfilepath.substring(1);
					}

					String libfilename = libfilepath.substring(libfilepath.lastIndexOf('/') + 1);
					if (plafLibname.equals(libfilename)) {
						boolean matched = true;

						boolean matchOsName = false;
						String[] values = entry.getAttributeValues(Constants.BUNDLE_NATIVECODE_OSNAME);
						for (int j = 0; i < values.length; i++) {
							String value = values[j];
							if (osName.contains(value)) {
								matchOsName = true;
								break;
							}
						}
						matched &= matchOsName;

						values = entry.getAttributeValues(Constants.BUNDLE_NATIVECODE_OSVERSION); 
						if (values != null && !osVersion.equals("*")) { 
							matched &= ArrayUtil.contains(values, osVersion);
						}

						values = entry.getAttributeValues(Constants.BUNDLE_NATIVECODE_PROCESSOR);
						matched &= ArrayUtil.contains(values, processor);

						values = entry.getAttributeValues(Constants.FRAMEWORK_LANGUAGE); 
						if (values != null && !language.equals("*")) { 
							matched &= ArrayUtil.contains(values, language);
						}
						
						if (matched) {
							try {
								return framework.getLibraryPath(bundle, libfilepath);
							} catch (IOException e) {
								framework.log(LogService.LOG_ERROR, e.getMessage(), e);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}

		return null;
	}
	
	private Bundle getBundle(URL url) {
		String host = url.getHost();
		
		long bundleId;
		
		int index = host.indexOf('.');
		if (index == -1) {
			bundleId = Long.parseLong(host);
		} else {
			bundleId = Long.parseLong(host.substring(0, index));
		}
		
		Bundle bundle = framework.getBundle(bundleId);
		
		return bundle;
	}
	
	// Local
	protected Class findClass(String name) throws ClassNotFoundException {
		try {
	        Class c = findLoadedClass(name);
	        if (c != null) {
	        	return c;
	        }

	        String entryName = name.replace('.', '/').concat(".class");
			InputStream is = null;
			Bundle bundle = null;
			for (int i = 0; i < classPaths.length; i++) {
				BundleURLClassPath classPath = classPaths[i];			
				is = classPath.getEntryAsStream(-1, entryName);
				if (is != null) {
					bundle = classPath.getBundle();
					break;
				}
			}

			if (is != null) {
				String pkgName = FrameworkUtil.getClassPackage(name);
				Package pkg = getPackage(pkgName);

				URL codesourceURL = new URL(bundle.getLocation());

				if (pkg != null) {
					if (pkg.isSealed()) {
						if (!pkg.isSealed(codesourceURL)) {
							throw new SecurityException(
								new StringBuilder("Sealing violation: package ").append(pkgName).append(" is sealed").toString());
						}
					}
				} else {
					definePackage(pkgName, null, null, null, null, null, null, null);
				}
				
				byte[] b = IOUtil.getBytes(is);
				
				/*
				 * Each bundle has its own protection domain.
				 */
				ProtectionDomain protectionDomain = framework.getProtectionDomain(bundle);				
				Class clazz = defineClass(name, b, 0, b.length, protectionDomain);
				
				/*
				 * 4.4.6.2   Lazy Activation Policy
				 * 
				 * A lazy activation policy indicates that the bundle, once started, must not be
				 * activated until a class is loaded from it; either during normal class loading or
				 * via the Bundle loadClass method. (...) When a class load triggers the lazy activation,
				 * the Framework must first define the triggering class.
				 * 
				 * XXX
				 * If update() method is invoked, the bundle is started with options = 0 and so it is 
				 * immediately activated.
				 */
				
				if (
						framework.isBundleActivationPolicyUsed(this.host) && 
						
						this.host.getState() == Bundle.STARTING &&
						
						((HostBundle) this.host).isActivationTriggered(pkgName) &&
						
						/*
						 * 4.7.2  During the shutdown, bundles with a lazy policy
						 * must not be activated even when classes are loaded from them.
						 */
						framework.getState() != Bundle.STOPPING
						) {
	        		((HostBundle)this. host).activate();
			    }

				return clazz;
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new ClassNotFoundException(e.getMessage(), e);
		}
		
	    throw new ClassNotFoundException(new StringBuilder("Class ").append(name).append(" not found.").toString());
	}

	// Local
    protected URL findResource(String name) {
		SecurityManager securityManager = System.getSecurityManager();
	
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(host, AdminPermission.RESOURCE));			
		}
	
		URL url = null;
		for (int i = 0; i < classPaths.length; i++) {
			BundleURLClassPath classPath = classPaths[i];			
			url = classPath.getEntry(name);
			if (url != null) {
				break;
			}
		}
	
		return url;
	}
    
	// Local
    protected Enumeration findResources(String name) {
    	return host.findEntries("", name, false);
    }

    /*
     * ClassLoader overridden methods
     */
    
	protected Class loadClass(String name, boolean resolve)	throws ClassNotFoundException {
    	Class c = findLoadedClass(name);
		if (c == null) {
			try {
				c = (Class) find0(name, Class.class);
				if (c == null) {
					throw new ClassNotFoundException(name);
				}
			} catch (Exception e) {
				throw new ClassNotFoundException(e.getMessage(), e);
			}
		}

		if (resolve) {
			resolveClass(c);
		}
		
		return c;
	}
	
    public URL getResource(String name) {
    	URL url = null;
    	try {
    		url = (URL) find0(name, URL.class);
		} catch (Exception e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
		
		return url;
    }

	public Enumeration getResources(String name) throws IOException {
		if (name.startsWith("/")) {
			name = name.substring(1);
		}
		
		if (host.getState() == Bundle.INSTALLED) {
			/*
			 * 6.1.4.17  If this bundle cannot be resolved,
			 * then only this bundle must be searched for the specified resources. 
			 */

			Enumeration e = findResources(name);
			if (e == null) {
				e = EMPTY_ENUMERATION;
			}
		}

		try {
			Enumeration e = (Enumeration) find0(name, Enumeration.class);
			
			if (e == null) {
				e = EMPTY_ENUMERATION;
			}
			return e;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
}
