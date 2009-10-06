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

import java.lang.reflect.AccessibleObject;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;

import f06.util.ManifestEntry;

public class FrameworkUtil {
	
	private static class FilterFactory {
		
		private static Map filterInstances = new HashMap();

		public static Filter createFilter(String filter) throws InvalidSyntaxException {
			Filter filterInstance = (Filter) filterInstances.get(filter);
			if (filterInstance == null) {
				filterInstance = new RFC1960Filter(filter);
				filterInstances.put(filter, filterInstance);
			}
			
			return filterInstance;
		}
	}
	
	static boolean isFragmentHost(Dictionary headers) {
		return headers.get(Constants.FRAGMENT_HOST) != null;
	}
	
	public static Filter createFilter(String filter) throws InvalidSyntaxException {
		return FilterFactory.createFilter(filter);
	}
	
	static String getSymbolicName(Dictionary headers) throws Exception {
		ManifestEntry[] entries = ManifestEntry.parse(headers.get(Constants.BUNDLE_SYMBOLICNAME));
		if (entries != null) {
			return entries[0].getName();
		}
		
		return null;
	}
	
	static boolean isSingleton(Bundle bundle) throws Exception {
		ManifestEntry[] entries = ManifestEntry.parse(bundle.getHeaders().get(Constants.BUNDLE_SYMBOLICNAME));
		if (entries != null) {
			if (entries[0].hasAttribute(Constants.SINGLETON_DIRECTIVE)) {
				return Boolean.parseBoolean(entries[0].getAttributeValue(Constants.SINGLETON_DIRECTIVE));
			}
		}
		
		return false;
	}
	
	public static void setAccessible(final AccessibleObject object, final boolean accesible) {
		if (!object.isAccessible() && accesible) {
			AccessController.doPrivileged(new PrivilegedAction() {
				public Object run() {
					object.setAccessible(accesible);
					
					return null;
				}
			});
		}
	}
	
	public static String getClassPackage(String className) {
        if (className == null) {
            className = "";
        }
        
        int i = className.lastIndexOf('.');
        
        return i == -1 ? "" : className.substring(0, i);
    }

	public static String getClassPackage(Class clazz) {
		return getClassPackage(clazz.getName());
	}
	
	static String getResourcePackage(String resource) {
        if (resource == null) {
            resource = "";
        }

        if (resource.startsWith("/")) {
        	resource = resource.substring(1);
        }
        
        int i = resource.lastIndexOf('/');
        
        String pkgName = i == -1 ? "" : resource.substring(0, i).replace('/', '.');
        
        return pkgName;
    }
}