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
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.RequiredBundle;

class RequiredBundleImpl implements RequiredBundle {
	
	private Bundle bundle;
	
	private Bundle[] requiringBundles;
	
	private boolean removalPending;

	public RequiredBundleImpl(Bundle bundle) {
		this.bundle = bundle;
	}
	
	public Bundle getBundle() {
		return bundle;
	}

	public Bundle[] getRequiringBundles() {
		return requiringBundles;
	}

	public String getSymbolicName() {
		return bundle.getSymbolicName();
	}

	public Version getVersion() {
		return bundle.getVersion();
	}

	public boolean isRemovalPending() {
		return removalPending;
	}

	void setRemovalPending0(boolean removalPending) {
		this.removalPending = removalPending;
	}
	
	void setRequiringBundles0(Bundle[] requiringBundles) {
		this.requiringBundles = requiringBundles;
	}

}
