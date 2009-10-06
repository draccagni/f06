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
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/*
 * Reactor Design Pattern
 */
public class EventAdminImpl implements EventAdmin {
		
	private BundleContext context;

	public EventAdminImpl(BundleContext context) {
		this.context = context;
	}
	
	public void postEvent(Event event) {
		Framework framework = (Framework) context.getBundle();

		framework.postEvent(event);
    }
	
	public synchronized void sendEvent(Event event) {
		Framework framework = (Framework) context.getBundle();

		framework.sendEvent(event);
	}
}
