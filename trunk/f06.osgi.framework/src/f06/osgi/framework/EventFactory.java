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

import java.util.Dictionary;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;

import f06.util.CaseSensitiveDictionary;

class EventFactory {

	public static Event createEvent(FrameworkEvent frameworkEvent) {
		/*
		 * 113.6.3  The following event properties must be set for a Framework Event.
		 * 
		 *   event – (FrameworkEvent) The original event object.
		 *   
		 * If the FrameworkEvent getBundle method returns a non-null value, the following
		 * fields must be set:
		 *
		 *   bundle.id – (Long) The source’s bundle id.
		 *   
		 *   bundle.symbolicName – (String) The source bundle's symbolic name.
		 *   Only set if the bundle’s symbolic name is not null.
		 *   
		 *   bundle – (Bundle) The source bundle.
		 *   If the FrameworkEvent getThrowable method returns a non- null value:
		 *   
		 *   exception.class – (String) The fully-qualified class name of the attached
		 *   Exception.
		 *   
		 *   exception.message –( String) The message of the attached exception.
		 *   Only set if the Exception message is not null.
		 *   
		 *   exception – (Throwable) The Exception returned by the getThrowable
		 *   method.
		 */
		Dictionary d = new CaseSensitiveDictionary(true);
		d.put("event", frameworkEvent);

		Bundle bundle = frameworkEvent.getBundle();
		d.put("bundle.id", Long.valueOf(bundle.getBundleId()));
		
		String symbolicName = bundle.getSymbolicName();
		if (symbolicName != null) {
		    d.put("bundle.symbolicName", symbolicName);
		}
		d.put("bundle", bundle);
		
		Throwable t = frameworkEvent.getThrowable();
		if (t != null) {
			d.put("exception.class", t.getClass().getName());
			
			String message = t.getMessage();
			if (message == null) {
				message = "null";
			}
			d.put("exception.message", message);			
			d.put("exception", t);			
		}

		StringBuilder message = new StringBuilder(FrameworkEvent.class.getName().replace('.', '/'));
		
		message.append('/');
		
		switch (frameworkEvent.getType()) {
		case FrameworkEvent.STARTED:
			message.append("STARTED");
			break;
		case FrameworkEvent.ERROR:
			message.append("ERROR");
			break;
		case FrameworkEvent.PACKAGES_REFRESHED:
			message.append("PACKAGES_REFRESHED");
			break;
		case FrameworkEvent.STARTLEVEL_CHANGED:
			message.append("STARTLEVEL_CHANGED");
			break;
		case FrameworkEvent.WARNING:
			message.append("WARNING");
			break;
		case FrameworkEvent.INFO:
			message.append("INFO");
			break;
		case FrameworkEvent.STOPPED:
			message.append("STOPPED");
			break;
		case FrameworkEvent.STOPPED_UPDATE:
			message.append("STOPPED_UPDATE");
			break;
		case FrameworkEvent.STOPPED_BOOTCLASSPATH_MODIFIED:
			message.append("STOPPED_BOOTCLASSPATH_MODIFIED");
			break;
		}

		Event event = new Event(message.toString(), d);
		
		return event;
	}
	
	public static Event createEvent(BundleEvent bundleEvent) {
		/*
		 * 113.6.4  The following event properties must be set for a Bundle Event. If listeners
	     * require synchronous delivery then they should register a Synchronous Bundle
		 * Listener with the Framework.
		 *   
		 *   event – (BundleEvent) The original event object.
		 *   
		 *   bundle.id – (Long) The source’s bundle id.
		 *   
		 *   bundle.symbolicName – (String) The source bundle's symbolic name.
		 * 
		 * Only set if the bundle’s symbolic name is not null.
		 *   
		 *   bundle – (Bundle) The source bundle.
		 */
		Dictionary d = new CaseSensitiveDictionary(true);
		d.put("event", bundleEvent);
		
		Bundle bundle = bundleEvent.getBundle();
		d.put("bundle.id", Long.valueOf(bundle.getBundleId()));
		
		String symbolicName = bundle.getSymbolicName();
		if (symbolicName != null) {
			d.put("bundle.symbolicName", symbolicName);
		}

		d.put("bundle", bundle);
		
		StringBuilder message = new StringBuilder(BundleEvent.class.getName().replace('.', '/'));
		
		message.append('/');
		
		switch (bundleEvent.getType()) {
		case BundleEvent.INSTALLED:
			message.append("INSTALLED");
			break;
		case BundleEvent.LAZY_ACTIVATION:
			message.append("LAZY_ACTIVATION");
			break;
		case BundleEvent.STARTED:
			message.append("STARTED");
			break;
		case BundleEvent.STARTING:
			message.append("STARTING");
			break;
		case BundleEvent.STOPPING:
			message.append("STOPPING");
			break;
		case BundleEvent.STOPPED:
			message.append("STOPPED");
			break;
		case BundleEvent.UPDATED:
			message.append("UPDATED");
			break;
		case BundleEvent.UNINSTALLED:
			message.append("UNINSTALLED");
			break;
		case BundleEvent.RESOLVED:
			message.append("RESOLVED");
			break;
		case BundleEvent.UNRESOLVED:
			message.append("UNRESOLVED");
			break;
		} 

		String topic = message.toString();
		Event event = new Event(topic, d);
		
		return event;
	}
	
	public static Event createEvent(ServiceEvent serviceEvent) {
		/*
		 * 113.6.5  
		 *   
		 *   event – (ServiceEvent) The original Service Event object.
		 *   
		 *   service – (ServiceReference) The result of the getServiceReference
		 *   method
		 *   
		 *   service.id – (Long) The service's ID.
		 *   
		 *   service.pid – (String) The service's persistent identity. Only set if not
		 *   null.
		 *   
		 *   service.objectClass – (String[]) The service's object class.
		 */
		Dictionary d = new CaseSensitiveDictionary(true);
		d.put("event", serviceEvent);

		ServiceReference reference = serviceEvent.getServiceReference();
		d.put("service", reference);

		d.put("service.objectClass", reference.getProperty(Constants.OBJECTCLASS));

		String[] propertyKeys = reference.getPropertyKeys();
		for (int i = 0; i < propertyKeys.length; i++) {
			String propertyKey = propertyKeys[i]; 
			
			d.put(propertyKey, reference.getProperty(propertyKey));
		}
		
		StringBuilder message = new StringBuilder(ServiceEvent.class.getName().replace('.', '/'));
		
		message.append('/');
		
		switch (serviceEvent.getType()) {
		case ServiceEvent.REGISTERED:
			message.append("REGISTERED");
			break;
		case ServiceEvent.MODIFIED:
			message.append("MODIFIED");
			break;
		case ServiceEvent.UNREGISTERING:
			message.append("UNREGISTERING");
			break;
		} 

		Event event = new Event(message.toString(), d);
		
		return event;
	}

	final static String[] FRAMEWORK_EVENT_TOPICS = new String[] { 
		"org/osgi/framework/FrameworkEvent/STARTED", 
		"org/osgi/framework/FrameworkEvent/ERROR", 
		"org/osgi/framework/FrameworkEvent/PACKAGES_REFRESHED", 
		"org/osgi/framework/FrameworkEvent/STARTLEVEL_CHANGED", 
		"org/osgi/framework/FrameworkEvent/WARNING", 
		"org/osgi/framework/FrameworkEvent/INFO",
		"org/osgi/framework/FrameworkEvent/STOPPED",
		"org/osgi/framework/FrameworkEvent/STOPPED_UPDATE",
		"org/osgi/framework/FrameworkEvent/STOPPED_BOOTCLASSPATH_MODIFIED",
	};
	final static String[] BUNDLE_EVENT_TOPICS = new String[] {
		"org/osgi/framework/BundleEvent/INSTALLED",
		"org/osgi/framework/BundleEvent/STARTED", 
		"org/osgi/framework/BundleEvent/STOPPED", 
		"org/osgi/framework/BundleEvent/UPDATED", 
		"org/osgi/framework/BundleEvent/UNINSTALLED", 
		"org/osgi/framework/BundleEvent/RESOLVED", 
		"org/osgi/framework/BundleEvent/UNRESOLVED"
	};
	final static String[] SYNCHRONOUS_BUNDLE_EVENT_TOPICS = new String[] {
		"org/osgi/framework/BundleEvent/STOPPING", 		
		"org/osgi/framework/BundleEvent/INSTALLED",
		"org/osgi/framework/BundleEvent/STARTING", 		
		"org/osgi/framework/BundleEvent/LAZY_ACTIVATION", 		
		"org/osgi/framework/BundleEvent/STARTED", 
		"org/osgi/framework/BundleEvent/RESOLVED", 
		"org/osgi/framework/BundleEvent/STOPPED", 
		"org/osgi/framework/BundleEvent/UPDATED", 
		"org/osgi/framework/BundleEvent/UNINSTALLED", 
		"org/osgi/framework/BundleEvent/RESOLVED", 
		"org/osgi/framework/BundleEvent/UNRESOLVED"
	};
	final static String [] SERVICE_EVENT_TOPICS = new String[] {
		"org/osgi/framework/ServiceEvent/REGISTERED",
		"org/osgi/framework/ServiceEvent/MODIFIED",
		"org/osgi/framework/ServiceEvent/UNREGISTERING",
	};
}
