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
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.util.tracker.ServiceTracker;

class URLStreamHandlerServiceTracker extends ServiceTracker {
	
	public URLStreamHandlerServiceTracker(BundleContext context) {
		super(context, URLStreamHandlerService.class.getName(), null);
	}
	
	public URLStreamHandlerService getURLStreamHandlerService(String protocol) {
		ServiceReference[] references = getServiceReferences();
		if (references != null) {
			String filter = new StringBuilder().append('(').append(URLConstants.URL_HANDLER_PROTOCOL).append('=').append(protocol).append(')').toString();
			try {
				Filter filterInstance = context.createFilter(filter);

				for (int i = 0; i < references.length; i++) {
					ServiceReference reference = references[i];
					if (filterInstance.match(reference)) {
						return (URLStreamHandlerService) getService(reference);
					}
				}
			} catch (InvalidSyntaxException e) {
			}
		}
		
		return null;
	}
}
