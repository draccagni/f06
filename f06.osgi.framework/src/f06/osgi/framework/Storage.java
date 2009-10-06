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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.jar.Manifest;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;
import org.osgi.service.permissionadmin.PermissionInfo;

import f06.util.ArrayUtil;
import f06.util.CaseSensitiveDictionary;
import f06.util.IOUtil;
import f06.util.ManifestEntry;
import f06.util.ManifestUtil;

/*
 * 4.4.2 Persistent Storage
 */
class Storage {
	
	private final static String TEMP_FOLDER = "temp";
	private final static String BUNDLES_FOLDER = "bundles";
	private final static String BUNDLE_CACHE_FOLDER = "cache";
	private final static String BUNDLE_DATA_FOLDER = "data";
	private final static String BUNDLE_INFO_FILE = "bundleinfo";
	private final static String BUNDLE_PERMISSIONS_FILE = "bundlepolicy";
	private final static String BUNDLE_MANIFEST_FILE = "bundlemanifest";
	final static String BUNDLE_FILE = "bundlefile";

	static class BundleInfo implements Serializable {

		/**
		 * 
		 */
		private static final long serialVersionUID = 7328644951710692800L;

		private long bundleId;

		private volatile long lastModified;

		private String location;

		private volatile int autostartSetting;

		private volatile int startLevel;

		private volatile boolean removalPending;

		private transient Dictionary headers;

		private transient File cache;

		BundleInfo(long bundleId, String location, long lastModified, int startLevel) {
			this.bundleId = bundleId;

			this.location = location;

			this.removalPending = false;

			this.lastModified = lastModified;

			this.autostartSetting = AbstractBundle.STOPPED;
			
			this.startLevel = startLevel;
		}

		long getBundleId() {
			return bundleId;
		}

		long getLastModified() {
			return lastModified;
		}

		String getLocation() {
			return location;
		}

		int getAutostartSetting() {
			return autostartSetting;
		}

		void setAutostartSetting(int autostartSetting) {
			this.autostartSetting = autostartSetting;
		}

		int getStartLevel() {
			return startLevel;
		}

		void setStartLevel(int startLevel) {
			this.startLevel = startLevel;
		}

		public Dictionary getHeaders() {
			return headers;
		}

		public void setHeaders(Dictionary headers) {
			this.headers = headers;
		}

		public void setRemovalPending(boolean removalPending) {
			this.removalPending = removalPending;
		}

		public boolean isRemovalPending() {
			return removalPending;
		}

		public void setCache(File cache) {
			this.cache = cache;
		}

		public File getCache() {
			return cache;
		}
	}

	private Bundle[] bundles;

	private File storagePath;

	private Framework framework;

	private Map bundleInfosByBundle;

	private Map bundleFoldersById;

	private Map dataFoldersByBundle;

	private Object bundlesLock;

	private Object bundleInfosLock;

    private Map permissionInfosByLocation;
    
    private PermissionInfo[] defaultPermissions;
	
	private Object permissionsLock;
	
    private Map classPathsByBundle;
	
	
	private static volatile boolean firstInit = true;

	public Storage(Framework framework) {
		this.framework = framework;

		bundleFoldersById = new HashMap();

		dataFoldersByBundle = new HashMap();

		/*
		 * Persistent storage location
		 */

		storagePath = new File(framework.getProperty(Constants.FRAMEWORK_STORAGE));
		if (!storagePath.exists()) {
			storagePath.mkdirs();
		}
		
		/*
		 * This RFC describes a proposed specification for a Command processing interface for the OSGi Framework.
		 */
		
		String storageClean = framework.getProperty(Constants.FRAMEWORK_STORAGE_CLEAN);
		if (storageClean == null) {
			storageClean = "none";
		}
		
		if (firstInit && storageClean.equals("onFirstInit")) {
			cleanStorage();
		}
		
		firstInit = false;

		this.bundlesLock = new Object();

		this.bundleInfosLock = new Object();
		
		this.permissionInfosByLocation = new HashMap();
		
		this.permissionsLock = new Object();
		
		this.classPathsByBundle = new HashMap();
	}
	
	private void cleanStorage() {
		try {
			IOUtil.delete(storagePath);
		} catch (IOException e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}
	
	void fetchBundles() throws Exception {
		synchronized (bundlesLock) {
			fetchSystemBundle();
			
			File[] files = getBundlesFolder().listFiles();

			if (files != null) {
				for (int i = 0; i < files.length; i++) {
					if (files[i].isDirectory()) {
						Bundle bundle = fetchBundle(files[i]);
						if (bundle != null) {
							bundles = (Bundle[]) ArrayUtil.add(this.bundles, bundle);
						}
					}
				}

				Arrays.sort(bundles, new Comparator() {
					public int compare(Object o1, Object o2) {
						return ((Bundle) o1).getBundleId() > ((Bundle) o1).getBundleId() ? 1 : -1;
					}
				});
			}
		}
	}

	private File getBundleFolder(long bundleId) {
		Long key = Long.valueOf(bundleId);

		File bundleFolder = (File) bundleFoldersById.get(key);
		if (bundleFolder == null) {
			bundleFolder = new File(getBundlesFolder(), Long.toString(bundleId));
			bundleFoldersById.put(key, bundleFolder);
		}

		return bundleFolder;
	}

	private File getDefaultPermissionsFile() {
		File permissionsFile = new File(storagePath, "default.policy");

		return permissionsFile;
	}

	private File getBundlePermissionsFile(Bundle bundle) {
		File permissionsFile = new File(getBundleFolder(bundle.getBundleId()),
				BUNDLE_PERMISSIONS_FILE);

		return permissionsFile;
	}

	private File getCache(Bundle bundle) {
		File rootCache = new File(getBundleFolder(bundle.getBundleId()), "ver");
		if (!rootCache.exists()) {
			rootCache.mkdirs();
		}

		Version version = bundle.getVersion();
		File cache = new File(rootCache, version.toString());

		cache.mkdirs();

		return cache;
	}

	private File createNewCache(long bundleId, Version version) {
		File rootCache = new File(getBundleFolder(bundleId), BUNDLE_CACHE_FOLDER);
		if (!rootCache.exists()) {
			rootCache.mkdirs();
		}

		File cache = new File(rootCache, version.toString());

		cache.mkdirs();

		return cache;
	}
	
	public synchronized File getTempFolder() {
		File temp = new File(storagePath, TEMP_FOLDER);
		if (!temp.exists()) {
			temp.mkdirs();
		}
		
		return temp;
	}

	public synchronized File getBundlesFolder() {
		File temp = new File(storagePath, BUNDLES_FOLDER);
		if (!temp.exists()) {
			temp.mkdirs();
		}
		
		return temp;
	}

	public synchronized File getDataFile(Bundle bundle, String fileName) {
		File data = (File) dataFoldersByBundle.get(bundle);
		if (data == null) {
			data = new File(getBundleFolder(bundle.getBundleId()), BUNDLE_DATA_FOLDER);
			dataFoldersByBundle.put(bundle, data);

			data.mkdirs();
		}

		File dataFile = new File(data, fileName);

		File parentFile = dataFile.getParentFile();
		if (!parentFile.exists()) {
			parentFile.mkdirs();
		}

		return dataFile;
	}

	private BundleURLClassPath createBundleURLClassPath(Bundle bundle, Version version,
			File bundleFile, File cache, boolean alreadyCached)
			throws Exception {
		String bundleClassPath = (String) bundle.getHeaders().get(
				Constants.BUNDLE_CLASSPATH);

		/*
		 * 3.8.1
		 */

		if (bundleClassPath == null) {
			bundleClassPath = ".";
		}

		ManifestEntry[] entries = ManifestEntry.parse(bundleClassPath);

		String[] classPaths = new String[0];

		for (int i = 0; i < entries.length; i++) {
			String classPath = entries[i].getName();
			if (classPath.startsWith("/")) {
				classPath = classPath.substring(1);
			}

			if (classPath.endsWith(".jar")) {
				try {
					File file = new File(cache, classPath);
					if (!alreadyCached) {
						file.getParentFile().mkdirs();

						String url = new StringBuilder("jar:").append(
								bundleFile.toURI().toURL().toString()).append(
								"!/").append(classPath).toString();

						OutputStream os = new FileOutputStream(file);

						InputStream is = new URL(url).openStream();

						IOUtil.copy(is, os);

						is.close();

						os.close();
					} else {
						if (!file.exists()) {
							throw new IOException(new StringBuilder(
									"classpath ").append(classPath).append(
									" not found").toString());
						}
					}
				} catch (IOException e) {
					/*
					 * 3.8.1 The Framework must ignore a target in the
					 * Bundle-Classpath header if the target (directory or JAR
					 * file) cannot be located when it is needed, which can
					 * happen at any time after the bundle is resolved. However,
					 * in this case the Framework should publish a Framework
					 * Event of type INFO with an appropriate message for each
					 * entry that cannot be located.
					 */

					FrameworkEvent frameworkEvent = new FrameworkEvent(
							FrameworkEvent.INFO, bundle, e);
					framework.postFrameworkEvent(frameworkEvent);

					continue;
				}
			}
			
			classPaths = (String[]) ArrayUtil.add(classPaths, classPath);					
		}

		if (!alreadyCached) {
			String bundleNativeCode = (String) bundle.getHeaders().get(
					Constants.BUNDLE_NATIVECODE);

			if (bundleNativeCode != null) {
				entries = ManifestEntry.parse(bundleNativeCode);
				for (int i = 0; i < entries.length; i++) {
					ManifestEntry entry = entries[i];
					String libPath = entry.getName();

					String url = new StringBuilder("jar:")
						.append(bundleFile.toURI().toURL().toString())
						.append("!/")
						.append(libPath).toString();

					File file = new File(cache, libPath);
					file.getParentFile().mkdirs();
					OutputStream os = new FileOutputStream(file);
					InputStream is = new URL(url).openStream();

					IOUtil.copy(is, os);

					is.close();

					os.close();
				}
			}
		}

		BundleURLClassPath urlClassPath = new BundleURLClassPathImpl(bundle,
				version, classPaths, cache);

		return urlClassPath;
	}

	Bundle install(String location, InputStream is) throws BundleException {
		synchronized (bundlesLock) {
			/*
			 * 3.16.1 (...) The installer of an extension bundle must have
			 * AdminPermission[ <extension bundle>,EXTENSIONLIFECYCLE] to
			 * install an extension bundle.
			 */
			SecurityManager securityManager = System.getSecurityManager();
			if (securityManager != null) {
				securityManager.checkPermission(new AdminPermission(
						new StringBuilder("(location=").append(location).append(")").toString(),
						org.osgi.framework.AdminPermission.EXTENSIONLIFECYCLE));
			}

			long bundleId = getNextBundleId();

			AbstractBundle bundle = null;

			try {
				if (is == null) {
					URL url = new URL(location);
					is = url.openStream();
				}

				File temp = new File(getTempFolder(), Long.toString(System.currentTimeMillis()));

				OutputStream os;
				
				os = new FileOutputStream(temp);
				IOUtil.copy(is, os);
				os.close();

				is.close();

				/*
				 * instead of JarInputStream.getManifest() who aspects
				 * MANIFEST.MF as the first entry
				 */
				Manifest manifest = ManifestUtil.getJarManifest(new FileInputStream(temp));
				Dictionary headers = ManifestUtil.toDictionary(manifest);

				Version version = Version.parseVersion((String) headers.get(Constants.BUNDLE_VERSION));
				File cache = createNewCache(bundleId, version);

				File manifestFile = new File(cache, BUNDLE_MANIFEST_FILE);
				os = new FileOutputStream(manifestFile);
				ManifestUtil.storeManifest(headers, os);
				os.close();

				/*
				 * check if it is an boot class path extension bundle, in that
				 * case install it
				 */

				if (isBundleInstalled((String) headers.get(Constants.BUNDLE_SYMBOLICNAME))) {
					throw new BundleException(new StringBuilder(
							"Bundle(location=").append(location).append(
							") already installed.").toString());
				}

				ManifestEntry[] entries = ManifestEntry.parse(headers.get(Constants.FRAGMENT_HOST));
				if (entries != null) {
					/*
					 * 3.15.2 Class Path Treatment
					 * 
					 * A boot class path extension bundle’s JAR file must be
					 * appended to the boot class path of the host VM. A
					 * framework extension bundle’s JAR is appended to the class
					 * path of the Framework. Extension bundles must be appended
					 * to their class path in the order in which the extension
					 * bundles are installed: that is, ascending bundle ID
					 * order. How a framework configures itself or the boot
					 * class path to append the extension bundle’s JAR is
					 * implementation specific. In some execution environments,
					 * it may be impossible to support extension bundles. In
					 * such environments, the Framework must throw a
					 * BundleException when such an extension bundle is
					 * installed. The resulting Bundle Exception must have a
					 * cause of type UnsupportedOperationException.
					 */

					if (entries[0].hasAttribute("extension")) {
						String extension = entries[0].getAttributeValue("extension");
						if (extension.equals("bootclasspath")) {
							String symbolicName = entries[0].getName();

							if (
									!symbolicName.equals(framework.getSymbolicName()) &&
									!symbolicName.equals(Constants.SYSTEM_BUNDLE_SYMBOLICNAME)) {
								throw new BundleException(
										new StringBuilder(
												"Trying to install a fragment Bundle(location=")
												.append(location)
												.append(") with extension 'bootclasspath' but host is not System Bundle.")
												.toString(),
										new UnsupportedOperationException());
							}
						}
					}
				}

				/*
				 * 3.3.1 (...) The org.osgi.framework.executionenvironment
				 * property from BundleContext.getProperty(String) must contain
				 * a comma-separated list of execution environment names
				 * implemented by the Framework. This property is defined as
				 * volatile. A Framework implementation must not cache this
				 * information because bundles may change this system property
				 * at any time. The purpose of this volatility is testing and
				 * possible extension of the execution environments at run-time.
				 */
				String requiredEE = (String) headers.get(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT);
				if (requiredEE != null) {
					BundleContext context = framework.getBundleContext();
					String ee = context.getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);

					if (!ee.contains(requiredEE)) {
						throw new BundleException(new StringBuilder("Bundle(location=")
							.append(location)
							.append(")  requires an unsopperted execution environment (=")
							.append(requiredEE)
							.append(").").toString());
					}
				}

				/*
				 * Create Bundle instance
				 */

				if (FrameworkUtil.isFragmentHost(headers)) {
					bundle = new FragmentBundle(framework);
				} else {
					bundle = new HostBundle(framework);
				}

				File bundlefile = new File(cache, Storage.BUNDLE_FILE);
				temp.renameTo(bundlefile);

				/*
				 * Create BundleInfo instance
				 */
				long lastModified = bundlefile.lastModified();
				BundleInfo info = new BundleInfo(bundleId, location, lastModified, framework.getInitialBundleStartLevel());
				info.setHeaders(headers);
				info.setCache(cache);	
				storeBundleInfo(info);
				
				bundleInfosByBundle.put(bundle, info);

				/*
				 * Create BundleURLClassPath instance
				 */
				BundleURLClassPath classPath = createBundleURLClassPath(bundle, version,
						bundlefile, cache, false);
				classPathsByBundle.put(
						bundle,
						new BundleURLClassPath[] { classPath });

				synchronized (bundlesLock) {
					bundles = (Bundle[]) ArrayUtil.add(bundles, bundle);
				}

				return bundle;
			} catch (Exception e) {
				if (bundle != null) {
					File bundleFolder = getBundleFolder(bundleId);
					try {
						IOUtil.delete(bundleFolder);
					} catch (IOException e1) {
					}
				}

				e.printStackTrace();
				throw new BundleException(e.getMessage(), e);
			}
		}
	}

	private BundleInfo getBundleInfo(Bundle bundle) {
		synchronized (bundleInfosLock) {
			BundleInfo info = (BundleInfo) bundleInfosByBundle.get(bundle);
			
			return info;
		}
	}
	
	String getLibraryPath(Bundle bundle, String libfilename) throws IOException {
		BundleInfo info = getBundleInfo(bundle);

		File file = new File(info.getCache(), libfilename);

		return file.getCanonicalPath();
	}

	BundleURLClassPath getBundleURLClassPath(Bundle bundle, Version version) {
		BundleURLClassPath[] classPaths = (BundleURLClassPath[]) classPathsByBundle.get(bundle);
		for (int i = 0; i < classPaths.length; i++) {
			if (classPaths[i].getVersion().equals(version)) {
				return classPaths[i];
			}
		}
		
		return null;
	}

	private Bundle getBundle(String location) {
		synchronized (bundlesLock) {
			for (int i = 0; i < bundles.length; i++) {
				Bundle bundle = bundles[i];
				if (bundle.getLocation().equals(location)) {
					return bundle;
				}
			}

			return null;
		}
	}

	private boolean isBundleInstalled(String symbolicName) {
		Bundle[] bundles = getBundles();
		for (int i = 0; i < bundles.length; i++) {
			if (bundles[i].getSymbolicName().equals(symbolicName)) {
				return true;
			}
		}
		
		return false;
	}

	void setRemovalPending(Bundle bundle) {
		try {
			BundleInfo info = getBundleInfo(bundle);

			info.setRemovalPending(true);

			storeBundleInfo(info);
		} catch (IOException e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}

	void remove(Bundle bundle) {
		synchronized (bundlesLock) {
			bundleFoldersById.remove(Long.valueOf(bundle.getBundleId()));

			bundles = (Bundle[]) ArrayUtil.remove(bundles, bundle);

			/*
			 * uninstalled / old version(s) bundle(s) file(s) will be physically
			 * remove on next JVM startup
			 */
		}
	}

	Bundle getBundle(long id) {
		synchronized (bundlesLock) {
			for (int i = 0; i < bundles.length; i++) {
				Bundle bundle = bundles[i];
				if (bundle.getBundleId() == id) {
					return bundle;
				}
			}

			return null;
		}
	}

	Bundle[] getBundles() {
		synchronized (bundlesLock) {
			return (Bundle[]) ArrayUtil.copyOf(this.bundles,
					this.bundles.length);
		}
	}

	long getBundleId(Bundle bundle) {
		synchronized (bundlesLock) {
			BundleInfo info = getBundleInfo(bundle);

			return info.getBundleId();
		}
	}

	long getLastModified(Bundle bundle) {
		BundleInfo info = getBundleInfo(bundle);

		return info.getLastModified();
	}

	String getLocation(Bundle bundle) {
		synchronized (bundlesLock) {
			BundleInfo info = getBundleInfo(bundle);

			return info.getLocation();
		}
	}

	Dictionary getHeaders(Bundle bundle) {
		synchronized (bundlesLock) {
			BundleInfo info = getBundleInfo(bundle);

			Dictionary headers = info.getHeaders();

			return headers;
		}
	}

	void update(Bundle bundle, InputStream is) throws BundleException {
		synchronized (bundlesLock) {
			File newCache = null;

			BundleInfo currentInfo = getBundleInfo(bundle);

			Version currentVersion = bundle.getVersion();
			try {
				File temp = new File(getTempFolder(), Long.toString(System.currentTimeMillis()));

				OutputStream os;
				
				os = new FileOutputStream(temp);
				IOUtil.copy(is, os);
				os.close();

				is.close();

				/*
				 * instead of JarInputStream.getManifest() who aspects
				 * MANIFEST.MF as the first entry
				 */
				Manifest manifest = ManifestUtil.getJarManifest(new FileInputStream(temp));
				Dictionary newHeaders = ManifestUtil.toDictionary(manifest);
				Version newVersion = Version.parseVersion((String) newHeaders.get(Constants.BUNDLE_VERSION));

				if (newVersion.compareTo(currentVersion) > 0) {
					long newBundleId = bundle.getBundleId();
					newCache = createNewCache(newBundleId, newVersion);

					File bundlefile = new File(newCache, BUNDLE_FILE);

					File manifestFile = new File(newCache, BUNDLE_MANIFEST_FILE);
					os = new FileOutputStream(manifestFile);
					ManifestUtil.storeManifest(newHeaders, os);
					os.close();

					temp.renameTo(bundlefile);

					BundleURLClassPath newClassPath = createBundleURLClassPath(bundle, newVersion, bundlefile, newCache, false);
					
					BundleURLClassPath[] classPaths = (BundleURLClassPath[]) classPathsByBundle.get(bundle);
					classPaths = (BundleURLClassPath[]) ArrayUtil.add(classPaths, newClassPath);
					
					classPathsByBundle.put(bundle, classPaths);

					/*
					 * the newer bundle has been succefully installed: update
					 */
					long newLastModified = bundlefile.lastModified();
					BundleInfo newInfo = new BundleInfo(bundle.getBundleId(),
							bundle.getLocation(), newLastModified, framework.getInitialBundleStartLevel());
					newInfo.setHeaders(newHeaders);
					newInfo.setCache(newCache);
					newInfo.setStartLevel(currentInfo.getStartLevel());
					newInfo.setAutostartSetting(currentInfo.getAutostartSetting());
					storeBundleInfo(newInfo);

					currentInfo.setRemovalPending(true);
					storeBundleInfo(currentInfo);
					
					/*
					 * BundleInfo up-to-date to the last version available
					 */
					bundleInfosByBundle.put(bundle, newInfo);
				}
			} catch (Exception e) {
				if (newCache != null) {
					try {
						IOUtil.delete(newCache);
					} catch (IOException e1) {
						throw new BundleException(e1.getMessage(), e);
					}
				}

				throw new BundleException(e.getMessage(), e);
			}
		}
	}

	private void fetchSystemBundle() throws Exception {
		URL location = Framework.class.getProtectionDomain().getCodeSource().getLocation();
	
		File file = new File(URLDecoder.decode(location.getFile(), "UTF-8"));
		long lastModified = file.lastModified();
		
		BundleInfo info = new BundleInfo(0, Constants.SYSTEM_BUNDLE_LOCATION,
				lastModified, 1);
		Dictionary headers = new CaseSensitiveDictionary(true);
		
		headers.put(Constants.BUNDLE_SYMBOLICNAME,
				Constants0.SYSTEM_BUNDLE_SYMBOLICNAME);
		headers.put(Constants.BUNDLE_NAME, Constants0.SYSTEM_BUNDLE_NAME);
		headers.put(Constants.BUNDLE_VERSION, Constants0.SYSTEM_BUNDLE_VERSION);
		headers.put(Constants.BUNDLE_DOCURL, Constants0.SYSTEM_BUNDLE_DOCURL);
		headers.put(Constants.BUNDLE_VENDOR, Constants0.SYSTEM_BUNDLE_VENDOR);
		headers.put(Constants.BUNDLE_COPYRIGHT,
				Constants0.SYSTEM_BUNDLE_COPYRIGHT);
		
		/*
		 * 3.8.5 (...) The system property
		 * 
		 * org.osgi.framework.system.packages
		 * 
		 * contains the export packages descriptions for the system bundle. This
		 * property employs the standard Export-Package manifest header syntax:
		 * org.osgi.framework.system.packages ::= package-description ( ','
		 * package-description )*
		 */
		
		StringBuilder exportPackages = new StringBuilder();
		
		String systemPackages = framework.getProperty(Constants.FRAMEWORK_SYSTEMPACKAGES);
		exportPackages.append(systemPackages);
		
		/*
		 * This RFC describes a proposed specification for a Command processing interface for the OSGi Framework.
		 * 
		 * org.osgi.framework.system.packages.extra
		 * 
		 * Set by the configurator. Framework environment property
		 * identifying extra packages which the Frameworksystem bundle
		 * must export from the current execution environment.
		 * This property is useful for configuring extra system packages in
		 * addition to the system packages calculated by the framework.
		 */
		String systemPackagesExtra = framework.getProperty("org.osgi.framework.system.packages.extra");
		if (systemPackagesExtra != null) {
			exportPackages.append(",").append(systemPackagesExtra);
		}

		headers.put(Constants.EXPORT_PACKAGE, exportPackages.toString());
		
		info.setAutostartSetting(AbstractBundle.STARTED_EAGER);
		info.setHeaders(headers);
		
		bundleInfosByBundle = new WeakHashMap();
		bundleInfosByBundle.put(framework, info);
		classPathsByBundle.put(
				framework,
				new BundleURLClassPath[] {
						new SystemBundleURLClassPath(framework)
				});
		
		bundles = new Bundle[] { framework };
	}

	private Bundle fetchBundle(File bundleFolder) {
		try {
			File[] caches = new File(bundleFolder, BUNDLE_CACHE_FOLDER).listFiles();
			if (caches == null) {
				return null;
			}
			
			Arrays.sort(caches, new Comparator() {
				public int compare(Object arg0, Object arg1) {
					return - Version.parseVersion(((File) arg0).getName()).compareTo(Version.parseVersion(((File) arg1).getName()));
				}
			});
			

			File cache = caches[0];
			File bundleInfoFile = new File(cache, BUNDLE_INFO_FILE);
			BundleInfo info = fetchBundleInfo(bundleInfoFile);
			/*
			 * If the bundle has been uninstalled, remove it completly from the
			 * storage and return null.
			 */
			if (info == null || info.isRemovalPending()) {
				IOUtil.delete(cache);

				return null;
			}
			
			/*
			 * Remove old versions
			 */
			for (int i = 1; i < caches.length; i++) {
				IOUtil.delete(cache);
			}
			
			info.setCache(cache);
			

			Manifest manifest = ManifestUtil.getJarManifest(new FileInputStream(new File(cache, BUNDLE_FILE)));
//			File manifestFile = new File(cache, BUNDLE_MANIFEST_FILE);
//			Manifest manifest = new Manifest();
			Dictionary headers = ManifestUtil.toDictionary(manifest);

			info.setHeaders(headers);

			Version lastVersion = Version.parseVersion((String) headers.get(Constants.BUNDLE_VERSION));
			
			/*
			 * Clean up previous versions
			 */
			
			for (int i = 1; i < caches.length; i++) {
				IOUtil.delete(caches[i]);
			}

			AbstractBundle bundle = null;
			if (FrameworkUtil.isFragmentHost(headers)) {
				bundle = new FragmentBundle(framework);
			} else {
				bundle = new HostBundle(framework);
			}

			bundleInfosByBundle.put(bundle, info);

			File bundleFile = new File(cache, BUNDLE_FILE);

			BundleURLClassPath classPath = createBundleURLClassPath(bundle, lastVersion, bundleFile, cache, true);
			classPathsByBundle.put(
					bundle,
					new BundleURLClassPath[] { classPath });

			if (bundle instanceof HostBundle) {
				int startlevel = info.getStartLevel();
				framework.setBundleStartLevel(bundle, startlevel);
			}

			return bundle;
		} catch (Exception e) {
			e.printStackTrace();
			framework.log(LogService.LOG_ERROR,
					new StringBuilder("A problem occurred processing ").append(
							bundleFolder.getAbsolutePath()).append(" folder.").toString(), e);
			try {
				IOUtil.delete(bundleFolder);
			} catch (IOException e1) {
				framework.log(LogService.LOG_ERROR, e.getMessage(), e);
			}

			return null;
		}
	}

	private long getNextBundleId() {
		long maxBundleId = 0;

		for (int i = 0; i < bundles.length; i++) {
			long bundleId = bundles[i].getBundleId();

			maxBundleId = Math.max(maxBundleId, bundleId);
		}

		return maxBundleId + 1;
	}

	private BundleInfo fetchBundleInfo(File file) throws Exception {
		InputStream is = new FileInputStream(file);

		ObjectInputStream ois = new ObjectInputStream(is);

		BundleInfo info = (BundleInfo) ois.readObject();

		is.close();

		return info;
	}

	/*
	 * StartLevel
	 */
	int getBundleAutostartSetting(Bundle bundle) {
		BundleInfo info = getBundleInfo(bundle);

		return info.getAutostartSetting();
	}

	void setBundleAutostartSetting(Bundle bundle, int autostartSetting) {
		BundleInfo info = getBundleInfo(bundle);
		info.setAutostartSetting(autostartSetting);

		try {
			storeBundleInfo(info);
		} catch (IOException e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}

	public boolean isBundlePersistentlyStarted(Bundle bundle) {
		return getBundleAutostartSetting(bundle) != AbstractBundle.STOPPED;
	}

	void setBundleStartLevel(Bundle bundle, int startlevel) {
		synchronized (bundlesLock) {
			BundleInfo info = getBundleInfo(bundle);
			info.setStartLevel(startlevel);

			try {
				storeBundleInfo(info);
			} catch (IOException e) {
				framework.log(LogService.LOG_ERROR, e.getMessage(), e);
			}
		}
	}
	
	public void storeBundleInfo(BundleInfo info) throws IOException {
		File file = new File(info.getCache(), BUNDLE_INFO_FILE);
		OutputStream os = new BufferedOutputStream(new FileOutputStream(file));

		ObjectOutputStream oos = new ObjectOutputStream(os);
		oos.writeObject(info);
		os.flush();
		os.close();

	}

	/*
	 * PersmissionAdmin
	 */

	PermissionInfo[] getDefaultPermissions() {
		synchronized (permissionsLock) {
			if (defaultPermissions == null) {
				try {
					File permissionFile = getDefaultPermissionsFile();
					if (permissionFile.exists()) {
						defaultPermissions =  fetchPermissions(permissionFile);
					}
				} catch (IOException e) {
					framework.log(LogService.LOG_ERROR, e.getMessage(),	e);
				}
			}
			return defaultPermissions;
		}
	}
	
	public void setPermissions(final String location, final PermissionInfo[] permissions) {
		if (location == null) {
			throw new NullPointerException();
		}

		try {
			synchronized (permissionsLock) {
				/*
				 * 10.4.2.5
				 */
				if (permissions != null) {
					Bundle bundle = getBundle(location);
					File permissionFile = getBundlePermissionsFile(bundle);
					storePermissions(permissionFile, permissions);

					permissionInfosByLocation.put(location, permissions);
				} else {
					framework.removePermissions(location);

					permissionInfosByLocation.remove(location);
				}
			}
		} catch (IOException e) {
		}
	}


	PermissionInfo[] getPermissions(String location) {
		if (location == null) {
			return PermissionAdminImpl.ALL_PERMISSIONS;
		}
		

		try {
			synchronized (permissionsLock) {
				PermissionInfo[] permissions = (PermissionInfo[]) permissionInfosByLocation.get(location);
				if (permissions == null) {
					Bundle bundle = getBundle(location);
					File permissionFile = getBundlePermissionsFile(bundle);
					if (permissionFile.exists()) {
						permissions = fetchPermissions(permissionFile);
						permissionInfosByLocation.put(location, permissions);
					}
				}
				
				return permissions;
			}
		} catch (IOException e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
			return null;
		}
	}

	PermissionInfo[] fetchPermissions(File permissionFile) throws IOException {
		List permissions = new ArrayList();

		BufferedReader reader = new BufferedReader(new FileReader(
				permissionFile));
		String line;
		while ((line = reader.readLine()) != null) {
			permissions.add(new PermissionInfo(line));
		}
		reader.close();

		return (PermissionInfo[]) permissions.toArray(new PermissionInfo[0]);
	}

	void setDefaultPermissions(PermissionInfo[] permissions) {
		synchronized (permissionsLock) {
			try {
				File permissionFile = getDefaultPermissionsFile();

				storePermissions(permissionFile, permissions);

				defaultPermissions = permissions;
			} catch (IOException e) {
				framework.log(LogService.LOG_ERROR, e.getMessage(),	e);
			}
		}
	}
	
	String[] getLocations() {
		synchronized (permissionsLock) {
			return (String[]) permissionInfosByLocation.keySet().toArray(new String[permissionInfosByLocation.size()]);
		}
	}

	private void storePermissions(File permissionFile,
			PermissionInfo[] permissions) throws IOException {
		FileWriter writer = new FileWriter(permissionFile);
		for (int i = 0; i < permissions.length; i++) {
			writer.write(permissions[i].getEncoded());
			writer.write('\n');
		}
		writer.close();
	}

	void removePermissions(String location) throws IOException {
		synchronized (permissionsLock) {
			Bundle bundle = getBundle(location);
			File permissionFile = getBundlePermissionsFile(bundle);
			permissionFile.delete();
			permissionInfosByLocation.remove(location);
		}
	}

	void removeDefaultPermissions() throws IOException {
		File permissionFile = getDefaultPermissionsFile();
		permissionFile.delete();
		defaultPermissions = null;
	}

	public int getBundleStartLevel(Bundle bundle) {
		synchronized (bundlesLock) {
			BundleInfo info = getBundleInfo(bundle);

			return info.getStartLevel();
		}
	}
}
