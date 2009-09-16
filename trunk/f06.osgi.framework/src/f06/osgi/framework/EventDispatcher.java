/*
 * Copyright (c) Davide Raccagni (2006, 2008). All Rights Reserved.
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

import java.awt.EventQueue;
import java.util.Collection;
import java.util.Iterator;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import f06.util.ThreadPoolExecutor;

/*
 * Reactor Design Pattern
 */

class EventDispatcher {
		
	private BundleContext context;

	private ThreadPoolExecutor executor;

	public EventDispatcher(BundleContext context) {
		this.context = context;
		
		executor = new ThreadPoolExecutor(getClass().getName(), 7); // must be > 1 : deadlock due to shutdown procedure
	}
	
	public void syncDispatchEvent(Event event) {
		dispatchEvent(event);
    }
	
	public synchronized void asyncDispatchEvent(final Event event) {
		executor.execute(new Runnable() {
			public void run() {
				dispatchEvent(event);
			}
		});
	}
	
	public void shutdown() throws InterruptedException {
		executor.shutdown();
	}
	
	private void dispatchEvent(Event event) {
		Framework framework = (Framework) context.getBundle();
		Collection serviceReferences = framework.getServiceReferences(event);
		Iterator it = serviceReferences.iterator();
		while (it.hasNext()) {
			ServiceReference reference = (ServiceReference) it.next();
			EventHandler eventHandler = (EventHandler) context.getService(reference);
			if (eventHandler != null) {
				/*
				 * if has not removed in the meanwhile
				 */
			    eventHandler.handleEvent(event);
			}
			
			context.ungetService(reference);
		}
	}
}
