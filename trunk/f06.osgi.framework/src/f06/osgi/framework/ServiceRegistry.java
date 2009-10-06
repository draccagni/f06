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
import java.util.Collection;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.PackageAdmin;

import f06.util.ArrayUtil;
import f06.util.CaseSensitiveDictionary;

/*
 * Technical Whitepaper 
 * Revision 4.1 
 * 11 November 2005 
 * 
 * With the Service Registry, bundles can:
 * 
 *   Register objects with the Service Registry.
 *   
 *   Search the Service Registry for matching objects.
 *   
 *   Receive notifications when services become registered or unregistered.
 *   
 */
class ServiceRegistry {
	
	private static Comparator referencesComparator = new Comparator() {
		public int compare(Object o1, Object o2) {
			String listenerClass1 = (String) ((ServiceReference) o1).getProperty(Constants0.LISTENERCLASS);
			
			Object listenerClass2 = (String) ((ServiceReference) o2).getProperty(Constants0.LISTENERCLASS);

			if (listenerClass1 != null && listenerClass2 != null) {
				if (
						listenerClass1.equals(SynchronousBundleListener.class.getName()) && 
						listenerClass2.equals(BundleListener.class.getName())
					) {
					return -1;
				} else if (
						listenerClass2.equals(SynchronousBundleListener.class.getName()) && 
						listenerClass1.equals(BundleListener.class.getName())
					) {
					return 1;
				}
			}

			/*
			 * references are already ordered by id / ranking
			 */
			return 0;
		}
	};
	
	private long nextId;
	
	private Framework framework;
	
	private Map referencesByClass;
	
	private Map referencesByRegistration;

	private Map servicesByReference;
	
	private Map propertiesByReference;
	
	private Map usingBundlesByReference;
	
	private List eventHooks;
	
	private List findHooks;
	
	private List listenerHooks;
	
	ServiceRegistry(Framework framework) {
		this.framework = framework;
		
		this.nextId = 0;
		
		/*
		 * 1.3.4  This association does not have to be a hard relationship
		 */
		// Not use a  weak reference here
		referencesByRegistration = new HashMap();
		
		servicesByReference = new WeakHashMap();
		
		referencesByClass = new HashMap();
		
		propertiesByReference = new WeakHashMap();
		
		usingBundlesByReference = new WeakHashMap();
		
		eventHooks = new ArrayList();
		
		findHooks = new ArrayList();
		
		listenerHooks = new ArrayList();
	}
	
	ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
		if (filter == null) {
			return getAllServiceReferences(clazz);
		} else {
			ServiceReference[] allReferences;
			if (clazz == null) {
				allReferences = getAllServiceReferences();
			} else {
				allReferences = getAllServiceReferences(clazz);
			}
			
	        Filter filterInstance = org.osgi.framework.FrameworkUtil.createFilter(filter);	        	
		        
			ServiceReference[] references = new ServiceReference[0];			
			
			if (allReferences != null) {
				for (int i = 0; i < allReferences.length; i++) {
		            ServiceReference reference = allReferences[i];
					if (filterInstance == null || filterInstance.match(reference)) {
						references = (ServiceReference[]) ArrayUtil.add(references, reference);
					}
				}
				
				if (references.length > 0) {
					Arrays.sort(references);
				}
			}
			
			return references.length > 0 ? references : null;
		}
	}
	
	ServiceReference getServiceReference(Bundle bundle, String clazz) {
		ServiceReference[] allReferences = getAllServiceReferences(clazz);
		if (allReferences != null) {
			for (int i = 0; i < allReferences.length; i++) {
	            ServiceReference reference = allReferences[i];
				if (reference.isAssignableTo(bundle, clazz)) {
					return reference;
				}
			}
		}
		
		return null;
	}

	ServiceReference[] getServiceReferences(Bundle bundle, String clazz, String filter) throws InvalidSyntaxException {
        /*
         * JavaDoc  If clazz is not null, the set is further reduced to those
         * services that are an instanceof and were registered under the 
         * specified class. The complete list of classes of which a service 
         * is an instance and which were specified when the service was 
         * registered is available from the service's Constants.OBJECTCLASS 
         * property.
         */
		ServiceReference[] references = new ServiceReference[0];
		
		ServiceReference[] allReferences = getAllServiceReferences(clazz, filter);
		if (allReferences != null) {
			if (clazz != null) {
				for (int i = 0; i < allReferences.length; i++) {
		            ServiceReference reference = allReferences[i];
					if (reference.isAssignableTo(bundle, clazz)) {
						references = (ServiceReference[]) ArrayUtil.add(references, reference);
						break;
					}
				}
			} else {
				references = allReferences;
			}

			if (references.length > 0) {
				Arrays.sort(references);
			}
		}
			
		return references.length > 0 ? references : null;
	}

	ServiceRegistration registerService(Bundle bundle, String[] clazzez, Object service, Dictionary dictionary) {
		/*
		 *  7.1.3 (...) This is an optional singleton service, so at most one Package Admin service must be
		 *  registered at any moment in time.
		 */
		for (int i = 0; i < clazzez.length; i++) {
			if (clazzez[i].equals(PackageAdmin.class.getName())) {
				if (getAllServiceReferences(PackageAdmin.class.getName()) != null) {
					// XXX log: at most one Package Admin service must be
					// registered at any moment in time
					return null; 
				}
			}
		}
		
		ServiceReferenceImpl reference = new ServiceReferenceImpl(framework, bundle);
			
		Dictionary d = new CaseSensitiveDictionary(false);
		
		if (dictionary != null) {
			for (Enumeration e = dictionary.keys(); e.hasMoreElements(); )  {
				String key = (String) e.nextElement();
				d.put(key, dictionary.get(key));
			}
		}
		
		int ranking = 1;
		for (int i = 0; i < clazzez.length; i++) {
			ServiceReference[] allReferences = getAllServiceReferences(clazzez[i]);
			if (allReferences != null) {
				for (int j = 0; j < allReferences.length; j++) {
					int ranking2 = ((Integer) allReferences[j].getProperty(Constants.SERVICE_RANKING)).intValue();
					ranking = Math.max(ranking, ranking2 + 1);
				}
			}
		}
		d.put(Constants.SERVICE_RANKING, Integer.valueOf(ranking));
		
		d.put(Constants.SERVICE_ID, Long.valueOf(nextId++));
		
		d.put(Constants.SERVICE_PID, framework.getServicePID(bundle, service));		
		
		d.put(Constants.OBJECTCLASS, clazzez);
		
		/*
		 * used to retrieve Bundle's registered services
		 */
		d.put("bundle.id", Long.valueOf(bundle.getBundleId()));
			
		synchronized (propertiesByReference) {
			propertiesByReference.put(reference, d);
		}

		synchronized (referencesByClass) {
			for (int i = 0; i < clazzez.length; i++) {
				String clazz = clazzez[i];
				
				ServiceReference[] allReferences = getAllServiceReferences(clazz);
				if (allReferences == null) {
					allReferences = new ServiceReference[] {
							reference
					};
				} else {
					allReferences = (ServiceReference[]) ArrayUtil.add(allReferences, reference);
				}
				
				referencesByClass.put(clazz, allReferences);
			}
		}
			
		synchronized (servicesByReference) {
			servicesByReference.put(reference, service);
		}
			
		ServiceRegistration registration = new ServiceRegistrationImpl(framework);
			
		synchronized (referencesByRegistration) {
			referencesByRegistration.put(registration, reference);
		}
		
		ServiceEvent serviceEvent = new ServiceEvent(ServiceEvent.REGISTERED, registration.getReference());
		framework.postServiceEvent(serviceEvent);
		
		return registration;
	}	

	ServiceRegistration registerService(Bundle bundle, String clazz, Object service, Dictionary properties) {
		return registerService(bundle, new String[] { clazz }, service, properties);
	}

	void unregisterService(ServiceRegistration registration) {
		synchronized (referencesByRegistration) {
			ServiceReference reference = (ServiceReference) referencesByRegistration.get(registration);
			
			String[] clazzez = (String[]) reference.getProperty(Constants.OBJECTCLASS);
			for (int i = 0; i < clazzez.length; i++) {
				String clazz = clazzez[i];
				
				ServiceReference[] allReferences = getAllServiceReferences(clazz);
				allReferences = (ServiceReference[]) ArrayUtil.remove(allReferences, reference);
				
				if (allReferences.length == 0) {
					referencesByClass.remove(clazz);
				} else {
					referencesByClass.put(clazz, allReferences);
				}
			}

		    /*
		     * remove ServiceRegistration entry
		     */
			referencesByRegistration.remove(registration);
			
			/*
			 * 113.6.5
			 * 
			 * Service Events must be delivered asynchronously (...).
			 */
			ServiceEvent serviceEvent = new ServiceEvent(ServiceEvent.UNREGISTERING, reference);
			framework.postServiceEvent(serviceEvent);
		}
	}

	void unregisterServices(Bundle bundle) {
		ServiceReference[] references = getRegisteredServices(bundle);
		if (references != null) {
			for (int i = 0; i < references.length; i++) {
				ServiceRegistration registration = getServiceRegistration(references[i]);
				unregisterService(registration);
			}
		}
	}

	ServiceRegistration getServiceRegistration(ServiceReference reference) {
		synchronized (referencesByRegistration) {
			Iterator it = referencesByRegistration.keySet().iterator();
			
			while (it.hasNext()) {
				ServiceRegistration registration = (ServiceRegistration) it.next();
				if (reference.equals(referencesByRegistration.get(registration))) {
					return registration;
				}
			}
			
			return null;
		}
	}

	ServiceReference[] getRegisteredServices(Bundle bundle) {
		ServiceReference[] references = null;
        try {
			String filter = new StringBuilder("(bundle.id=").append(bundle.getBundleId()).append(")").toString();
	        
			references = getAllServiceReferences(null, filter);			
		} catch (InvalidSyntaxException e) {
		}
		
		return references;
	}
	
	Object getService(ServiceReference reference) {
		synchronized (servicesByReference) {
			Object service = servicesByReference.get(reference);
			
			synchronized (usingBundlesByReference) {
				Bundle[] usingBundles = (Bundle[]) usingBundlesByReference.get(reference);
				if (usingBundles == null) {
					usingBundles = new Bundle[] {
							reference.getBundle()
					};
				} else {
					usingBundles = (Bundle[]) ArrayUtil.add(usingBundles, reference.getBundle());
				}
			
				usingBundlesByReference.put(reference, usingBundles);
			}
			
			return service;
		}
	}
	
	boolean ungetService(ServiceReference reference) {
		synchronized (usingBundlesByReference) {
			Bundle[] usingBundles = (Bundle[]) usingBundlesByReference.get(reference);
			usingBundles = (Bundle[]) ArrayUtil.add(usingBundles, reference.getBundle());
			
			usingBundlesByReference.put(reference, usingBundles);
			
			return true;
		}
	}
	
	
	private ServiceReference[] getAllServiceReferences() {
		synchronized (referencesByRegistration) {
			return referencesByRegistration.size() > 0 ? (ServiceReference[]) referencesByRegistration.values().toArray(new ServiceReference[0]) : null;
		}
	}

	ServiceReference[] getAllServiceReferences(String className) {
		synchronized (referencesByClass) {
			ServiceReference[] allReferences = (ServiceReference[]) referencesByClass.get(className);
			
			return allReferences;
		}
	}
	
	void setProperties(ServiceRegistration registration, Dictionary properties) {
		synchronized (propertiesByReference) {
			ServiceReference reference = registration.getReference();
			
			Dictionary properties0 = (Dictionary) propertiesByReference.get(reference);
			
			for (Enumeration e = properties.keys(); e.hasMoreElements();) {
				String key = (String) e.nextElement();
				
				properties0.put(key, properties.get(key));
			}
		}
	}
	
	/*
	 * ServiceReference
	 */
	
	Object getProperty(ServiceReference reference, String key) {
		synchronized (propertiesByReference) {
			Dictionary properties = (Dictionary) propertiesByReference.get(reference);
	
			return properties.get(key);
		}
	}
	
	String[] getPropertyKeys(ServiceReference reference) {
		synchronized (propertiesByReference) {
			return (String[]) ArrayUtil.toArray(String.class, (Enumeration) ((Dictionary) propertiesByReference.get(reference)).keys());
		}
	}

	public Bundle[] getUsingBundles(ServiceReference reference) {
		synchronized (usingBundlesByReference) {
			return (Bundle[]) usingBundlesByReference.get(reference);
		}
	}

	/*
	 * ServiceRegistration
	 */
	
	ServiceReference getServiceReference(ServiceRegistration registration) {
		synchronized (referencesByRegistration) {
			return (ServiceReference) referencesByRegistration.get(registration);
		}
	}
	
	/*
	 * Hooks
	 */
	
	List getEventHooks() {
		return eventHooks;
	}
	
	List getFindHooks() {
		return findHooks;
	}
	
	List getListenerHooks() {
		return listenerHooks;
	}

	ServiceReference[] getServiceReferences(Event event) {
		Collection references = new ArrayList();
		try {
			String eventTopic = event.getTopic();
			
			BundleContext context = framework.getBundleContext(); 
			/*
			 * 4.6.2  If the Framework delivers an event asynchronously, it must:
			 *   
			 *   Collect a snapshot of the listener list at the time the event is published
			 *   (rather than doing so in the future just prior to event delivery), but
			 *   before the event is delivered, so that listeners do not enter the list after
			 *   the event happened.
			 */
			String filter = new StringBuilder("(").append(EventConstants.EVENT_TOPIC).append("=").append(eventTopic).append(")").toString();
			ServiceReference[] references0 = context.getAllServiceReferences(EventHandler.class.getName(), filter);
			
			if (references0 != null) {
				Arrays.sort(references0, referencesComparator);

				NEXT_REFERENCE:	for (int i = 0; i < references0.length; i++) {
					ServiceReference reference = references0[i];
					/*
					 *   4.6.2  Ensure, at the time the snapshot is taken, that listeners on the list still belong
					 *   to active bundles at the time the event is delivered.
					 *   
					 *   4.3.2  The following code sample can be used to determine if a bundle is in the STARTING,
					 *   ACTIVE, or STOPPING state:
					 *   
					 *   
					 *   STARTING  BundleActivator#start(BundleContext)
					 *   
					 *   STOPPING  BundleActivator#stop(BundleContext)
					 *   
					 *   ACTIVE
					 */
					Bundle bundle = reference.getBundle();
					if ((bundle.getState() & (Bundle.STARTING | Bundle.ACTIVE | Bundle.STOPPING)) != 0) {
						String eventFilter = (String) reference.getProperty(EventConstants.EVENT_FILTER);
						if (eventFilter != null) {
							if (!event.matches(context.createFilter(eventFilter))) {
								continue;
							}
						}
						
						/*
						 * 5.9.2  Some bundles need to listen to all service events regardless the compatibility
						 * issues. A new type of ServiceListener is therefore added: AllServiceListener.
						 * This is a marker interface; it extends ServiceListener. Listeners that
						 * use this marker interface indicate to the Framework that they want to see all
						 * services, including services that are incompatible with them.
						 */
						String listenerClassName = (String) reference.getProperty(Constants0.LISTENERCLASS);
						if (
								listenerClassName != null && 
								listenerClassName.equals(ServiceListener.class.getName())
							) {
							ServiceEvent serviceEvent = (ServiceEvent) event.getProperty("event");
							
							String[] clazzez = (String[]) event.getProperty(Constants.OBJECTCLASS);
							for (int j = 0; j < clazzez.length; j++) {
								/*
								 * reference refers to the listener, serviceEvent.getServiceReference() to the
								 * registering bundle
								 */
								ServiceReference serviceEventReference = serviceEvent.getServiceReference();
								
								if (!serviceEventReference.isAssignableTo(reference.getBundle(), clazzez[j])) {
									continue NEXT_REFERENCE;
								}
							}
						}
						
						references.add(reference);
					}
				}
			}
		} catch (InvalidSyntaxException e) {
			framework.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
		
		return references.isEmpty() ? null : (ServiceReference[]) references.toArray(new ServiceReference[0]);
	}	
}
