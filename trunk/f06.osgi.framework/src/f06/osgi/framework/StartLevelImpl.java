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

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

import f06.util.SerialExecutorService;

public class StartLevelImpl implements StartLevel {

	private Framework framework;
	
	private SerialExecutorService executor;

	private int activeStartLevel;

	private int initialBundleStartLevel;
	
	
	public StartLevelImpl(BundleContext context) {
		framework = (Framework) context.getBundle();

		activeStartLevel = 0;
		
		String str = context.getProperty("org.osgi.framework.startlevel.initialbundle");
		
		initialBundleStartLevel = str != null ? Integer.parseInt(str) : 1;
		
		executor = new SerialExecutorService(getClass().getName());
	}
	
	public boolean isBundleActivationPolicyUsed(Bundle bundle) {
		return framework.isBundleActivationPolicyUsed(bundle); 
	}
	
	public int getBundleStartLevel(Bundle bundle) {
		return framework.getBundleStartLevel(bundle);
	}

	public int getInitialBundleStartLevel() {
		return initialBundleStartLevel;
	}

	public int getStartLevel() {
		return activeStartLevel;
	}
	
	public boolean isBundlePersistentlyStarted(Bundle bundle) {
		if (bundle.getState() == Bundle.UNINSTALLED) {
			throw new IllegalArgumentException(new StringBuilder(bundle.toString()).append(" has been uninstalled.").toString());
		}
		
		return framework.isBundlePersistentlyStarted(bundle);
	}

	public void setBundleStartLevel(Bundle bundle, int startlevel) {
        /*
         * 4.8.1.1
         */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(bundle, AdminPermission.EXECUTE));
		}		

		if (startlevel < 0L) {
			throw new IllegalArgumentException(new StringBuilder("Specified level is less then 0 (=").append(startlevel).append(").").toString());
		} else if (bundle.getState() == Bundle.UNINSTALLED) {
			/* 8.6.1.5  Throws IllegalArgumentException – If the specified bundle has been uninstalled
			 * or if the specified start level is less than or equal to zero, or the specified bundle
			 * is the system bundle.
			 */
			throw new IllegalArgumentException(new StringBuilder("Bundle (id=").append(bundle.getBundleId()).append(") has been uninstalled.").toString());
		} else if (bundle.getBundleId() == 0L) {
			/*
			 * 8.2.8  The System Bundle is defined to have a start level of zero. The start level of
			 * the System Bundle cannot be changed. An IllegalArgumentException must
			 * be thrown if an attempt is made to change the start level of the System Bundle.
			 */ 
			throw new IllegalArgumentException("System bundle start level cannot be changed.");
		} 

		framework.setBundleStartLevel(bundle, startlevel);

		int activeStartLevel = framework.getStartLevel();
		
		/*
		 * 8.2.5  When a bundle’s start level is changed and the bundle
		 * is marked persistently to be started, then the OSGi Framework must
		 * compare the new bundle start level to the active start level. For example,
		 * assume that the active start level is 5 and a bundle with start level 5 is
		 * started. If the bundle’s start level subsequently is changed to 6 then this bundle
		 * must be stopped by the OSGi Framework but it must still be marked persistently
		 * to be started.
		 */
		if (activeStartLevel > 0 && startlevel <= activeStartLevel) {
			try {
				/*
				 * JavaDoc  They are started as described in the start(int) method using 
				 * the Bundle.START_TRANSIENT option. The Bundle.START_ACTIVATION_POLICY 
				 * option must also be used if isBundleActivationPolicyUsed(Bundle) returns true
                 * for the bundle.
				 */
				int options = Bundle.START_TRANSIENT;
				if (isBundleActivationPolicyUsed(bundle)) {
					options |= Bundle.START_ACTIVATION_POLICY;
				}
				
				bundle.start(options);
			} catch (BundleException e) {
				FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, bundle, e);
				framework.postFrameworkEvent(frameworkEvent);
			}
		} else {
			try {
				bundle.stop(Bundle.STOP_TRANSIENT);
			} catch (BundleException e) {
				FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, bundle, e);
				framework.postFrameworkEvent(frameworkEvent);
			}
		}
		
	}

	public void setInitialBundleStartLevel(int startlevel) {
	       /*
         * 4.8.1.1
         * 
         * ??? "*"
         */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission("*", AdminPermission.STARTLEVEL));
		}		

		initialBundleStartLevel = startlevel;
	}

	public void setStartLevel(final int requestedStartLevel) {
        /*
         * 4.8.1.1
         * 
         * ??? "*"
         */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission("*", AdminPermission.STARTLEVEL));
		}		

		int activeStartLevel = framework.getStartLevel();
		if (requestedStartLevel == activeStartLevel) {
			FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, framework, null);
			/*
		     * 8.2.2  If the requested start level and active start level are equal, then this 
		     * event may arrive before the setStartLevel method has returned.
		     */
			framework.postFrameworkEvent(frameworkEvent);
		} else {
			/*
			 * 8.2.2  The process of starting or stopping bundles, which is initiated by the 
			 * setStartLevel(int) method, must take place asynchronously.
			 */
			executor.execute(new Runnable() {
				public void run() {
					setStartLevel0(requestedStartLevel);
				}
			});
		}
	}
	
	public void setStartLevel0(int requestedStartLevel) {
	    BundleContext context = framework.getBundleContext();

	    if (activeStartLevel < requestedStartLevel) {
			while (activeStartLevel < requestedStartLevel) {
				++activeStartLevel;

				/*
				 * get bundles at each step to take into account new installed
				 * bundles by BundleActivator
				 */
				Bundle[] bundles = context.getBundles();
				for (int i = 1; i < bundles.length; i++) {
					AbstractBundle bundle = (AbstractBundle) bundles[i];
					
					if (framework.getBundleType(bundle) == PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
						continue;
					}
					
					/*
					 * 8.2.1  A bundle cannot run unless it is marked
					 * started, regardless of the bundle’s start level.
					 */
					int startlevel = getBundleStartLevel(bundle);
					if (
							(
									bundle.getState() == Bundle.INSTALLED || 
									bundle.getState() == Bundle.RESOLVED 
							)  &&
							isBundlePersistentlyStarted(bundle) &&
							startlevel <= activeStartLevel) {
						try {
							/*
							 * JavaDoc  They are started as described in the start(int) method using 
							 * the Bundle.START_TRANSIENT option. The Bundle.START_ACTIVATION_POLICY 
							 * option must also be used if isBundleActivationPolicyUsed(Bundle) returns true
			                 * for the bundle.
							 */
							int options = Bundle.START_TRANSIENT;
							if (isBundleActivationPolicyUsed(bundle)) {
								options |= Bundle.START_ACTIVATION_POLICY;
							}
							
							bundle.start(options);
						} catch (BundleException e) {
							/*
							 * 8.2.7  Exceptions in the Bundle Activator
							 * If the BundleActivator.start or stop method throws an Exception, then the
							 * handling of this Exception is different depending who invoked the start or
							 * stop method.
							 * If the bundle is started/stopped due to a change in the active start level or
							 * the bundle’s start level, then the Exception must be wrapped in a
							 * BundleException and broadcast as a FrameworkEvent.ERROR event.
							 */
							
							FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, bundle, e);
							framework.postFrameworkEvent(frameworkEvent);
							continue;
						}
					}
				}
			}
		} else {
			while (activeStartLevel > requestedStartLevel) {
				--activeStartLevel;

				/*
				 * get bundles at each step to take into account uninstalled
				 * bundles by BundleActivator
				 */
				Bundle[] bundles = context.getBundles();
				for (int i = 1; i < bundles.length; i++) {
					Bundle bundle = bundles[i];
					
					if (framework.getBundleType(bundle) == PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
						continue;
					}
					
					int bundleStartLevel = getBundleStartLevel(bundle);
					if (bundle.getState() == Bundle.ACTIVE && bundleStartLevel > activeStartLevel) {
						try {
							bundle.stop(Bundle.STOP_TRANSIENT);
						} catch (BundleException e) {
							FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, bundle, e);
							framework.postFrameworkEvent(frameworkEvent);
						}
					}
				}
			}
		}
		
		FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, framework, null);
		framework.sendFrameworkEvent(frameworkEvent);
	}
}
