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
import java.net.ContentHandlerFactory;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandlerFactory;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.AllServiceListener;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServicePermission;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.service.EventHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;
import org.osgi.service.startlevel.StartLevel;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.xml.XMLParserActivator;

import f06.util.ArrayUtil;
import f06.util.CaseSensitiveDictionary;
import f06.util.ManifestEntry;
import f06.util.TextUtil;

/*
 * 4.5  The System Bundle
 * 
 * In addition to normal bundles, the Framework itself is represented as a bundle.
 * The bundle representing the Framework is referred to as the system bundle.
 */

public class Framework extends HostBundle implements org.osgi.framework.launch.Framework {
	
	static class BundleListenerHandler implements EventHandler {
		
		BundleListener listener;
		
		public BundleListenerHandler(BundleListener listener) {
			this.listener = listener;
		}
		
		public void handleEvent(Event event) {
			BundleEvent eventDispatched = (BundleEvent) event.getProperty("event");

			listener.bundleChanged(eventDispatched);
		}
		
		public BundleListener getListener() {
			return listener;
		}
	}

	static class ServiceListenerHandler implements EventHandler {
		
		ServiceListener listener;
		
		public ServiceListenerHandler(ServiceListener listener) {
			this.listener = listener;
		}
		
		public void handleEvent(Event event) {
			ServiceEvent eventDispatched = (ServiceEvent) event.getProperty("event");

			listener.serviceChanged(eventDispatched);
		}
		
		public ServiceListener getListener() {
			return listener;
		}
	}

	static class FrameworkListenerHandler implements EventHandler {
		
		FrameworkListener listener;
		
		public FrameworkListenerHandler(FrameworkListener listener) {
			this.listener = listener;
		}
		
		public void handleEvent(Event event) {
			FrameworkEvent eventDispatched = (FrameworkEvent) event.getProperty("event");

			listener.frameworkEvent(eventDispatched);
		}
		
		public FrameworkListener getListener() {
			return listener;
		}
	}
	
	private Storage storage;

	private ServiceRegistry serviceRegistry;

	private Properties configuration;

	/*
	 * Bundle(s)
	 */

	private Set bootDelegatedPackageNames;

	private Object stopLock;
	
	private FrameworkEvent stopEvent; 

	/*
	 * PackageAdmin
	 */
	
	private PackageAdmin packageAdmin;

	/*
	 * StartLevel
	 */
	
	private StartLevel startLevel;
	
	/*
	 * Events
	 */
	
	private EventDispatcher eventDispatcher;
	
	/*
	 * Log
	 */
	
	protected ServiceTracker logServiceTracker;
	
	public Framework(Map initConfiguration) {
		super(null);
		
		this.framework = this;
		
		this.stopLock = new Object();
		
		/*
		 * 8.2.1  A start level of 0 (zero) is the state in which the Framework has either
		 * not been launched.
		 */
		
		if (initConfiguration == null) {
			this.configuration = new Properties(System.getProperties());
		} else {
			this.configuration = new Properties();
			this.configuration.putAll(initConfiguration);
		}
		
		System.setProperty("org.osgi.vendor.framework", Constants0.FRAMEWORK_VENDOR);

		setState(INSTALLED);
	}
	
	public void init() {
		int state = getState();
		
		if (state == STARTING || state == ACTIVE) {
			throw new IllegalStateException("Cannot init framework already started.");
		}
		
		try {

			/*
			 * JavaDoc  Initialize this Framework. After calling this method, this Framework
			 * must:
			 * 		Be in the STARTING state.
			 * 		Have a valid Bundle Context.
			 * 		Be at start level 0.
			 * 		Have event handling enabled.
			 * 		Have reified Bundle objects for all installed bundles.
			 * 		Have registered any framework services. For example,
			 * 		PackageAdmin, ConditionalPermissionAdmin,
			 * 		StartLevel.
			 */
			this.serviceRegistry = new ServiceRegistry(this);

			/*
			 * Create System Bundle context
			 */
			BundleContext context = createBundleContext(Framework.this);
			
			setBundleContext(context);

			this.logServiceTracker = new ServiceTracker(context, LogService.class.getName(), null);

			initPersistentStorage();
			
			initBootDelegatedPackageNames();

			eventDispatcher = new EventDispatcher(context);
			
			this.logServiceTracker.open();
			
			/*
			 * Framework Services 
			 */
			
			/* serviceRegistration = */context.registerService( 
					new String[] { EventAdmin.class.getName() }, 
					new EventAdminImpl(context), 
					null);
			
			/*
			 * StartLevel service registration
			 */
			
			startLevel = new StartLevelImpl(context);

			/* serviceRegistration = */context.registerService( 
				    new String[] { StartLevel.class.getName() }, 
				    startLevel, 
					null);
			
			/*
			 * PackageAdmin service registration
			 */
			
			packageAdmin = new PackageAdminImpl(context);

			/* serviceRegistration = */context.registerService( 
					new String[] { PackageAdmin.class.getName() }, 
					packageAdmin, 
					null);

			resolve();

			/*
			 * 10.1.3  The Permission Admin service is registered by the Framework’s system bundle
			 * under the org.osgi.service.permissionadmin.PermissionAdmin interface.
			 */
			
			/* ServiceRegistration serviceRegistration = */ context.registerService( 
					new String[] { PermissionAdmin.class.getName() }, 
					new PermissionAdminImpl(context), 
					null);		
			
			/*
			 * 11.3 Framework Procedures
			 * 
			 * The OSGi Framework must register a java.net.URLStreamHandlerFactory object and a 
			 * java.net.ContentHandlerFactory object with the java.net.URL.setURLStreamHandlerFactory
			 * and java.net.URLConnection.setContentHandlerFactory methods, respectively.
			 */		
		    URL.setURLStreamHandlerFactory(
		    		new URLStreamHandlerFactoryImpl(context)
		    	);

		    URLConnection.setContentHandlerFactory(
		    		new ContentHandlerFactoryImpl(context)
		    	);
			
			Dictionary d = new CaseSensitiveDictionary(false);
			d.put(URLConstants.URL_HANDLER_PROTOCOL, new String[] { "bundle" });
			
			/* ServiceRegistration serviceRegistration = */context.registerService( 
					new String[] { URLStreamHandlerService.class.getName() }, 
					new BundleURLStreamHandlerService(context), 
					d);		

			setState(Bundle.STARTING);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void initPersistentStorage() throws Exception {
		this.storage = new Storage(this);
		this.storage.fetchBundles();
	}
	
	/*
	 * ClassLoader(s)
	 */
	
	private void initBootDelegatedPackageNames() {
	
		bootDelegatedPackageNames = new TreeSet(new Comparator() {
			public int compare(Object o1, Object o2) {
				String s = (String) o1;
				String ws = (String) o2;
	
				return TextUtil.wildcardCompare(ws, s);
			}
		});
	
		BundleContext context = getBundleContext();
		
		String frameworkBootDelegation = context.getProperty(Constants.FRAMEWORK_BOOTDELEGATION);
		if (frameworkBootDelegation != null) {
			try {
				ManifestEntry[] entries = ManifestEntry.parse(frameworkBootDelegation);
				for (int i = 0; i < entries.length; i++) {
					bootDelegatedPackageNames.add(entries[i].getName());
				}
			} catch (Exception e) {
				log(LogService.LOG_ERROR, e.getMessage(), e);
			}
		}		
	}

	public FrameworkEvent waitForStop(long timeout) {
		if (timeout < 0L) {
			throw new IllegalArgumentException(new StringBuilder("timeout cannot be negative (").append(timeout).append(")").toString());
		}
		
		if ((getState() & STARTING | ACTIVE | STOPPING) == 0) {
			return new FrameworkEvent(FrameworkEvent.INFO, framework, null);
		}
		
		BundleContext context = getBundleContext();
		
		/*
		 * JavaDoc
		 * 
		 *         FrameworkEvent.INFO - This method has timed out
		 *         and returned before this Framework has stopped.
		 */
		stopEvent = new FrameworkEvent(FrameworkEvent.INFO, framework, null);
		
		context.addFrameworkListener(new FrameworkListener() {
			public void frameworkEvent(FrameworkEvent event) {
				switch (event.getType()) {
				/*
				 * JavaDoc
				 * 
				 *         FrameworkEvent.STOPPED - This Framework has been stopped.
				 */
				case FrameworkEvent.STOPPED:
				/*
				 * JavaDoc
				 * 
				 *         FrameworkEvent.STOPPED_BOOTCLASSPATH_MODIFIED - This Framework 
				 *         has been stopped and a bootclasspath extension bundle has been 
				 *         installed or updated. The VM must be restarted in order for the
				 *         changed boot class path to take affect.
				 */
				case FrameworkEvent.STOPPED_BOOTCLASSPATH_MODIFIED:
				/*
				 * JavaDoc
				 * 
				 *         FrameworkEvent.STOPPED_UPDATE - This
				 *         Framework has been updated which has shutdown and will now
				 *         restart.
				 */
				case FrameworkEvent.STOPPED_UPDATE:
				/*
				 * JavaDoc
				 * 
				 *         FrameworkEvent.ERROR - The Framework
				 *         encountered an error while shutting down or an error has occurred
				 *         which forced the framework to shutdown.
				 */
				case FrameworkEvent.ERROR:
					stop1();
					
					stopEvent = event;
					
					synchronized (stopLock) {
						stopLock.notifyAll();
					}

					break;
				}
			}
		});
		
		try {
			synchronized (stopLock) {
				if (getState() != INSTALLED) {
					stopLock.wait(timeout);
				}
			}
		} catch (InterruptedException e) {
			// do nothing
		}
		
		return stopEvent;
	}
	
	private void stop1() {
		unregisterServices(Framework.this);

		this.logServiceTracker.close();

		/*
		 * 4.7.2  3. Event handling is disabled.
		 */
		this.eventDispatcher.shutdown();
		
		setState(Bundle.RESOLVED);
		
		setBundleContext(null);
	}
	
	void start0() throws BundleException {
		/*
		 * JavaDoc  Start this Framework.
		 * 
		 * The following steps are taken to start this Framework:
		 * 
		 * 		If this Framework is not in the STARTING state,
		 * init() this Framework.
		 */
		
		if (getState() != STARTING) {
			init();
		}
		
		/*
		 * JavaDoc
		 * 
		 * All installed bundles must be started in accordance with each
		 * bundle's persistent autostart setting. This means some bundles
		 * will not be started, some will be started with eager activation
		 * and some will be started with their declared activation policy. If
		 * this Framework implements the optional Start Level Service
		 * Specification, then the start level of this Framework is moved to the
		 * start level specified by the Constants.FRAMEWORK_BEGINNING_STARTLEVEL 
		 * beginning start level framework property, as described in the Start Level Service
		 * Specification. If this framework property is not specified, then the
		 * start level of this Framework is moved to start level one (1). Any
		 * exceptions that occur during bundle starting must be wrapped in a
		 * BundleException and then published as a framework event of type
		 * FrameworkEvent.ERROR.
		 */
		
		try {			
			BundleContext context = getBundleContext();
			
			/*
			 * 4.7.1  2. The system bundle enters the STARTING state. More information about
			 * the system bundle can be found in The System Bundle on page 92.
			 * 
			 * 
			 * After all services are registered.
			 */ 
			
			/*
			 * 4.7.1  3. All installed bundles previously recorded as being started must be
			 * started
			 */
			
			Bundle[] bundles = storage.getBundles();
			
			for (int i = 1; i < bundles.length; i++) {
				if (isBundlePersistentlyStarted(bundles[i])) {
					try {
						int options = getBundleAutostartSetting(bundles[i]);    					
					    bundles[i].start(options);
					} catch (BundleException e) {
						log(LogService.LOG_ERROR, e.getMessage(), e);
					}
				}
			}
			
			/*
			 * Start XMLParserActivator after System Bundle resolving process
			 * to permit to properly attach Fragments 
			 */
			BundleActivator activator = new XMLParserActivator();
			activator.start(context);
			
			String beginningStartLevel = getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
			if (beginningStartLevel == null) {
				beginningStartLevel = "1";
			}

			setStartLevel(Integer.parseInt(beginningStartLevel));
			
			/*
			 * 4.7.1  4. The system bundle enters the ACTIVE state.
			 */ 
			setState(Bundle.ACTIVE);

			/*
			 * 4.7.1  5. A Framework event of type FrameworkEvent.STARTED is broadcast.
			 */
			FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.STARTED, Framework.this, null);
			postFrameworkEvent(frameworkEvent);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BundleException(e.getMessage(), e);
		}
	}

	void stop0() {
		/* 
		 * JavaDoc  If this bundle's state is not ACTIVE then this method returns immediately.
		 */
		if (getState() != ACTIVE) {
		    return;
		}

		/*
		 * 8.2.1  A start level of 0 (zero) is the state in which the
		 * Framework (...) has completed shutdown
		 * 
		 * 4.7.2  Shutdown
		 * 
		 * The Framework will also need to be shut down on occasion. Shutdown can
		 * also be initiated by stopping the system bundle, covered in The System Bundle
		 * on page 96. When the Framework is shut down, the following actions
		 * must occur in the given order:
		 * 
		 * 1. The system bundle enters the STOPPING state.
		 */
		setState(Bundle.STOPPING);
		
		/* 
		 * 4.7.2  2. All ACTIVE bundles are stopped as described in the Bundle.stop method,
		 * except that their persistently recorded state indicates that they must be
		 * restarted when the Framework is next started is kept unchanged. Any
		 * exceptions that occur during shutdown must be wrapped in a
		 * BundleException and then published as a Framework event of type
		 * FrameworkEvent.ERROR. If the Framework implements the optional
		 * Start Level specification, this behavior is different. See Start Level Service
		 * Specification on page 203. During the shutdown, bundles with a lazy policy
		 * must not be activated even when classes are loaded from them.
		 */
    	BundleContext context = getBundleContext();

		ServiceReference startLevelReference = context.getServiceReference(StartLevel.class.getName());
		if (startLevelReference == null) {
	    	log(LogService.LOG_DEBUG, "StartLevel service is unavailable.");
			return;
		}
		
		StartLevel startLevel = (StartLevel) context.getService(startLevelReference);
		
		startLevel.setStartLevel(0);
		
		context.ungetService(startLevelReference);
	}
	
	/*
	 * Mediator : Bundle
	 */

	long getBundleId(Bundle bundle) {
		return storage.getBundleId(bundle);
	}
	
	long getLastModified(Bundle bundle) {
		return storage.getLastModified(bundle);
	}
	
	String getLocation(Bundle bundle) {
		return storage.getLocation(bundle);
	}
	
	Dictionary getHeaders(Bundle bundle) {
		return storage.getHeaders(bundle);
	}
	
	void update(Bundle bundle, InputStream is) throws BundleException {
		storage.update(bundle, is);
	}

	String getLibraryPath(Bundle bundle, String libfilename) throws IOException {
		return storage.getLibraryPath(bundle, libfilename);
	}
	
	ServiceReference[] getRegisteredServices(Bundle bundle) {
		return serviceRegistry.getRegisteredServices(bundle);
	}

	BundleURLClassPath getBundleURLClassPath(Bundle bundle) {
		return getBundleURLClassPath(bundle, bundle.getVersion());
	}

	BundleURLClassPath getBundleURLClassPath(Bundle bundle, Version version) {
		return storage.getBundleURLClassPath(bundle, version);
	}
	
	/*
	 * Mediator : BundleContext
	 */
	
	Bundle install(String location, InputStream input) throws BundleException {
		Bundle bundle = storage.install(location, input);
	
		BundleContext context = getBundleContext();
		if (context == null) {
			log(LogService.LOG_DEBUG, "Framework context is unavailable.");
			return null;
		}
		
		if (getBundleType(bundle) != PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
		    ServiceReference startLevelReference = context.getServiceReference(StartLevel.class.getName());
		    if (startLevelReference == null) {
		    	log(LogService.LOG_DEBUG, "StartLevel service is unavailable.");
		    	return null;
		    }
		    
		    StartLevel startLevel = (StartLevel) context.getService(startLevelReference);

		    int istartlevel = startLevel.getInitialBundleStartLevel();
		    startLevel.setBundleStartLevel(bundle, istartlevel);
		    
		    context.ungetService(startLevelReference);
		} else {
			/*
			 * 3.15  1 When an extension bundle is installed it enters the INSTALLED state.
			 * 2 The extension bundle is allowed to enter the RESOLVED state at the
			 * Frameworks discretion, which can require a Framework re-launch.
			 */
			try {
				ManifestEntry[] entries = ManifestEntry.parse(bundle.getHeaders().get(Constants.FRAGMENT_HOST));
				String symbolicName = entries[0].getName();
				
				if (
						symbolicName.equals(framework.getSymbolicName()) || 
						symbolicName.equals(Constants.SYSTEM_BUNDLE_SYMBOLICNAME)
					) {
					framework.update();
				}
			} catch (Exception e) {
				// do nothing
			}
		}

		BundleEvent bundleEvent = new BundleEvent(BundleEvent.INSTALLED, bundle);
		/*
		 * It has to dispatch synchronously to permit to
		 * PackageAdmin to register Fragment(s) 
		 */
		sendBundleEvent(bundleEvent);
		
		return bundle;
	}

	void remove(Bundle bundle) {
		storage.remove(bundle);
	}

	void setRemovalPending(Bundle bundle) {
		storage.setRemovalPending(bundle);
	}
	
	File getDataFile(Bundle bundle, String filename) {
		return storage.getDataFile(bundle, filename);
	}

	Bundle getBundle(long id) {
		return storage.getBundle(id);
	}

	Bundle[] getBundles() {
		return storage.getBundles();
	}
	
//	Bundle getBundle(URL url) {
//		String host = url.getHost();
//		
//		long bundleId;
//		
//		int i = host.indexOf('.');
//		if (i == -1) {
//			bundleId = Long.parseLong(host);
//		} else {
//			bundleId = Long.parseLong(host.substring(0, i));
//		}
//		
//		Bundle bundle = framework.getBundle(bundleId);
//
//		return bundle;
//	}
	
	public void addBundleListener(Bundle bundle, BundleListener listener) {
        /*
         * 4.8.1.1
         */
		if (listener instanceof SynchronousBundleListener) {
			SecurityManager securityManager = System.getSecurityManager();
			if (securityManager != null) {
				securityManager.checkPermission(new AdminPermission(bundle, AdminPermission.LISTENER));
			}
		}
		
		Dictionary d = new CaseSensitiveDictionary(false);
		if (listener instanceof SynchronousBundleListener) {
			d.put(EventConstants.EVENT_TOPIC, EventFactory.SYNCHRONOUS_BUNDLE_EVENT_TOPICS);			
		} else {
			d.put(EventConstants.EVENT_TOPIC, EventFactory.BUNDLE_EVENT_TOPICS);			
		}
		
		if (listener instanceof SynchronousBundleListener) {
			d.put(Constants0.LISTENERCLASS, SynchronousBundleListener.class.getName());			
		} else {
			d.put(Constants0.LISTENERCLASS, BundleListener.class.getName());			
		}		

		EventHandler handler = new BundleListenerHandler(listener);

		registerService(bundle, EventHandler.class.getName(), handler, d);
	}

	public void addFrameworkListener(Bundle bundle, FrameworkListener listener) {
		Dictionary d = new CaseSensitiveDictionary(false);
		d.put(EventConstants.EVENT_TOPIC, EventFactory.FRAMEWORK_EVENT_TOPICS);
		d.put(Constants0.LISTENERCLASS, FrameworkListener.class.getName());

		EventHandler handler = new FrameworkListenerHandler(listener);
		
		registerService(bundle, EventHandler.class.getName(), handler, d);
	}
	
	void addServiceListener(Bundle bundle, ServiceListener listener, String filter)	throws InvalidSyntaxException {
		// Invoke the ListenerHook.added() on all hooks.
        List listenerHooks = serviceRegistry.getListenerHooks();
        Collection c = Collections.singleton(new ListenerHookInfoImpl(bundle.getBundleContext(), filter));
        for (int i = 0; i < listenerHooks.size(); i++) {
            ((ListenerHook) listenerHooks.get(i)).added(c);
        }
		
		Dictionary d = new CaseSensitiveDictionary(false);
		d.put(EventConstants.EVENT_TOPIC, EventFactory.SERVICE_EVENT_TOPICS);
		
		if (filter != null) {
			d.put(EventConstants.EVENT_FILTER, filter);
		}
		
		if (listener instanceof AllServiceListener) {
			d.put(Constants0.LISTENERCLASS, AllServiceListener.class.getName());			
		} else {
			d.put(Constants0.LISTENERCLASS, ServiceListener.class.getName());			
		}		

		EventHandler handler = new ServiceListenerHandler(listener);
		
		registerService(bundle, EventHandler.class.getName(), handler, d);
	}
	
	void removeServiceListener(Bundle bundle, ServiceListener listener) {
		String clazz;
		
		if (listener instanceof AllServiceListener) {
			clazz = AllServiceListener.class.getName();			
		} else {
			clazz = ServiceListener.class.getName();			
		}		

		try {
			ServiceReference[] references = getBundleContext().getServiceReferences(clazz, null);
			
			if (references != null) {
				// Invoke the ListenerHook.removed() on all hooks.
	            List listenerHooks = serviceRegistry.getListenerHooks();
	            Collection c = Collections.singleton(listener);
	            for (int i = 0; i < listenerHooks.size(); i++) {
	                ((ListenerHook) listenerHooks.get(i)).removed(c);
	            }
				
				for (int i = 0; i < references.length; i++) {
					ServiceReference reference = references[i];
					
					Object service = getService(reference);
					if (service instanceof ServiceListenerHandler) {
						ServiceListenerHandler handler = (ServiceListenerHandler) service;
						if (handler.getListener().equals(listener)) {
							ServiceRegistration registration = getServiceRegistration(reference);
							registration.unregister();
							
							break;
						}
					}
				}
			}
		} catch (InvalidSyntaxException e) {
			log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}

	public void removeBundleListener(Bundle bundle, BundleListener listener) {
        /*
         * 4.8.1.1
         */
		if (listener instanceof SynchronousBundleListener) {
			SecurityManager securityManager = System.getSecurityManager();
			if (securityManager != null) {
				securityManager.checkPermission(new AdminPermission("*", AdminPermission.LISTENER));
			}
		}

		String clazz;
		
		if (listener instanceof SynchronousBundleListener) {
			clazz = SynchronousBundleListener.class.getName();			
		} else {
			clazz = BundleListener.class.getName();			
		}		

		try {
			ServiceReference[] references = context.getServiceReferences(clazz, null);
			
			if (references != null) {
				for (int i = 0; i < references.length; i++) {
					ServiceReference reference = references[i];

					Object service = getService(reference);
					if (service instanceof BundleListenerHandler) {
						BundleListenerHandler handler = (BundleListenerHandler) service;
						if (handler.getListener().equals(listener)) {
							ServiceRegistration registration = framework.getServiceRegistration(reference);
							registration.unregister();
							
							break;
						}
					}
				}
			}
		} catch (InvalidSyntaxException e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}

	public void removeFrameworkListener(Bundle bundle, FrameworkListener listener) {
		String clazz = FrameworkListener.class.getName();			

		try {
			ServiceReference[] references = context.getServiceReferences(clazz, null);
			
			if (references != null) {
				for (int i = 0; i < references.length; i++) {
					ServiceReference reference = references[i];

					Object service = getService(reference);
					if (service instanceof FrameworkListenerHandler) {
						FrameworkListenerHandler handler = (FrameworkListenerHandler) service;
						if (handler.getListener().equals(listener)) {
							ServiceRegistration registration = framework.getServiceRegistration(reference);
							registration.unregister();
							
							break;
						}
					}
				}
			}
		} catch (InvalidSyntaxException e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}

	Object getService(ServiceReference reference) {
		return serviceRegistry.getService(reference);
	}
	
	boolean ungetService(ServiceReference reference) {
		return serviceRegistry.ungetService(reference);
	}
	
	void unregisterService(ServiceRegistration registration) {
		serviceRegistry.unregisterService(registration);
	}

	void unregisterServices(Bundle bundle) {
		serviceRegistry.unregisterServices(bundle);
	}
	
	ServiceRegistration registerService(Bundle bundle, String clazz, Object service, Dictionary properties) {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new ServicePermission(clazz, ServicePermission.REGISTER));
		}
		
		return serviceRegistry.registerService(bundle, clazz, service, properties);
	}
	
	ServiceRegistration registerService(Bundle bundle, String[] clazzez, Object service, Dictionary properties) {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			for (int i = 0; i < clazzez.length; i++) {
				securityManager.checkPermission(new ServicePermission(clazzez[i], ServicePermission.REGISTER));				
			}
		}
		
		return serviceRegistry.registerService(bundle, clazzez, service, properties);
	}

	ServiceReference getReference(ServiceRegistration registration) {
		return serviceRegistry.getServiceReference(registration);
	}

	ServiceReference getServiceReference(Bundle bundle, String clazz) {
		return serviceRegistry.getServiceReference(bundle, clazz);
	}

	ServiceReference[] getServiceReferences(Bundle bundle, String clazz, String filter) throws InvalidSyntaxException {
        ServiceReference[] references = serviceRegistry.getServiceReferences(bundle, clazz, filter);
		if (references != null) {
	        List findHooks = serviceRegistry.getFindHooks();
	        for (int i = 0; i < findHooks.size(); i++)
	        {
	            ((FindHook) findHooks.get(i)).find(
	                bundle.getBundleContext(),
	                clazz,
	                filter,
	                false,
	                Arrays.asList(references));
	        }
		}
		
		return references; 
	}

	ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
		ServiceReference[] references = serviceRegistry.getAllServiceReferences(clazz, filter);
		
		if (references != null) {
	        List findHooks = serviceRegistry.getFindHooks();
	        for (int i = 0; i < findHooks.size(); i++)
	        {
	            ((FindHook) findHooks.get(i)).find(
	                null,
	                clazz,
	                filter,
	                true,
	                Arrays.asList(references));
	        }
		}
		
		return references;
	}
	
	ServiceRegistration getServiceRegistration(ServiceReference reference) {
		return serviceRegistry.getServiceRegistration(reference);
	}
	
	void setProperties(ServiceRegistration registration, Dictionary properties) {
		serviceRegistry.setProperties(registration, properties);
	}
	
	Object getProperty(ServiceReference reference, String key) {
		return serviceRegistry.getProperty(reference, key);
	}
	
	String[] getPropertyKeys(ServiceReference reference) {
		return serviceRegistry.getPropertyKeys(reference);
	}
	
	public Bundle[] getUsingBundles(ServiceReference reference) {
		return serviceRegistry.getUsingBundles(reference);
	}	

	ProtectionDomain getProtectionDomain(Bundle bundle) throws Exception {
		BundleContext context = getBundleContext();
		
		ServiceReference permissionAdminReference = context.getServiceReference(PermissionAdmin.class.getName());
		PermissionAdmin permissionAdmin = (PermissionAdmin) context.getService(permissionAdminReference);

		PermissionInfo[] permissions = permissionAdmin.getPermissions(bundle.getLocation());
		if (permissions == null) {
			/*
			 * 10.1.3 When no specific permissions are set, the bundle must use the default
			 * permissions. If no default is set, the bundle must use
			 * java.security.AllPermission.
			 */
			
			permissions = permissionAdmin.getDefaultPermissions();
			if (permissions == null) {
				permissions = PermissionAdminImpl.ALL_PERMISSIONS;
			}
		}
		
		BundlePermissionCollection permissionCollecion = new BundlePermissionCollection(permissions);
		
		CodeSource codeSource = new CodeSource(new URL(bundle.getLocation()), (Certificate[]) null);
		
	    ProtectionDomain protectionDomain = new ProtectionDomain(codeSource, permissionCollecion);
		
		context.ungetService(permissionAdminReference);		
		
		return protectionDomain;
	}
	
	BundleContext createBundleContext(Bundle host) {
		BundleContext context = new BundleContextImpl(this, host);
		
		return context;
	}
	
	String getServicePID(Bundle bundle, Object service) {
		String pid = new StringBuilder(TextUtil.toHexString(bundle.getBundleId()))
			.append('#')
			.append(TextUtil.toHexString(bundle.getLastModified())).append('#')
			.append(TextUtil.toHexString(service.getClass().getName())).toString();
		
		return pid;
	}

	
	/*
	 * Bundle Impl.
	 */
	
	public void start(int options) throws BundleException {
		if (getState() == ACTIVE) {
			/* 
			 * 4.4  start – Does nothing because the system bundle is already started.
			 */
	
			FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.WARNING, this, new BundleException("Framework already started up"));
			postFrameworkEvent(frameworkEvent);
			
			return;
		}
		
//		if (configuration == null) {
//			init();
//		}
		
		start0();
	}
	
	public void stop() throws BundleException {
		/* 
		 * 4.4  stop – Returns immediately and shuts down the Framework on another thread.
		 */
		
//		Executors.newCachedThreadPool(getClass().getName(), 1).execute(new Runnable() {
		new Thread(new Runnable() {
			public void run() {
				/*
				 * Shutdown
				 */
				final BundleContext context = getBundleContext();

				context.addFrameworkListener(new FrameworkListener() {
					public void frameworkEvent(FrameworkEvent event) {
						if (event.getType() == FrameworkEvent.STARTLEVEL_CHANGED) {
							ServiceReference startLevelReference = context.getServiceReference(StartLevel.class.getName());
							
							StartLevel startLevel = (StartLevel) context.getService(startLevelReference);

							int startlevel = startLevel.getStartLevel();
							
							context.ungetService(startLevelReference);
							
							if (startlevel == 0) {								
								FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.STOPPED, framework, null);
								framework.postFrameworkEvent(frameworkEvent);
							}
						}
					}
				});

				stop0();
			}
//		});
		}, new StringBuilder(getClass().getName()).append(" stop").toString()).start();
	}
	
	public void stop(int options) throws BundleException {
		this.stop();
	}
	
	public void update() throws BundleException {
		/* 4.4  update – Returns immediately, then stops and restarts the Framework on another
		 * thread.
		 */
		
//		Executors.newCachedThreadPool(getClass().getName(), 1).execute(new Runnable() {
		new Thread(new Runnable() {
			public void run() {
				/*
				 * 3.15 (...) the Framework must shutdown; the host VM must terminate and 
				 * framework must be relaunched.
				 * 
				 * PENDING: an API to advise the launcher that framework needs to be relaunched
				 */
				context.addFrameworkListener(new FrameworkListener() {
					public void frameworkEvent(FrameworkEvent event) {
						if (event.getType() == FrameworkEvent.STARTLEVEL_CHANGED) {
							ServiceReference startLevelReference = context.getServiceReference(StartLevel.class.getName());
							
							StartLevel startLevel = (StartLevel) context.getService(startLevelReference);

							int startlevel = startLevel.getStartLevel();
							
							context.ungetService(startLevelReference);
							
							if (startlevel == 0) {
								FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.STOPPED_UPDATE, framework, null);
								framework.postFrameworkEvent(frameworkEvent);
							}
						}
					}
				});


				stop0();
			}
//		});
		}, new StringBuilder(getClass().getName()).append(" update").toString()).start();
	}
	
	/*
	 * 4.4  uninstall – The Framework must throw a BundleException indicating that the system bundle cannot
	 * be uninstalled.
	 */
	public void uninstall() throws BundleException {
		throw new BundleException("Framework cannot be uninstalled");
	}
	
	public void update(InputStream is) throws BundleException {
		update();
	}
	
	/*
	 * Properties
	 */
	
	String getProperty(String key) {
		SecurityManager securityManager = System.getSecurityManager();

		if (securityManager != null) {
			securityManager.checkPropertyAccess(key);
		}
		
		return configuration.getProperty(key);
	}
	
	/*
	 * Events
	 */
	
	void sendEvent(Event event) {
		eventDispatcher.syncDispatchEvent(event);
	}
	
	void postEvent(Event event) {
		eventDispatcher.asyncDispatchEvent(event);
	}
	
	void postFrameworkEvent(FrameworkEvent frameworkEvent) {
		Event event = EventFactory.createEvent(frameworkEvent);
		
		postEvent(event);
	}
	
	void sendFrameworkEvent(FrameworkEvent frameworkEvent) {
		Event event = EventFactory.createEvent(frameworkEvent);
		
		sendEvent(event);
	}

	void postBundleEvent(BundleEvent bundleEvent) {
		Event event = EventFactory.createEvent(bundleEvent);

		postEvent(event);
	}
	
	void sendBundleEvent(BundleEvent bundleEvent) {
		Event event = EventFactory.createEvent(bundleEvent);
				
		/*
		 * all ServiceEvent types are synchronously delivered (6.1.19)
		 */		
		sendEvent(event);
	}
	
	
	ServiceReference[] getServiceReferences(Event event) {
		return serviceRegistry.getServiceReferences(event);
	}

	/*
	 * 113.6.5
	 * 
	 * Service Events must be delivered asynchronously (...).
	 */
	void postServiceEvent(ServiceEvent serviceEvent) {
		Event event = EventFactory.createEvent(serviceEvent);
		
		List eventHooks = serviceRegistry.getEventHooks();
        if (!eventHooks.isEmpty()) {
        	ServiceReference[] references = getServiceReferences(event);
        	Collection contexts = new ArrayList();
        	for (int i = 0; i < references.length; i++) {
        		contexts.add(references[i].getBundle().getBundleContext());
        	}

        	for (int i = 0; i < eventHooks.size(); i++) {
                ((EventHook) eventHooks.get(i)).event(serviceEvent, contexts);
            }
        }
		
		postEvent(event);
	}
	
	
	void log(int level, String message, Throwable exception) {
		LogService logService = (LogService) logServiceTracker.getService();
		if (logService != null) {
			logService.log(level, message, exception);
//		} else {
//			StringBuilder sb = new StringBuilder();
//			
//			Calendar calendar = Calendar.getInstance();
//			
//			sb.append(calendar.get(Calendar.YEAR));
//			sb.append('-');
//			int month = calendar.get(Calendar.MONTH) + 1;
//			if (month < 10) {
//				sb.append('0');
//			}
//			sb.append(month);
//			sb.append('-');
//			int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
//			if (dayOfMonth < 10) {
//				sb.append('0');
//			}
//			sb.append(dayOfMonth);
//			sb.append(' ');
//			int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
//			if (hourOfDay < 10) {
//				sb.append('0');
//			}
//			sb.append(hourOfDay);
//			sb.append(':');
//			int minute = calendar.get(Calendar.MINUTE);
//			if (minute < 10) {
//				sb.append('0');
//			}
//			sb.append(minute);
//			sb.append(':');
//			int second = calendar.get(Calendar.SECOND);
//			if (second < 10) {
//				sb.append('0');
//			}
//			sb.append(second);
//			sb.append('.');
//			int millisecond = calendar.get(Calendar.MILLISECOND);
//			if (millisecond < 100) {
//				sb.append('0');
//
//				if (millisecond < 10) {
//					sb.append('0');
//				}
//			}
//			sb.append(millisecond);
//
//			sb.append(" ");
//			
//			switch (level) {
//			case LogService.LOG_ERROR:
//				sb.append("ERROR");
//				break;
//			case LogService.LOG_WARNING:
//				sb.append("WARNING");
//				break;
//			case LogService.LOG_INFO:
//				sb.append("INFO");
//				break;
//			default:
//				sb.append("DEBUG");
//				break;
//			}
//			
//			sb.append(": ");
//
//			sb.append(message);
//			
//			if (exception != null) {
//				sb.append("\n");
//				ByteArrayOutputStream baos = new ByteArrayOutputStream();
//				exception.printStackTrace(new PrintStream(baos));
//				sb.append(baos.toString());
//			}
//			
//			System.err.println(sb.toString());
		}
	}

	void log(int level, String message) {
		log(level, message, null);
	}
	
	/*
	 * ClassLoader(s)
	 */
	
	boolean isBootDelegated(String pkgName) {
		return bootDelegatedPackageNames.contains(pkgName);
	}	

	BundleClassLoader createBundleClassLoader(Bundle host) {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkCreateClassLoader();
		}
		
		BundleURLClassPath[] classPaths = new BundleURLClassPath[] {
			getBundleURLClassPath(host)
		};

		Bundle[] fragments = getFragments0(host);
		if (fragments != null) {
			for (int i = 0; i < fragments.length; i++) {
				classPaths = (BundleURLClassPath[]) ArrayUtil.add(classPaths, getBundleURLClassPath(fragments[i]));
			}
		}
		
		ClassLoader parent;
		String bundleParent = getProperty(Constants.FRAMEWORK_BUNDLE_PARENT);
		if (bundleParent == null || bundleParent.equals(Constants.FRAMEWORK_BUNDLE_PARENT_BOOT)) {
			parent = ClassLoader.getSystemClassLoader();
		} else if (bundleParent == null || bundleParent.equals(Constants.FRAMEWORK_BUNDLE_PARENT_APP)) {
			parent = ClassLoader.getSystemClassLoader();
		} else if (bundleParent == null || bundleParent.equals(Constants.FRAMEWORK_BUNDLE_PARENT_EXT)) {
			parent = ClassLoader.getSystemClassLoader().getParent();
		} else if (bundleParent == null || bundleParent.equals(Constants.FRAMEWORK_BUNDLE_PARENT_FRAMEWORK)) {
			parent = Framework.class.getClassLoader();
		} else {
			throw new IllegalArgumentException(new StringBuilder("Unknown parent class loader type: ").append(bundleParent).toString());
		}
		
		BundleClassLoader classLoader0;
		if (host == Framework.this) {
			classLoader0 = new SystemBundleClassLoader(parent, Framework.this, classPaths);
		} else {
			classLoader0 = new BundleClassLoader(parent, Framework.this, host, classPaths);
		}
		
		return classLoader0;
	}

	/*
	 * PackageAdmin
	 */
	
	void unresolveBundle(Bundle bundle) {
		((PackageAdminImpl) packageAdmin).unresolveBundle(bundle);
	}

	
	int getBundleType(Bundle bundle) {
		return packageAdmin.getBundleType(bundle);
	}
	
	RequiredBundle[] getRequiredBundles(String symbolicName) {
		return packageAdmin.getRequiredBundles(symbolicName);
	}

	Bundle[] getBundles(String symbolicName, String versionRange) {
		return packageAdmin.getBundles(symbolicName, versionRange);
	}

	Bundle[] getHosts(Bundle bundle) {
		return packageAdmin.getHosts(bundle);
	}
	
	Bundle[] getHosts0(Bundle bundle) {
		
		/*
		 * JavaDoc  Returns an array containing the host bundle to which the specified fragment 
		 * bundle is attached or null if the specified bundle is not attached to a host or is 
		 * not a fragment bundle.
		 */
		
		if (getBundleType(bundle) != PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
			return null;
		}
		
		BundleContext context = framework.getBundleContext();
		
		Bundle[] bundles = context.getBundles();
		
		for (int i = 0; i < bundles.length; i++) {
			try {
				Bundle bundle0 = bundles[i];
				
				if (getBundleType(bundle0) != PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
					continue;
				}
				
				ManifestEntry[] entries = ManifestEntry.parse(bundle0.getHeaders().get(Constants.FRAGMENT_HOST));
				for (int j = 0; j < entries.length; j++) {
					ManifestEntry entry = entries[j];
					
					String versionAttribute = null;
				    if (entry.hasAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE)) {
				    	versionAttribute = entry.getAttributeValue(Constants.BUNDLE_VERSION_ATTRIBUTE);
				    }
					
				    Bundle[] hosts = getBundles(entry.getName(), versionAttribute);
					
					if (hosts != null) {
						return hosts;
					}
				}
			} catch (Exception e) {
				framework.log(LogService.LOG_ERROR, e.getMessage(), e);
			}
		}
		
		return null;
	}
	
	public boolean resolveBundles(Bundle[] bundles) {
		return packageAdmin.resolveBundles(bundles);
	}

	public Bundle[] getFragments(Bundle bundle) {
		return packageAdmin.getFragments(bundle);
	}
	
	Bundle[] getFragments0(Bundle bundle) {
		/*
		 * PackageAdmin
		 * 
		 * JavaDoc  If the specified bundle is a fragment then null is returned. 
		 */

		if (getBundleType(bundle) == PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
			return null;
		}
		
		BundleContext context = framework.getBundleContext();
		
		Version hostVersion = bundle.getVersion();
		
		Bundle[] bundles = context.getBundles();
		
    	Bundle[] fragments = new Bundle[0];
		for (int i = 0; i < bundles.length; i++) {
			try {
				Bundle bundle0 = bundles[i];
				
				if (getBundleType(bundle0) != PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
					continue;
				}
				
				ManifestEntry[] entries = ManifestEntry.parse(bundle0.getHeaders().get(Constants.FRAGMENT_HOST));
				for (int j = 0; j < entries.length; j++) {
					ManifestEntry entry = entries[j];
					
					String versionAttribute = null;
				    if (entry.hasAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE)) {
				    	versionAttribute = entry.getAttributeValue(Constants.BUNDLE_VERSION_ATTRIBUTE);
				    }
				    
				    VersionRange versionRange = new VersionRange(versionAttribute);
				    if (
				    		bundle.getSymbolicName().equals(entry.getName()) && 
				    		versionRange.isIncluded(hostVersion)
				    	) {
				    	fragments = (Bundle[]) ArrayUtil.add(fragments, bundle0);
				    }
				}
			} catch (Exception e) {
				framework.log(LogService.LOG_ERROR, e.getMessage(), e);
			}
		}
		
		return fragments.length > 0 ? fragments : null;
	}

	
	public ExportedPackage[] getExportedPackages(Bundle bundle) {
		return packageAdmin.getExportedPackages(bundle);
	}

	public ExportedPackage[] getExportedPackages(String name) {
		return packageAdmin.getExportedPackages(name);
	}

	/*
	 * StartLevel
	 */
	
	int getInitialBundleStartLevel() {
		return startLevel.getInitialBundleStartLevel();
	}
	
	void setInitialBundleStartLevel(int startlevel) {
		startLevel.setInitialBundleStartLevel(startlevel);
	}

	int getStartLevel() {
		return startLevel.getStartLevel();
	}
	
	void setStartLevel(int startlevel) {
		startLevel.setStartLevel(startlevel);
	}
	
	int getBundleStartLevel(Bundle bundle) {
		return storage.getBundleStartLevel(bundle);
	}

	
	void setBundleAutostartSetting(Bundle bundle, int autostartSetting) {
		/*
		 * 4.3.5  The framework therefore maintains a persistent autostart setting for each
		 * bundle.
		 */
		
		storage.setBundleAutostartSetting(bundle, autostartSetting);
	}
	
	boolean isBundleActivationPolicyUsed(Bundle bundle) {
		if (bundle.getState() == Bundle.UNINSTALLED) {
			throw new IllegalArgumentException(new StringBuilder(bundle.toString()).append(" has been uninstalled.").toString());
		}
		
		return getBundleAutostartSetting(bundle) == AbstractBundle.STARTED_DECLARED; 
	}

	int getBundleAutostartSetting(Bundle bundle) {
		/*
		 * 4.3.5  The framework therefore maintains a persistent autostart setting for each
		 * bundle.
		 */
		
		return storage.getBundleAutostartSetting(bundle);
	}
	
	void setBundleStartLevel(Bundle bundle, int startlevel) {
		storage.setBundleStartLevel(bundle, startlevel);
	}
	
	boolean isBundlePersistentlyStarted(Bundle bundle) {
		return storage.isBundlePersistentlyStarted(bundle);
	}

	/*
	 * PermissionAdmin 
	 */
	
	String[] getLocations() {
		return storage.getLocations();
	}
	
	void removePermissions(String location) throws IOException {
		storage.removePermissions(location);
	}

	void removeDefaultPermissions() throws IOException {
		storage.removeDefaultPermissions();
	}
	
	PermissionInfo[] getPermissions(String location) {
		return storage.getPermissions(location);
	}
	
	PermissionInfo[] getDefaultPermissions() {
		return storage.getDefaultPermissions();
	}

	public void setDefaultPermissions(PermissionInfo[] permissions) {
		storage.setDefaultPermissions(permissions);
	}

	public void setPermissions(String location, PermissionInfo[] permissions) {
		if (location == null) {
			throw new NullPointerException();
		}

		storage.setPermissions(location, permissions);
	}
}
