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

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import f06.util.SerialExecutorService;

/*
 * Reactor Design Pattern
 */

class EventDispatcher {
	
	private BundleContext context;

	protected SerialExecutorService asyncExecutor;

	protected SerialExecutorService syncExecutor;

	protected volatile boolean shutting_down;

	
	public EventDispatcher(BundleContext context) {
		this.context = context;

		syncExecutor = new SerialExecutorService("");

		asyncExecutor = new SerialExecutorService("");
	}
	
	public void syncDispatchEvent(final Event event) {
		if (shutting_down) {
			return;
		}

		syncExecutor.submit(new Runnable() {
			public void run() {
				dispatchEvent(event);
			}
		}).get();
    }
	
	public void asyncDispatchEvent(final Event event) {
		if (shutting_down) {
			return;
		}

		asyncExecutor.execute(new Runnable() {
			public void run() {
				dispatchEvent(event);
			}
		});
	}
	
	public void shutdown() {
		shutting_down = true;

		syncExecutor.shutdown();

		asyncExecutor.shutdown();
	}
	
	private void dispatchEvent(Event event) {
		Framework framework = (Framework) context.getBundle();
		ServiceReference[] references = framework.getServiceReferences(event);
		if (references != null)
		for (int i = 0; i < references.length; i++) {
			ServiceReference reference = references[i];
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
