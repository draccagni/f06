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

import java.lang.reflect.Constructor;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.osgi.service.permissionadmin.PermissionInfo;

import f06.util.ArrayUtil;

public class BundlePermissionCollection extends PermissionCollection {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7966028914801955822L;

	private static Map constructorsByClassName = new HashMap();
	
	private static Class[] PARAMETER_TYPES_0_CLASS_STRING = new Class[0];
	
	private static Class[] PARAMETER_TYPES_1_CLASS_STRING = new Class[] {
		String.class
	}; 
	
	private static Class[] PARAMETER_TYPES_2_CLASS_STRING = new Class[] {
		String.class,
		String.class
	}; 
	
	private transient PermissionInfo[] permissionInfos;
	
	private boolean hasAllPermissions;
	
	BundlePermissionCollection(PermissionInfo[] permissionInfos) {
		this.permissionInfos = permissionInfos;
		
		for (int i = 0; i < permissionInfos.length; i++) {
			if (permissionInfos[i].equals(PermissionAdminImpl.ALL_PERMISSIONS[0])) {
				hasAllPermissions = true;
				break;
			}
		}
	}
	
	public void add(Permission permission) {
		// do nothing
	}

	public Enumeration elements() {
		return ArrayUtil.toEnumeration(permissionInfos);
	}

	public boolean implies(Permission permission) {
		if (hasAllPermissions) {
			return true;
		}
		
		try {
			Class cls = permission.getClass();
			String className = cls.getName();
			
			Constructor constructor = (Constructor) constructorsByClassName.get(className);		
			if (constructor == null) {
				constructor = cls.getConstructor(PARAMETER_TYPES_2_CLASS_STRING); 
				if (constructor != null) {
				} else {
					constructor = cls.getConstructor(PARAMETER_TYPES_1_CLASS_STRING);
					
					if (constructor == null) {
						constructor = cls.getConstructor(PARAMETER_TYPES_0_CLASS_STRING);
					}
				}
				
				constructorsByClassName.put(className, constructor);
			}
			
			int argsNumber = constructor.getParameterTypes().length;
			String args[] = new String[argsNumber];
			
			Permission perm = null;
			for (int i = 0; i < permissionInfos.length; i++) {
				if (permissionInfos[i].getType().equals(className)) {
					/*
					 * 
					 */
					if (argsNumber > 0)
						args[0] = permissionInfos[i].getName();
					if (argsNumber > 1)
						args[1] = permissionInfos[i].getActions();
					
					perm = (Permission) constructor.newInstance((Object[]) args);
					
					if (perm.implies(permission)) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

		return false;
	}

}
