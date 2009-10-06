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
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;

import f06.util.ArrayUtil;

class PermissionAdminImpl implements PermissionAdmin {

	public static final PermissionInfo[] ALL_PERMISSIONS = new PermissionInfo[] {
		new PermissionInfo("java.security.AllPermission", Constants0.WILDCARD, Constants0.WILDCARD) 
	};
	
	protected Framework framework;
	
	public PermissionAdminImpl(BundleContext context) {
		this.framework = (Framework) context.getBundle();
	}

	public PermissionInfo[] getPermissions(final String location) {
		return framework.getPermissions(location);
	}

	public void setPermissions(final String location, final PermissionInfo[] permissions) {
		if (location == null) {
			throw new NullPointerException();
		}

		framework.setPermissions(location, permissions);
	}

	public String[] getLocations() {
		return framework.getLocations();
	}

	public PermissionInfo[] getDefaultPermissions() {
		return framework.getDefaultPermissions();
	}

	public void setDefaultPermissions(final PermissionInfo[] permissions) {
		try {
			List list = new ArrayList();
			
			BundleContext context = framework.getBundleContext();
			Bundle[] bundles = context.getBundles();
			
			PermissionInfo[] oldDefaultPermissions = framework.getDefaultPermissions();
			framework.setDefaultPermissions(permissions);
			for (int i = 0; i < bundles.length; i++) {
				Bundle bundle = bundles[i];
				
				if (bundle.getState() == Bundle.ACTIVE) {
					/*
					 * 10.1.3  If the default permissions are changed, a bundle
		             * with no specific permissions must immediately start using the new
		             * default permissions.
					 */
					PermissionInfo[] bundlePermissions = getPermissions(bundle.getLocation());
					if (ArrayUtil.equals(bundlePermissions, oldDefaultPermissions)) {
						bundle.stop();

						bundle.start();
					}
				}
			}
		} catch (Exception e) {
			// TODO -> Log
		}
	}
}
