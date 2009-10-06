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

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.ExportedPackage;

import f06.util.ArrayUtil;

class ServiceReferenceImpl implements ServiceReference {
	
	private Framework framework;
	
	private Bundle bundle;
	
	public ServiceReferenceImpl(Framework framework, Bundle bundle) {
		this.bundle = bundle;
		
		this.framework = framework;
	}
	
	public Bundle getBundle() {
		return bundle;
	}

	public Object getProperty(String key) {
		/*
		 * JavaDoc  Properties keys are case-insesitive
		 */
		return framework.getProperty(this, key);
	}

    public String[] getPropertyKeys() {
		return framework.getPropertyKeys(this);
	}
	
	public Bundle[] getUsingBundles() {
		return framework.getUsingBundles(this);
	}
	
	public boolean isAssignableTo(Bundle bundle, String className) {
		/*
		 * 6.1.23.5  Returns true if the bundle which registered the service referenced by this ServiceReference
		 * and the specified bundle use the same source for the package of the specified class name. Otherwise
		 * false is returned.
		 */	    
		if (bundle == this.bundle) {
			return true;
		}
		
	    try {
			String pkgName = FrameworkUtil.getClassPackage(className);
			ExportedPackage[] exportedPackages = framework.getExportedPackages(pkgName);
			
			if (exportedPackages != null) {
				for (int i = 0; i < exportedPackages.length; i++) {
					ExportedPackage exportedPackage = exportedPackages[i];
					
					Bundle[] importingBundles = exportedPackage.getImportingBundles();
					if (
							importingBundles != null &&
							/*
							 * Specified bundle imports the package 
							 */
							ArrayUtil.contains(importingBundles, bundle) &&
							(
									/*
									 * Registering bundle is the exporting Bundle
									 */
									exportedPackage.getExportingBundle() == this.bundle ||
									/*
									 * Registering bundle imports the package from a different Bundle
									 */
									ArrayUtil.contains(importingBundles, this.bundle)
							)
					) {
						return true;
					}
				}
			}
			
			/*
			 * The source Bundle do not still have a wire to the specified bundle (dynamic import)
			 */
			return this.bundle.loadClass(className) == bundle.loadClass(className);

		} catch (Exception e) {
//			e.printStackTrace();
//			framework.log(LogService.LOG_ERROR, getClass(), e.getMessage(), e);
		}

		return false;
	}

	public int compareTo(Object reference) {
		if (!(reference instanceof ServiceReferenceImpl)) {
			throw new IllegalArgumentException(new StringBuilder("Passed argument is not an instance of ").append(getClass().getName()).toString());
		}
		
		/*
		 * 5.11  Changes 4.1
		 * • The ServiceReference interface extends java.lang.Comparable, Service
		 * Reference objects are compared for their ranking and id according to the
		 * ordering that is used in the getServiceReference(String) method to
		 * select a service.
		 * 
		 * 5.2.5
		 * 
		 * service.ranking Integer SERVICE_RANKING When registering a service object, a
         *                                         bundle may optionally specify a
         *                                         service.ranking number as one of
         *                                         the service object’s properties. If
         *                                         multiple qualifying service interfaces
         *                                         exist, a service with the highest
         *                                         SERVICE_RANKING number, or
         *                                         when equal to the lowest
         *                                         SERVICE_ID,
		 */
		
		int ranking1 = ((Integer) getProperty(Constants.SERVICE_RANKING)).intValue();
		
		ServiceReference reference2 = (ServiceReference) reference;
		
		int ranking2 = ((Integer) reference2.getProperty(Constants.SERVICE_RANKING)).intValue();

		if (ranking1 == ranking2) {
			/*
			 * If there is a tie in ranking, the service object with the lowest
			 * SERVICE_ID (the service object that was registered first) is returned.
			 */
			long id1 = ((Long) getProperty(Constants.SERVICE_ID)).longValue();
			
			long id2 = ((Long) reference2.getProperty(Constants.SERVICE_ID)).longValue();
			
			return id1 < id2 ? -1 : (id1 == id2 ? 0 : 1);
		}
		
		return ranking2 - ranking1;
	}
	
	public boolean equals(Object obj) {
		if (!(obj instanceof ServiceReferenceImpl)) {
			return false;
		}

		return compareTo(obj) == 0;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Service(id=");
		sb.append(getProperty(Constants.SERVICE_ID));
		
		String[] objectClass = (String[]) getProperty(Constants.OBJECTCLASS);
		
		sb.append(",objectClass=[");
		for (int i = 0; i < objectClass.length; i++) {
			sb.append(objectClass[i]);
			if (i < objectClass.length - 1) {
				sb.append(",");
			}
		}
		sb.append("])");
		
		return sb.toString();
	}
}
