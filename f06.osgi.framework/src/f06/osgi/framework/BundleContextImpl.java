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
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServicePermission;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

class BundleContextImpl implements BundleContext {
	
	protected Framework framework;

	protected Bundle host;
	
	protected Map counts;
	
	protected Map serviceFactories;
		
	public BundleContextImpl(Framework framework, Bundle host) {
		this.framework = framework;
		this.host = host;
		
		counts = new HashMap();
		
		serviceFactories = new HashMap();
	}
	
	public void addBundleListener(BundleListener listener) {
		framework.addBundleListener(host, listener);
	}

	public void addFrameworkListener(FrameworkListener listener) {
		framework.addFrameworkListener(host, listener);
	}

	public void addServiceListener(final ServiceListener listener, String filter)	throws InvalidSyntaxException {
		framework.addServiceListener(host, listener, filter);
	}

	public void addServiceListener(ServiceListener listener) {
		try {
			addServiceListener(listener, null);
		} catch (InvalidSyntaxException e) {
			// do nothing
		}
	}


	public Filter createFilter(String filter) throws InvalidSyntaxException {
		return FrameworkUtil.createFilter(filter);
	}

	public ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new ServicePermission(clazz, ServicePermission.GET));
		}
		
		return framework.getAllServiceReferences(clazz, filter);
	}

	public Bundle getBundle() {
		return host;
	}

	public Bundle getBundle(long id) {
		return framework.getBundle(id);
	}

	public Bundle[] getBundles() {
		return framework.getBundles();
	}

	public File getDataFile(String filename) {
		return framework.getDataFile(host, filename);
	}

	public String getProperty(String key) {
		return framework.getProperty(key);
	}

	public Object getService(ServiceReference reference) {
		Object service = framework.getService(reference);
		/*
		 * JavaDoc  The following steps are required to get the service object:
		 * 
		 *     1 If the service has been unregistered, null is returned.
		 *     
		 */
		if (service == null) {
			return null;
		}
		
		/*
		 *     2 The context bundle's use count for this service is incremented by one.
	     */
	    int useCount = getUseCount(reference);
		setUseCount(reference, ++useCount);
		
		/*
		 *     3 If the context bundle's use count for the service is currently one and
		 *       the service was registered with an object implementing the ServiceFactory
		 *       interface, the ServiceFactory.getService(Bundle, ServiceRegistration) 
		 *       method is called to create a service object for the context bundle. This
		 *       service object is cached by the Framework. While the context bundle's use
		 *       count for the service is greater than zero, subsequent calls to get the
		 *       services's service object for the context bundle will return the cached
		 *       service object.
		 *       If the service object returned by the ServiceFactory object is not an
		 *       instanceof all the classes named when the service was registered or the
		 *       ServiceFactory object throws an exception, <code>null</code> is returned
		 *       and a Framework event of type FrameworkEvent.ERROR is fired.
		 */
		if (service instanceof ServiceFactory) {
			/*
			 *     3 If the context bundle's use count for the service is currently one and
			 *       the service was registered with an object implementing the ServiceFactory
			 *       interface, the ServiceFactory.getService(Bundle, ServiceRegistration) 
			 *       method is called to create a service object for the context bundle. This
			 *       service object is cached by the Framework. (...)
			 */
			if (useCount == 1) {
				ServiceFactory factory = (ServiceFactory) service;
				
				ServiceRegistration registration = framework.getServiceRegistration(reference);
				service = factory.getService(host, registration);
				
				if (service == null) {
					return null;
				}
				
				serviceFactories.put(reference, service);
			} else {
				/*
				 *       (...) While the context bundle's use count for the service is greater 
				 *       than zero, subsequent calls to get the services's service object for the 
				 *       context bundle will return the cached service object.
				 */
				service = serviceFactories.get(reference);
			}
		}
		
		/*
		 *       If the service object returned by the ServiceFactory object is not an
		 *       instanceof all the classes named when the service was registered or the
		 *       ServiceFactory object throws an exception, null is returned
		 *       and a Framework event of type FrameworkEvent.ERROR is fired.
		 */
		Class cls = service.getClass();
		
		String[] clazzez = (String[]) reference.getProperty(Constants.OBJECTCLASS);
		for (int i = 0; i < clazzez.length; i++) {
			String clazz = clazzez[i];
			
			try {
				Class c = host.loadClass(clazz);
				
				if (!c.isAssignableFrom(cls)) {
					Throwable throwable = new ClassCastException(new StringBuilder(c.getName()).append(" is not assignable from ").append(cls.getName()).toString());
					
					FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, host, throwable);
					framework.postFrameworkEvent(frameworkEvent);
					
					return null;
				}
			} catch (ClassNotFoundException e) {
				// do nothing
			}
		}
		
		/*       
		 *     4 The service object for the service is returned.
		 */
		
		return service; 				
	}
	
	private int getUseCount(ServiceReference reference) {
		Integer useCount = (Integer) counts.get(reference);
		if (useCount == null) {
			useCount = Integer.valueOf(0);
			counts.put(reference, useCount);
		}
		
		return useCount.intValue();
	}

	private void setUseCount(ServiceReference reference, int useCount) {
		counts.put(reference, Integer.valueOf(useCount));
	}
	
	ServiceReference[] getServicesInUse() {
		List l = new ArrayList();
		Iterator it = counts.keySet().iterator();
		while (it.hasNext()) {
			ServiceReference reference = (ServiceReference) it.next();
			int useCount = getUseCount(reference);
			if (useCount > 0) {
				l.add(reference);
			}
		}
		
		return (ServiceReference[]) (l.isEmpty() ? null : l.toArray(new ServiceReference[0]));
	}
	
	public ServiceReference getServiceReference(String clazz) {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new ServicePermission(clazz, ServicePermission.GET));
		}
		
		return framework.getServiceReference(host, clazz);
	}

	public ServiceReference[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new ServicePermission(clazz, ServicePermission.GET));
		}
		
		return framework.getServiceReferences(host, clazz, filter);
	}

	public Bundle installBundle(String location) throws BundleException {
		InputStream is = null;
		try {
			is = new URL(location).openStream();
			Bundle bundle = installBundle(location, is);
 			
			return bundle;
		} catch (IOException e) {
			throw new BundleException(e.getMessage(), e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					throw new BundleException(e.getMessage());
				}
			}
		}
	}

	public Bundle installBundle(String location, InputStream input) throws BundleException {
        /*
         * 3.16.1  (...) The installer of an extension bundle must have AdminPermission[
         * <extension bundle>,EXTENSIONLIFECYCLE] to install an extension bundle.\\
         * 
         * see PStore.installBundle
         */
			
		return framework.install(location, input);
	}

	public ServiceRegistration registerService(String[] clazzez, Object service, Dictionary properties) {
		return framework.registerService(host, clazzez, service, properties);
	}

	public ServiceRegistration registerService(String clazz, Object service, Dictionary properties) {
		return framework.registerService(host, clazz, service, properties);
	}

	public void removeBundleListener(BundleListener listener) {
		framework.removeBundleListener(host, listener);
	}
	
	public void removeFrameworkListener(FrameworkListener listener) {
		framework.removeFrameworkListener(host, listener);
	}
	
	public void removeServiceListener(ServiceListener listener) {
		framework.removeServiceListener(host, listener);
	}

	/*
	 * JavaDoc  The following steps are required to unget the service object:
	 * 
	 *     1 If the context bundle's use count for the service is zero or the
	 *       service has been unregistered, <code>false</code> is returned.
	 *     
	 *     2 The context bundle's use count for this service is decremented by
	 *       one.
	 * 
	 *     3 If the context bundle's use count for the service is currently zero
	 *       and the service was registered with a <code>ServiceFactory</code>
	 *       object, the ServiceFactory.ungetService(Bundle, ServiceRegistration, Object)}
	 *       method is called to release the service object for the context bundle.
	 *       true is returned.
	 * 
	 */
	public boolean ungetService(ServiceReference reference) {
		int useCount = getUseCount(reference);
		if (useCount > 0) {
			setUseCount(reference, --useCount);

			if (useCount == 0) {
				Object service = framework.getService(reference);
				if (service == null) {
					return false;
				} else if (service instanceof ServiceFactory) {
					ServiceFactory factory = (ServiceFactory) service;
					
					service = serviceFactories.get(reference);
					
					ServiceRegistration registration = framework.getServiceRegistration(reference);

					factory.ungetService(host, registration, service);
				}
			}

			return framework.ungetService(reference);
		} else {
			return false;
		}
	}

	
}
