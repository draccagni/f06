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
import org.osgi.framework.hooks.service.ListenerHook;

public class ListenerHookInfoImpl implements ListenerHook.ListenerInfo {
	
	private BundleContext context;
	
	private String filter;
	
	private boolean removed;
	
	public ListenerHookInfoImpl(BundleContext context, String filter) {
		this.context = context;
		this.filter = filter;
	}

	public BundleContext getBundleContext() {
		return context;
	}
	
	public String getFilter() {
		return filter;
	}
	
	public boolean isRemoved() {
		return removed;
	}
	
	void setRemoved(boolean removed) {
		this.removed = removed;
	}
}
