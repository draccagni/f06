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
import java.util.Enumeration;

import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import f06.util.CaseSensitiveDictionary;

class ServiceRegistrationImpl implements ServiceRegistration {

	private Framework framework;
	
	public ServiceRegistrationImpl(Framework framework) {
		this.framework = framework;
	}
	
	public ServiceReference getReference() {
		return framework.getReference(this);
	}

	public void setProperties(Dictionary properties) {
		/* JavaDoc  throws IllegalStateException If this ServiceRegistration object 
		 * has already been unregistered.
		 */
		ServiceReference reference = getReference();
		
		if (reference == null) {
			throw new IllegalStateException("Trying to modify properties of an UNREGISTERED Service.");
		}
		
		String[] keys = reference.getPropertyKeys();
		
		Dictionary d = new CaseSensitiveDictionary(false); 

		/* JavaDoc  throws IllegalArgumentException If properties contains case variants
		 * of the same key name.
		 */
		for (Enumeration e = properties.keys(); e.hasMoreElements(); )  {
			String key = ((String) e.nextElement());
			for (int i = 0; i < keys.length; i++) {
				String key0 = keys[i];
				if (key.equalsIgnoreCase(key0) && !key.equals(key0)) {
					throw new IllegalArgumentException(new StringBuilder("Property ").append(key).append(" already defined with case variants in Service(id=").append(reference.getProperty(Constants.SERVICE_ID)).append(")").toString());
				}
			}
			d.put(key, properties.get(key));
		}
		
		framework.setProperties(this, d);
		
		/*
		 * JavaDoc  The service's properties are replaced with the provided properties.
		 * A service event of type ServiceEvent.MODIFIED is fired.
		 */
		ServiceEvent serviceEvent = new ServiceEvent(ServiceEvent.MODIFIED, reference);
		framework.postServiceEvent(serviceEvent);
	}

	public void unregister() {
		framework.unregisterService(this);
	}
}
