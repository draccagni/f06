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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.PackagePermission;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;

import f06.util.ArrayUtil;
import f06.util.ManifestEntry;
import f06.util.TextUtil;
import f06.util.SerialExecutorService;

/*
 * 7.1.3  PackageAdmin (...) provides access to the internal structures of the Framework
 * related to package sharing, fragments and required bundles.
 */
class PackageAdminImpl implements PackageAdmin {

	class BundleListenerImpl implements BundleListener {
		
		public void bundleChanged(BundleEvent event) {
			Bundle bundle = event.getBundle();
			
			int type = event.getType();
			
			if (
					type == BundleEvent.UPDATED ||
					type == BundleEvent.UNINSTALLED
				) { 
				synchronized (changedBundlesLock) {
					changedBundles.add(bundle);
				}
			} 
		}
	}
	
	private Framework framework;
	
	private List changedBundles;
	
	private Object changedBundlesLock;
	
	private SerialExecutorService executor;

	private Map exportedPackagesByName;

	private Map requiredBundlesBySymbolicName;
		
	private Object exportedPackagesLock;
	

	public PackageAdminImpl(BundleContext context) {
		this.framework = (Framework) context.getBundle();
		
		this.changedBundles = new ArrayList();
		
		this.changedBundlesLock = new Object();
		
		this.executor = new SerialExecutorService(new StringBuilder(getClass().getName()).append(" refreshPackages").toString());
		
		this.exportedPackagesByName = new TreeMap(new Comparator() {
			public int compare(Object o1, Object o2) {
				String s = (String) o1;
				String ws = (String) o2;

				return TextUtil.wildcardCompare(ws, s);
			}
		});
		
		this.requiredBundlesBySymbolicName = new HashMap();
		
		this.exportedPackagesLock = new Object();
		
		context.addBundleListener(new BundleListenerImpl());
	}

	public Bundle getBundle(Class cls) {
		ClassLoader classLoader = cls.getClassLoader();
		if (classLoader instanceof BundleClassLoader) {
			return ((BundleClassLoader) classLoader).getBundle();
		}
		
		return framework;
	}

	public int getBundleType(Bundle bundle) {
		return FrameworkUtil.isFragmentHost(bundle.getHeaders()) ? PackageAdmin.BUNDLE_TYPE_FRAGMENT : 0x00000000;
	}

	public Bundle[] getBundles(String symbolicName, String versionRange) {
		List list = new ArrayList();

		VersionRange versionRange0 = new VersionRange(versionRange);
		
		Bundle[] bundles = framework.getBundles();
		for (int i = 0; i < bundles.length; i++) {
			Bundle bundle = bundles[i];
			String bundleSymbolicName = bundle.getSymbolicName();

			Version version = bundle.getVersion();

			if (symbolicName.equals(bundleSymbolicName) && versionRange0.isIncluded(version)) {
				list.add(bundle);
			}
		}
		
		Collections.sort(list, new Comparator() {
			public int compare(Object o1, Object o2) {
				return -Version.parseVersion((String) ((Bundle) o1).getHeaders().get(Constants.BUNDLE_VERSION)).
					compareTo(Version.parseVersion((String) ((Bundle) o2).getHeaders().get(Constants.BUNDLE_VERSION)));
			}
		});

		return (Bundle[]) (list.isEmpty() ? null : list.toArray(new Bundle[0]));
	}

	public Bundle[] getFragments(Bundle bundle) {
		/*
		 * PackageAdmin
		 * 
		 * JavaDoc  This method does not attempt to resolve the specified bundle. If the
		 * specified bundle is not resolved then null is returned.
		 */
		if (bundle.getState() == Bundle.INSTALLED) {
			return null;
		}
		
		Bundle[] fragments = new Bundle[0]; 
		
    	Bundle[] fragments0 = framework.getFragments0(bundle);
    	if (fragments0 != null) {
			for (int i = 0; i < fragments0.length; i++) {
				Bundle fragment = fragments0[i];
				if (fragment.getState() == Bundle.RESOLVED) {
					fragments = (Bundle[]) ArrayUtil.add(fragments, fragment);
				}
			}
    	}
		
		return fragments.length > 0 ? fragments : null;
	}
		
	public Bundle[] getHosts(Bundle bundle) {
		
		/*
		 * PackageAdmin
		 * 
		 * JavaDoc  Returns an array containing the host bundle to which the specified fragment 
		 * bundle is attached or null if the specified bundle is not attached to a host or is 
		 * not a fragment bundle.
		 */
		
		if (getBundleType(bundle) != PackageAdmin.BUNDLE_TYPE_FRAGMENT || bundle.getState() != Bundle.RESOLVED) {
			return null;
		}
		
		return framework.getHosts0(bundle);
	}

	public RequiredBundle[] getRequiredBundles(String symbolicName) {
		RequiredBundle[] requiredBundles = (RequiredBundle[]) requiredBundlesBySymbolicName.get(symbolicName);
		
		return requiredBundles != null ? (requiredBundles.length > 0 ? requiredBundles : null) : null;
	}
		
	public ExportedPackage getExportedPackage(String name) {
		ExportedPackage[] exportedPackages = getExportedPackages(name);
		if (exportedPackages != null) {
			return exportedPackages[0];
		}

		return null;
	}

	public ExportedPackage[] getExportedPackages(Bundle bundle) {
		synchronized (exportedPackagesLock) {
			ExportedPackage[] exportedPackages = new ExportedPackage[0];
	
			Iterator it = exportedPackagesByName.values().iterator();
			
			while (it.hasNext()) {
				ExportedPackage[] exportedPackages0 = (ExportedPackage[]) it.next();
	
				for (int j = 0; j < exportedPackages0.length; j++) {
					ExportedPackage exportedPackage = exportedPackages0[j];
					Bundle exportingBundle = exportedPackage.getExportingBundle();
	
					if (exportingBundle.equals(bundle)) {						
						exportedPackages = (ExportedPackage[]) ArrayUtil.add(exportedPackages, exportedPackage);
					}
				}
			}
	
			return exportedPackages.length > 0 ? exportedPackages : null;
		}
	}

	public ExportedPackage[] getExportedPackages(String name) {
		synchronized (exportedPackagesLock) {
			return (ExportedPackage[]) exportedPackagesByName.get(name);
		}
	}
	
	private void wireExportPackage(Bundle bundle) throws Exception {
		String exportPackage = (String) bundle.getHeaders().get(Constants.EXPORT_PACKAGE);
		if (exportPackage == null) {
			return;
		}
		
		/*
		 * If this bundle has exported any packages, these packages must not be
		 * updated. Instead, the previous package version must remain exported until
		 * the PackageAdmin.refreshPackages method has been has been called or the
		 * Framework is relaunched.
		 */
		Bundle host;
		if (getBundleType(bundle) == PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
			host = framework.getHosts0(bundle)[0];
		} else {
			host = bundle;
		}
		
		ManifestEntry[] entries = ManifestEntry.parse(exportPackage);
		if (entries == null)
			return;
		
		ClassLoader classLoader = ((HostBundle) bundle).getClassLoader();
		
		NEXT_ENTRY: for (int i = 0; i < entries.length; i++) {
			ManifestEntry entry = entries[i];
			String exportPkgName = entry.getName();
			
			if (!host.equals(bundle)) {
				/*
				 * Bundle is a Fragment. Check if the Package has been already exported.
				 */
				ExportedPackage[] exportedPackages = getExportedPackages(exportPkgName);
				if (exportedPackages != null) {
					for (int j = 0 ; j < exportedPackages.length; j++) {
						ExportedPackage exportedPackage = exportedPackages[j];
						if (exportedPackage.getExportingBundle().equals(host)) {
							continue NEXT_ENTRY;
						}
					}
				}
			}
			
			/*
			 * 3.11
			 */
			if (exportPkgName.startsWith("java.")) {
				throw new Exception(new StringBuilder(bundle.toString()).append(") exports java.* packages.").toString());
			}
			
			SecurityManager securityManager = System.getSecurityManager();
			if (securityManager != null) {
				securityManager.checkPermission(new PackagePermission(exportPkgName, PackagePermission.EXPORT));
			}

			String specificationVersion = null;
			if (entry.hasAttribute(Constants.PACKAGE_SPECIFICATION_VERSION)) {
				specificationVersion = entry.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION);
			}

			String versionAttribute = null;
			if (entry.hasAttribute(Constants.VERSION_ATTRIBUTE)) {
				versionAttribute = entry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
			}
			
			if (specificationVersion != null && versionAttribute != null && !specificationVersion.equals(versionAttribute)) {
				throw new Exception(new StringBuilder("Bundle(").append(bundle.getBundleId()).append(") exports a Package(name=").append(exportPkgName).append(") with specification-version attribute not equal to version attribute.").toString());
			}

			Version version;
			if (versionAttribute != null) {
				version = Version.parseVersion(versionAttribute);
			} else if (specificationVersion != null) {
				version = Version.parseVersion(specificationVersion);
			} else {
				/*
				 * {@link ExportedPackage}
				 */
				version = Version.emptyVersion;
			}
			
			String company = null;
			if (entry.hasAttribute(Constants0.BUNDLE_COMPANY_ATTRIBUTE)) {
				company = entry.getAttributeValue(Constants0.BUNDLE_COMPANY_ATTRIBUTE);
			}
			
			String[] uses = null;
			if (entry.hasAttribute(Constants.USES_DIRECTIVE)) {
				uses = entry.getAttributeValue(Constants.USES_DIRECTIVE).split("\\,");
			}
			
			String[] mandatory = null;
			if (entry.hasAttribute(Constants.MANDATORY_DIRECTIVE)) {
				mandatory = entry.getAttributeValue(Constants.MANDATORY_DIRECTIVE).split("\\,");
			}
			
			/*
			 * 3.6.7  The default for the include directive is ’*’ 
			 */
			String[] include;
			if (entry.hasAttribute(Constants.INCLUDE_DIRECTIVE)) {
				include = entry.getAttributeValue(Constants.INCLUDE_DIRECTIVE).split("\\,");
			} else {
				include = new String[] {
					"*"
				};
			}
			
			/*
			 * 3.6.7  for the exclude directive, so that no classes or resources are excluded, an
			 * empty list that matches no names.  
			 */
			String[] exclude;
			if (entry.hasAttribute(Constants.EXCLUDE_DIRECTIVE)) {
				exclude = entry.getAttributeValue(Constants.EXCLUDE_DIRECTIVE).split("\\,");
			} else {
				exclude = new String[0];
			}

			ExportedPackage exportedPackage = new ExportedPackageImpl(host, exportPkgName, specificationVersion, version, company, uses, mandatory, include, exclude, classLoader);
			
			/*
			 * If a bundle has been updated and exports packages in use by other bundles,
			 * the exported packages have to be available until PackageAdmin#refreshPackages
			 * method has been called.
			 */
			
			synchronized (exportedPackagesLock) {
				ExportedPackage[] exportedPackages = (ExportedPackage[]) exportedPackagesByName.get(exportPkgName);
				if (exportedPackages != null) {
					exportedPackages = (ExportedPackage[]) ArrayUtil.add(exportedPackages, exportedPackage);
					
					Arrays.sort(exportedPackages, new Comparator() {
						public int compare(Object o1, Object o2) {
							ExportedPackage ep1 = (ExportedPackage) o1;
							ExportedPackage ep2 = (ExportedPackage) o2;
	
							return -ep1.getVersion().compareTo(ep2.getVersion());
						}
					});
				} else {
					exportedPackages = new ExportedPackage[] {
						exportedPackage
					};
				}
			
				exportedPackagesByName.put(exportPkgName, exportedPackages);
			}
		}
	}
	
	private void wireImportPackage(Bundle bundle) throws Exception {
		String importPackage = (String) bundle.getHeaders().get(Constants.IMPORT_PACKAGE);
		if (importPackage == null) {
			return;
		}
		
		Bundle host;
		if (getBundleType(bundle) == PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
			host = framework.getHosts0(bundle)[0];
		} else {
			host = bundle;
		}
		
		ManifestEntry[] entries = ManifestEntry.parse(importPackage);
		NEXT_ENTRY: for (int i = 0; i < entries.length; i++) {
			ManifestEntry importPackageEntry = entries[i];
			String importPkgName = importPackageEntry.getName();
			
			SecurityManager securityManager = System.getSecurityManager();
			if (securityManager != null) {
				securityManager.checkPermission(new PackagePermission(importPkgName, PackagePermission.IMPORT));
			}
			
			/*
			 * 3.11
			 */
			if (importPkgName.startsWith("java.")) {
				throw new Exception(new StringBuilder("Bundle(").append(bundle.getBundleId()).append(") imports java.* packages.").toString());
			}

			/*
			 * 3.5.4  resolution – Indicates that the packages must be resolved if the value is
			 * mandatory, which is the default. If mandatory packages cannot be
			 * resolved, then the bundle must fail to resolve. A value of optional indicates
			 * that the packages are optional.
			 */
			
			boolean mandatory = true;
			if (importPackageEntry.hasAttribute(Constants.RESOLUTION_DIRECTIVE)) {
				mandatory = importPackageEntry.getAttributeValue(Constants.RESOLUTION_DIRECTIVE).equals(Constants.MANDATORY_DIRECTIVE);
			}

			ExportedPackage[] exportedPackages = getExportedPackages(importPkgName);
			if (exportedPackages == null) {
				if (mandatory) {
					throw new Exception(new StringBuilder("Bundle(").
							append(bundle.getBundleId()).
					        append(") imports a Package(name=").
					        append(importPkgName).
					        append(") not exported by any Bundle.").toString());
				}
				
				continue;
			}
			
			if (!host.equals(bundle)) {
				/*
				 * Bundle is a Fragment. Check if the Package has been already imported.
				 */
				if (exportedPackages != null) {
					for (int j = 0 ; j < exportedPackages.length; j++) {
						ExportedPackage exportedPackage = exportedPackages[j];
						if (ArrayUtil.contains(exportedPackage.getImportingBundles(), host)) {
							continue NEXT_ENTRY;
						}
					}
				}
			}
			
			/*
			 * 3.5.4  bundle-symbolic-name – The bundle symbolic name of the exporting
			 * bundle. In the case of a Fragment bundle, this will be the host bundle’s
			 * symbolic name.
			 */
			
			String symbolicName = null;
			if (importPackageEntry.hasAttribute(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE)) {
				symbolicName = importPackageEntry.getAttributeValue(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE);
			}
			
			/*
			 * 3.5.4  bundle-version – A version-range to select the bundle version of the
			 * exporting bundle. The default value is [0.0.0, infinite). See Version Matching
			 * on page 41. In the case of a Fragment bundle, the version is from the host
			 * bundle.
			 */
			
			Bundle[] bundles = null;
			if (symbolicName != null) {
				String bundleVersionAttribute = null;
				if (importPackageEntry.hasAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE)) {
					bundleVersionAttribute = importPackageEntry.getAttributeValue(Constants.BUNDLE_VERSION_ATTRIBUTE);
				}

				VersionRange bundleVersion = new VersionRange(bundleVersionAttribute);
			    bundles = getBundles(symbolicName, bundleVersion.toString());
				
				if (bundleVersionAttribute != null && bundles == null) {
					if (mandatory) {
						throw new Exception(new StringBuilder("Bundle(").
								append(bundle.getBundleId()).
								append(") imports a Package(name=").
								append(importPackage).
								append(") requiring a not available Bundle(symbolicName=").
								append(symbolicName).
								append(",version=").
								append(bundleVersionAttribute).
								append(").").toString());
					}
					
					continue;
				}
		    }
			
			String versionAttribute = null;
			if (importPackageEntry.hasAttribute(Constants.VERSION_ATTRIBUTE)) {
				versionAttribute = importPackageEntry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
			}
			
			VersionRange version = new VersionRange(versionAttribute);
			
			for (int j = 0; j < exportedPackages.length; j++) {
				ExportedPackage exportedPackage = exportedPackages[j];
				
				if (exportedPackage.isRemovalPending()) {
					continue;
				}
				
				AbstractBundle exportingBundle = (AbstractBundle) exportedPackage.getExportingBundle();
				if (bundles != null) {
					if (!ArrayUtil.contains(bundles, exportingBundle)) {
						continue;
					}
				}
				
				Version packageVersion = exportedPackage.getVersion();
				
				/*
				 * 3.5.4  specification-version – This attribute is an alias of the version attribute
				 * only to ease migration from earlier versions. If the version attribute is
				 * present, the values must be equal.
				 */
				
				String specificationVersion = null;
				if (importPackageEntry.hasAttribute(Constants.PACKAGE_SPECIFICATION_VERSION)) {
					specificationVersion = importPackageEntry.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION);
				}

				/*
				 * 3.11
				 */
				if (specificationVersion != null && versionAttribute != null && !specificationVersion.equals(version.toString())) {
					throw new Exception(new StringBuilder("Bundle(").
							append(bundle.getBundleId()).
					        append(") requires a Bundle(symbolicName=").
							append(symbolicName).append(") with specification-version attribute not equal to version attribute.").append(importPkgName).toString());
				}
				
				if (versionAttribute == null) {
				    version = new VersionRange(specificationVersion);
				}

				if (version.isIncluded(packageVersion)) {
					/*
					 * 3.5.5  uses – If this exported package is chosen as an export, then the resolver must ensure that importers of this package wire
					 * to the same versions of the package in this list.
					 */
					String[] uses = ((ExportedPackageImpl) exportedPackage).getUses();
					if (uses != null) {
						ManifestEntry[] exportPackageEntries = ManifestEntry.parse(exportingBundle.getHeaders().get(Constants.EXPORT_PACKAGE));
						for (int k = 0; k < exportPackageEntries.length; k++) {
							ManifestEntry exportPackageEntry = exportPackageEntries[k];
							for (int l = 0; l < uses.length; l++) {
								if (exportPackageEntry.getName().equals(uses[l])) {
									if (exportPackageEntry.hasAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE)) {
									    Version exporterVersion = new Version(exportPackageEntry.getAttributeValue(Constants.BUNDLE_VERSION_ATTRIBUTE));
									    if (exportPackageEntry.hasAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE)) {
										    VersionRange importerVersion = new VersionRange(exportPackageEntry.getAttributeValue(Constants.BUNDLE_VERSION_ATTRIBUTE));
										    if (!importerVersion.isIncluded(exporterVersion)) {
												if (mandatory) {
													throw new Exception(new StringBuilder("Bundle(").
															append(bundle.getBundleId()).
													        append(") imports a Package(name=").append(importPkgName).append(") not wired to the same version of the Package specified in the uses attribute.").toString());
												}
												
										    	continue;
										    }
									    }
									}
								}
							}
						}
					}
					
					/*
					 * Add resolving bundle to the list of importing bundles
					 */
					Bundle[] importingBundles = exportedPackage.getImportingBundles();
					if (importingBundles == null) {
						importingBundles = new Bundle[] {
								host
						};
					} else {
						importingBundles = (Bundle[]) ArrayUtil.add(importingBundles, host);
					}
					
					((ExportedPackageImpl) exportedPackage).setImportingBundles0(importingBundles = importingBundles);
					
					continue NEXT_ENTRY;
				}
			}
			
			throw new Exception(new StringBuilder("Bundle(").
					append(bundle.getBundleId()).
			        append(") imports a Package(name=").append(importPkgName).append(",version=").append(version).append(") not exported by any Bundle.").toString());
		}
	}
	
	private void wireRequireBundle(Bundle bundle) throws Exception {
		String requireBundle = (String) bundle.getHeaders().get(Constants.REQUIRE_BUNDLE);
		if (requireBundle == null) {
			return;
		}

		Bundle host;
		if (getBundleType(bundle) == PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
			host = framework.getHosts0(bundle)[0];
		} else {
			host = bundle;
		}
		
		ManifestEntry[] entries = ManifestEntry.parse(requireBundle);
		NEXT_ENTRY: for (int i = 0; i < entries.length; i++) {
			ManifestEntry entry = entries[i];
			String symbolicName = entry.getName();
			
			String versionAttribute = null;
			if (entry.hasAttribute(Constants.VERSION_ATTRIBUTE)) {
				versionAttribute = entry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
			}
			VersionRange versionRange = new VersionRange(versionAttribute);

			RequiredBundle[] requiredBundles = getRequiredBundles(symbolicName);

			if (requiredBundles != null) {
				if (!host.equals(bundle)) {
					/*
					 * Bundle is a Fragment. Check if the Package has been already imported.
					 */
					for (int j = 0 ; j < requiredBundles.length; j++) {
						RequiredBundle requiredBundle = requiredBundles[j];
						if (ArrayUtil.contains(requiredBundle.getRequiringBundles(), host)) {
							continue NEXT_ENTRY;
						}
					}
				}
				
				for (int j = 0; j < requiredBundles.length; j++) {
					RequiredBundle requiredBundle = requiredBundles[j];
					if (!requiredBundle.isRemovalPending() && versionRange.isIncluded(requiredBundle.getVersion())) {
						Bundle[] requiringBundles = requiredBundle.getRequiringBundles();
						if (requiringBundles != null) {
							requiringBundles = (Bundle[]) ArrayUtil.add(requiringBundles, host);
						} else {
							requiringBundles = new Bundle[] {
									host
							};
						}
						
						((RequiredBundleImpl) requiredBundle).setRequiringBundles0(requiringBundles);
						
						continue NEXT_ENTRY;
					}
				}
			} else {
				requiredBundles = new RequiredBundle[0];
			}
				
			Bundle[] bundles = getBundles(symbolicName, versionAttribute);
			if (bundles == null) {
				throw new Exception(new StringBuilder("Bundle(").
						append(bundle.getBundleId()).
				        append(") requires a not available Bundle(symbolicName=").
				        append(symbolicName).
				        append(",version=").
				        append(versionAttribute).
				        append(")").toString());
			}
			
			for (int j = 0; j < bundles.length; j++) {
				Bundle bundle0 = bundles[j];
				/*
				 * ?.?.?  try to resolve it if it isn't
				 */							
				if (bundle0.getState() < Bundle.RESOLVED) {
					if (!resolveBundle(bundle0)) {
						unresolveBundle(bundle0);
						
						throw new Exception(new StringBuilder("Bundle(").
								append(bundle.getBundleId()).
						        append(") requires a not resolvable Bundle(").append(bundle0.getBundleId()).append(").").toString());
					}
				}							
				
				RequiredBundle requiredBundle = new RequiredBundleImpl(bundle0);
				
				Bundle[] requiringBundles = new Bundle[] {
						host
				};

				((RequiredBundleImpl) requiredBundle).setRequiringBundles0(requiringBundles);
				
				requiredBundles = (RequiredBundle[]) ArrayUtil.add(requiredBundles, requiredBundle);
				
				requiredBundlesBySymbolicName.put(symbolicName, requiredBundles);
			}
		}
	}
	
	/*
	 * Remove related ExportedPackage entries.
	 */
	private void unwireExportedPackages(Bundle bundle) {
		try {
			ExportedPackage[] exportedPackages = getExportedPackages(bundle);
			
			if (exportedPackages != null) {
				for (int j = 0; j < exportedPackages.length; j++) {
					ExportedPackage exportedPackage = exportedPackages[j];
					
					synchronized (exportedPackagesLock) {
						ExportedPackage[] exportedPackages0 = (ExportedPackage[]) getExportedPackages(exportedPackage.getName());
						exportedPackages0 = (ExportedPackage[]) ArrayUtil.remove(exportedPackages0, exportedPackage);
					
						exportedPackagesByName.put(exportedPackage.getName(), exportedPackages0);
					}
				}
			}

			String importPackage = (String) bundle.getHeaders().get(Constants.IMPORT_PACKAGE);
			
			if (importPackage != null) {
				ManifestEntry[] entries = ManifestEntry.parse(importPackage);
				for (int i = 0; i < entries.length; i++) {
					String importPkgName = entries[i].getName();
					
					exportedPackages = (ExportedPackage[]) getExportedPackages(importPkgName);
	
					for (int j = 0; j < exportedPackages.length; j++) {
						ExportedPackage exportedPackage = exportedPackages[j];
	
						Bundle[] importingBundles = exportedPackage.getImportingBundles();
						if (importingBundles != null) {
							if (ArrayUtil.contains(importingBundles, bundle)) {
								importingBundles = (Bundle[]) ArrayUtil.remove(importingBundles, bundle);
								// XXX see: Dependency Injection
								((ExportedPackageImpl) exportedPackage).setImportingBundles0(importingBundles);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			// do nothing
		}
	}
	
	/*
	 * Remove related RequiredBundle entries.
	 */
	private void unwireRequiredBundles(Bundle bundle) {
		try {
			String requireBundle = (String) bundle.getHeaders().get(Constants.REQUIRE_BUNDLE);

			if (requireBundle != null) {
				ManifestEntry[] entries = ManifestEntry.parse(requireBundle);
				for (int i = 0; i < entries.length; i++) {
					ManifestEntry entry = entries[i];
					String symbolicName = entry.getName();
					
					String versionAttribute = null;
					if (entry.hasAttribute(Constants.VERSION_ATTRIBUTE)) {
						versionAttribute = entry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
					}
					VersionRange versionRange = new VersionRange(versionAttribute);
	
					RequiredBundle[] requiredBundles = getRequiredBundles(symbolicName);
	
					if (requiredBundles != null) {
						for (int j = 0; j < requiredBundles.length; j++) {
							RequiredBundle requiredBundle = requiredBundles[j];
							if (versionRange.isIncluded(requiredBundle.getVersion())) {
								Bundle[] requiringBundles = requiredBundle.getRequiringBundles();
								
								if (requiringBundles.length > 1) {
									requiringBundles = (Bundle[]) ArrayUtil.remove(requiringBundles, bundle);
								} else {
									requiringBundles = null;
								}
								
								((RequiredBundleImpl) requiredBundle).setRequiringBundles0(requiringBundles);
							}
						}
					}
				}
			}
			
		    RequiredBundle[] requiredBundles = (RequiredBundle[]) requiredBundlesBySymbolicName.get(bundle.getSymbolicName());
		    if (requiredBundles != null) {
			    for (int i = 0; i < requiredBundles.length; i++) {
			    	RequiredBundle requiredBundle = requiredBundles[i];
			    	
			    	if (requiredBundle.getBundle() == bundle) {
			    		if (requiredBundles.length > 1) {
			    			requiredBundles = (RequiredBundle[]) ArrayUtil.remove(requiredBundles, requireBundle);
			    			
			    			requiredBundlesBySymbolicName.put(bundle.getSymbolicName(), requiredBundles);
			    		} else {
			    			requiredBundlesBySymbolicName.remove(bundle.getSymbolicName());
			    		}
			    		
			    		break;
			    	}
			    }
		    }
		} catch (Exception e) {
			// do nothing
		}
	}

	public boolean resolveBundles(Bundle[] bundles) {
		/*
		 * 4.8.1.1
		 */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(framework, AdminPermission.RESOLVE));		
		}
		
		boolean resolved = true;
		
		for (int i = 0; i < bundles.length; i++) {
			Bundle bundle0 = bundles[i];
			
			if (!resolveBundle(bundle0)) {
				unresolveBundle(bundle0);
				
				resolved = false;
			}
		}
		
		return resolved;
	}

	private boolean resolveBundle(Bundle bundle) {
		try {
			synchronized (this) {
				/*
				 * Export-Package
				 */
				
				wireExportPackage(bundle);
				
				/*
				 * Import-Package
				 */
				
				wireImportPackage(bundle);
				
				/*
				 * Require-Bundle
				 */
				
				wireRequireBundle(bundle);
				
				/*
				 * Attach fragments
				 */
				
				String symbolicName = (String) bundle.getHeaders().get(Constants.BUNDLE_SYMBOLICNAME);
				if (symbolicName != null) {
					ManifestEntry[] entries = ManifestEntry.parse(symbolicName);
					
					/*
					 * 3.5.2  fragment-attachment – Defines how fragments are allowed to be
					 * attached, see the optional fragments in Fragment Bundles on page 67. The
					 * following values are valid for this directive:
					 * 
					 *   always – Fragments can attach at any time while the host is resolved
					 *   or during the process of resolving.
					 *   
					 *   never – No fragments are allowed.
					 *   
					 *   resolve-time – Fragments must only be attached during resolving.
					 */
					
					boolean attachFragments = true;
					if (entries[0].hasAttribute(Constants.FRAGMENT_ATTACHMENT_DIRECTIVE)) {
						attachFragments = !entries[0].getAttributeValue(Constants.FRAGMENT_ATTACHMENT_DIRECTIVE).equals(Constants.FRAGMENT_ATTACHMENT_NEVER);
				    }
					
					if (attachFragments) {
						Bundle[] fragments = framework.getFragments0(bundle);
						if (fragments != null) {
							
							/*
							 * 3.14  Fragments are bundles that are attached to a host bundle by the Framework.
							 * Attaching is done as part of resolving: the Framework appends the relevant
							 * definitions of the fragment bundles to the host’s definitions before the host
							 * is resolved.
							 * 
							 * 1 Append the import definitions for the Fragment bundle that do not conflict
							 * with an import definition of the host to the import definitions of the
							 * host bundle. A Fragment import definition conflicts with a host import
							 * definition if it has the same package name and any of its directives or
							 * matching attributes are different. If a conflict is found, the Fragment
							 * bundle is not attached to the host bundle. A Fragment can provide an
							 * import statement for a private package of the host. The private package
							 * in the host is hidden in that case.
							 */
							
							for (int i = 0; i < fragments.length; i++) {
								Bundle fragment = fragments[i];
								
								checkAttachConditions(fragment);
								
								wireImportPackage(fragment);

								/*
								 * 3.14  2 Append the Require-Bundle entries of the fragment bundle that do not
								 * conflict with a Require-Bundle entry of the host to the Require-Bundle
								 * entries of the host bundle. A fragment Require-Bundle entry conflicts
								 * with a host Require-Bundle entry only if it has the same bundle symbolic
								 * name but a different version range. If a conflict is found, the fragment is
								 * not attached to the host bundle.
								 */

								wireRequireBundle(fragment);
								
								/*
								 * 3.14  3 Append the export definitions of a Fragment bundle to the export definitions
								 * of the host bundle unless the exact definition (directives and
								 * attributes must match) is already present in the host. Fragment bundles
								 * can therefore add additional exports for the same package name. The
								 * bundle-version attributes and bundle-symbolic-name attributes will
								 * reflect the host bundle.
								 */

								wireExportPackage(fragment);
								
								/*
								 * 3.14  A Fragment bundle must enter the resolved state only if it has been successfully
								 * attached to its host bundle.
								 */
								((AbstractBundle) fragment).setState(Bundle.RESOLVED);
								
								/*
								 * 4.6.1  RESOLVED – Sent when the Framework has resolved a bundle. The
				                 * state is now the Bundle RESOLVED state.
								 */
								
								BundleEvent bundleEvent = new BundleEvent(BundleEvent.RESOLVED, fragment);
								framework.postBundleEvent(bundleEvent);
							}
						}
					}
				}

				((AbstractBundle) bundle).setState(Bundle.RESOLVED);
				
				/*
				 * 4.6.1  RESOLVED – Sent when the Framework has resolved a bundle. The
			     * state is now the Bundle RESOLVED state.
				 */
				
				BundleEvent bundleEvent = new BundleEvent(BundleEvent.RESOLVED, bundle);
				framework.postBundleEvent(bundleEvent);
			}
			
			return true;
		} catch (Exception e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
			
			return false;
		}
	}
	
	private void checkAttachConditions(Bundle bundle) throws Exception {
		Bundle host = framework.getHosts0(bundle)[0];

		String importPackage = (String) bundle.getHeaders().get(Constants.IMPORT_PACKAGE);

		if (importPackage != null) {
			ManifestEntry[] importPackageEntries = ManifestEntry.parse(importPackage);
			for (int i = 0; i < importPackageEntries.length; i++) {
				ManifestEntry importPackageEntry = importPackageEntries[i];
				String importPkgName = importPackageEntry.getName();
	
				ExportedPackage[] exportedPackages = getExportedPackages(importPkgName);
				if (exportedPackages != null) {
					for (int j = 0 ; j < exportedPackages.length; j++) {
						ExportedPackage exportedPackage = exportedPackages[j];
						if (ArrayUtil.contains(exportedPackage.getImportingBundles(), host)) {
							if (importPackageEntry.hasAttribute(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE)) {
								String symbolicName = importPackageEntry.getAttributeValue(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE);
								
								if (symbolicName.equals(exportedPackage.getExportingBundle().getSymbolicName())) {
									throw new Exception(new StringBuilder(bundle.toString()).
											append(" imports Package(name=").
											append(importPkgName).
											append(") from Bundle(symbolicName=").
											append(symbolicName).
											append(") in spite of Host Bundle-Import declaration.").toString());
								}
							}
							
							if (importPackageEntry.hasAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE)) {
								String bundleVersionAttribute = importPackageEntry.getAttributeValue(Constants.BUNDLE_VERSION_ATTRIBUTE);
								
								if (!new VersionRange(bundleVersionAttribute).isIncluded(exportedPackage.getVersion())) {
									throw new Exception(new StringBuilder(bundle.toString()).
											append(" imports Package(name=").
											append(importPkgName).
											append(",version=").
											append(bundleVersionAttribute).
											append(") in spite of Host Import-Package declaration.").toString());
								}
							}
						}
					}
				}
			}
		}

		String requireBundle = (String) bundle.getHeaders().get(Constants.REQUIRE_BUNDLE);

		if (requireBundle != null) {
			ManifestEntry[] requireBundleEntries = ManifestEntry.parse(requireBundle);
			for (int i = 0; i < requireBundleEntries.length; i++) {
				ManifestEntry entry = requireBundleEntries[i];
				String symbolicName = entry.getName();
				
				RequiredBundle[] requiredBundles = getRequiredBundles(symbolicName);
	
				if (requiredBundles != null) {
					if (host.equals(bundle)) {
						/*
						 * Bundle is a Fragment. Check if the Package has been already imported.
						 */
						for (int j = 0 ; j < requiredBundles.length; j++) {
							RequiredBundle requiredBundle = requiredBundles[j];
							if (ArrayUtil.contains(requiredBundle.getRequiringBundles(), host)) {
								if (entry.hasAttribute(Constants.VERSION_ATTRIBUTE)) {
									String versionAttribute = entry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
									
									if (!new VersionRange(versionAttribute).isIncluded(requiredBundle.getVersion())) {
										throw new Exception(new StringBuilder(bundle.toString()).
												append(" requires Bundle{symbolicName=").
												append(symbolicName).
												append(",version=").
												append(versionAttribute).
												append(") in spite of Host Require-Bundle declaration.").toString());									
									}
								}
							}
						}
					}
				}
			}
		}
		
		String exportPackage = (String) bundle.getHeaders().get(Constants.EXPORT_PACKAGE);
		
		if (exportPackage != null) {
			ManifestEntry[] entries = ManifestEntry.parse(exportPackage);
			if (entries == null)
				return;
			
			ExportedPackage[] exportedPackages = getExportedPackages(host);
	
			if (exportedPackages != null) {
				for (int i = 0; i < entries.length; i++) {
					ManifestEntry entry = entries[i];
					String exportPkgName = entry.getName();
					
					for (int j = 0 ; j < exportedPackages.length; j++) {
						ExportedPackage exportedPackage = exportedPackages[j];
						if (exportedPackage.getName().equals(exportPackage)) {
							String specificationVersion = null;
							if (entry.hasAttribute(Constants.PACKAGE_SPECIFICATION_VERSION)) {
								specificationVersion = entry.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION);
							}

							String versionAttribute = null;
							if (entry.hasAttribute(Constants.VERSION_ATTRIBUTE)) {
								versionAttribute = entry.getAttributeValue(Constants.VERSION_ATTRIBUTE);
							}
							
							if (specificationVersion != null && versionAttribute != null && !specificationVersion.equals(versionAttribute)) {
								throw new Exception(new StringBuilder(bundle.toString()).append(" exports a Package(name=").append(exportPkgName).append(") with specification-version attribute not equal to version attribute.").toString());
							}

							Version version;
							if (versionAttribute != null) {
								version = Version.parseVersion(versionAttribute);
							} else if (specificationVersion != null) {
								version = Version.parseVersion(specificationVersion);
							} else {
								/*
								 * {@link ExportedPackage}
								 */
								version = Version.emptyVersion;
							}

							if (!version.equals(exportedPackage.getVersion())) {
								throw new Exception(new StringBuilder(bundle.toString()).
										append(" exports Package(symbolicName=").
										append(exportPkgName).
										append(",version=").
										append(version).
										append(") in spite of Host Export-Package declaration.").toString());									
								
							}
						}
					}
				}
			}
		}
	}

	
	/* 
	 * JavaDoc  Add to the graph any bundle that is wired to a package that is currently
	 * exported by a bundle in the graph. The graph is fully constructed when there is no
	 * bundle outside the graph that is wired to a bundle in the graph. The
	 * graph may contain UNINSTALLED bundles that are currently still exporting packages.
	 */
	private void addWiredBundles(List graph, Bundle bundle) {
		if (getBundleType(bundle) == BUNDLE_TYPE_FRAGMENT) {
			Bundle[] hosts = getHosts(bundle);
			addWiredBundles(graph, hosts[0]);
		} else {
			if (!graph.contains(bundle)) {
				graph.add(bundle);
				
				Bundle[] fragments = getFragments(bundle);
				if (fragments != null) {
					for (int i = 0; i < fragments.length; i++) {
						graph.add(fragments[i]);
					}
				}
				
				RequiredBundle[] requiredBundles = getRequiredBundles(bundle.getSymbolicName());
				if (requiredBundles != null) {
					for (int i = 0; i < requiredBundles.length; i++) {
						RequiredBundle requiredBundle = requiredBundles[i];
						
						addWiredBundles(graph, requiredBundle.getBundle());
						
						Bundle[] requiringBundles = requiredBundle.getRequiringBundles();
						for (int j = 0; j < requiringBundles.length; j++) {
							Bundle requiringBundle = requiringBundles[j];
							
							addWiredBundles(graph, requiringBundle);
						}
					}
				}
			}
			
			ExportedPackage[] exportedPackages = getExportedPackages(bundle);
			
			if (exportedPackages != null) {
				for (int k = 0; k < exportedPackages.length; k++) {
					ExportedPackage exportedPackage = exportedPackages[k];

					Bundle[] importingBundles = exportedPackage.getImportingBundles();
					
					if (importingBundles != null) {
						for (int l = 0; l < importingBundles.length; l++) {
							Bundle importingBundle = importingBundles[l];
							addWiredBundles(graph, importingBundle);
						}
					}
				}
			}
		}
	}

	public void refreshPackages(final Bundle[] bundles) {
		/*
		 * 4.8.1.1
		 */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(framework, AdminPermission.RESOLVE));		
		}
		
		/* 
		 * JavaDoc  This method returns to the caller immediately and then performs the
		 * following steps on a separate thread:
		 */
//		Executors.newCachedThreadPool(PackageAdmin.class.getName(), 1).execute(new Runnable() {
		executor.execute(new Runnable() {
			public void run() {
				BundleContext context = framework.getBundleContext();

				Bundle[] bundles0 = bundles;
				
				if (bundles0 == null) {
					/* 
					 * JavaDoc  If no bundles are specified, this method will update or remove any
					 * packages exported by any bundles that were previously updated or
					 * uninstalled since the last call to this method.
					 */
					synchronized (changedBundlesLock) {
						bundles0 = (Bundle[]) changedBundles.toArray(new Bundle[changedBundles.size()]);
					}
				}
				
				List graph = new ArrayList();

				/* 
				 * JavaDoc  Add to the graph any bundle that is wired to a package that is currently
				 * exported by a bundle in the graph. The graph is fully constructed when there is no
				 * bundle outside the graph that is wired to a bundle in the graph. The
				 * graph may contain UNINSTALLED bundles that are currently still exporting packages.
				 */
				for (int i = 0; i < bundles0.length; i++) {
					addWiredBundles(graph, bundles0[i]);
				}
				
				Bundle bundle0 = null;
				try {
					
					List activeBundles = new ArrayList();
					
					List uninstalledBundles = new ArrayList();
					
					for (int i = 0; i < graph.size(); i++) {
						bundle0 = (Bundle) graph.get(i);
						
						if (getBundleType(bundle0) != BUNDLE_TYPE_FRAGMENT) {
							framework.unresolveBundle(bundle0);
							
							((HostBundle) bundle0).setBundleClassLoader(null);
						}
						
						/* 
						 * JavaDoc  2 Each bundle in the graph that is in the ACTIVE state
						 * will be stopped as described in the Bundle.stop method.
						 */
						
						if (bundle0.getState() == Bundle.ACTIVE) {							
							activeBundles.add(bundle0);
							
							bundle0.stop(Bundle.STOP_TRANSIENT);
						}

//						if (getBundleType(bundle0) != BUNDLE_TYPE_FRAGMENT) {
//							framework.removeBundleClassLoader(bundle0);
//						}
						
						/* 
						 * JavaDoc  3 Each bundle in the graph that is in the RESOLVED
						 * state is unresolved and thus moved to the INSTALLED state.
						 * The effect of this step is that bundles in the graph are no longer
						 * RESOLVED.
						 */
						
						if (bundle0.getState() == Bundle.RESOLVED) {
							if (getBundleType(bundle0) == BUNDLE_TYPE_FRAGMENT) {
								Bundle[] hosts = getHosts(bundle0);
								if (hosts != null && framework.equals(hosts[0])) {
									/*
									 * 3.15  4 If a RESOLVED extension bundle is refreshed then the Framework must
									 * shutdown; the host VM must terminate and framework must be relaunched.
									 */
									framework.update();
								}
							}
							
							((AbstractBundle) bundle0).setState(Bundle.INSTALLED);
							// XXX out of specs
							((AbstractBundle) bundle0).stale = false;

							/*
							 * 4.6.1  When a set of bundles are refreshed using the Package
							 * Admin API then each bundle in the set must have an UNRESOLVED
							 * BundleEvent published. The UNRESOLVED BundleEvent must be
							 * published after all the bundles in the set have been stopped and, in
							 * the case of a synchronous bundle listener, before any of the bundles in
							 * the set are re-started. RESOLVED and UNRESOLVED do not have to
							 * paired.
							 */
							BundleEvent bundleEvent = new BundleEvent(BundleEvent.UNRESOLVED, bundle0);
							framework.postBundleEvent(bundleEvent);
						}
						
						else if (bundle0.getState() == Bundle.UNINSTALLED) {
							uninstalledBundles.add(bundle0);
						}
					}

					Iterator it;
					
					/* 
					 * JavaDoc  4 Each bundle in the graph that is in the UNINSTALLED
					 * state is removed from the graph and is now completely removed from the
					 * Framework.
					 */
					it = uninstalledBundles.iterator();
					while (it.hasNext()) {
						framework.remove((Bundle) it.next());
					}
					
					/* 
					 * JavaDoc  5 Each bundle in the graph that was in the ACTIVE state
					 * prior to Step 2 is started as described in the Bundle.start
					 * method, causing all bundles required for the restart to be resolved.
					 */
					
					it = activeBundles.iterator();
					while (it.hasNext()) {
						((Bundle) it.next()).start(Bundle.START_TRANSIENT);
					}
					
					/* 
					 * 6 A framework event of type FrameworkEvent.PACKAGES_REFRESHED is fired.
					 */
					FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, context.getBundle(), null);
					framework.postFrameworkEvent(frameworkEvent);
				} catch (BundleException e) {
					/* 
					 * For any exceptions that are thrown during any of these steps, a
					 * FrameworkEvent of type ERROR is fired
					 * containing the exception. The source bundle for these events should be
					 * the specific bundle to which the exception is related. If no specific
					 * bundle can be associated with the exception then the System Bundle must
					 * be used as the source bundle for the event.
					 */
					FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, bundle0, e);
					framework.postFrameworkEvent(frameworkEvent);
				}
			}
		});
	}

	void unresolveBundle(Bundle bundle) {
		unwireExportedPackages(bundle);
		
		unwireRequiredBundles(bundle);
	}
}
