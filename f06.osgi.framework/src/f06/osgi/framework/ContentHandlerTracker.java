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

import java.net.ContentHandler;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.util.tracker.ServiceTracker;

class ContentHandlerTracker extends ServiceTracker {

	public ContentHandlerTracker(BundleContext context) {
		super(context, URLStreamHandlerService.class.getName(), null);
	}
	
	public ContentHandler getContentHandler(String mimetype) {
		ServiceReference[] references = getServiceReferences();
		if (references != null) {
	    	String filter = new StringBuilder().append('(').append(URLConstants.URL_CONTENT_MIMETYPE).append('=').append(mimetype).append(')').toString();
			try {
				Filter filterIstance = context.createFilter(filter);
				
				for (int i = 0; i < references.length; i++) {
					ServiceReference reference = references[i];
					if (filterIstance.match(reference)) {
						return (ContentHandler) getService(reference);
					}
				}
			} catch (InvalidSyntaxException e) {
			}
		}
		
		return null;
	}
}
