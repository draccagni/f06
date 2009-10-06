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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.Enumeration;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.RequiredBundle;

import f06.util.ManifestEntry;
import f06.util.TextUtil;

/*
 * 3.2  A bundle is deployed as a Java ARchive (JAR) file. JAR files are used to store
 * applications and their resources in a standard ZIP-based file format. This format is defined
 * by [27] Zip File Format. A bundle is a JAR file that:
 */
class HostBundle extends AbstractBundle {

	private BundleActivator activator;
	
	protected BundleContext context;
	
	protected BundleClassLoader classLoader;
	
	protected volatile boolean activationTriggered;

	public HostBundle(Framework framework) {
		super(framework);
    }
	
	boolean resolve() {
		return framework.resolveBundles(new Bundle[] { this });
	}
	
	public URL getResource(String name) {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.RESOURCE));
		}		
		
		/*
		 * JavaDoc  This bundle's class loader is called to search for the specified
		 * resource. If this bundle's state is INSTALLED, this
		 * method must attempt to resolve this bundle before attempting to get the
		 * specified resource. If this bundle cannot be resolved, then only this
		 * bundle must be searched for the specified resource. Imported packages
		 * cannot be searched when this bundle has not been resolved.
		 */
		if (getState() == INSTALLED) {
			if (!resolve()) {
				return getEntry(name);
			}
		}
		
		ClassLoader classLoader = getClassLoader();
		
		return classLoader.getResource(name);
	}

	public Enumeration getResources(String name) throws IOException {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.RESOURCE));
		}
		
		/*
		 * JavaDoc  This bundle's class loader is called to search for the specified
		 * resources. If this bundle's state is INSTALLED, this
		 * method must attempt to resolve this bundle before attempting to get the
		 * specified resources. If this bundle cannot be resolved, then only this
		 * bundle must be searched for the specified resources. Imported packages
		 * cannot be searched when a bundle has not been resolved.
		 */
		if (getState() == INSTALLED) {
			if (!resolve()) {
				BundleURLClassPath classPath = framework.getBundleURLClassPath(this);
					
				return classPath.findEntries("", name, false);
			}
		}

		ClassLoader classLoader = getClassLoader();
		
		return classLoader.getResources(name);
	}

	public Class loadClass(String name) throws ClassNotFoundException {
		/*
		 * 4.8.1
		 */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
		    securityManager.checkPermission(new AdminPermission(this, AdminPermission.CLASS));
		}
		
		/*
		 * 6.1.4.22  If this bundle’s state is INSTALLED, this method must attempt to resolve the
		 * bundle before attempting to load the class.
		 */
		if (getState() == INSTALLED) {
			if (!resolve()) {
				/*
				 * If this bundle cannot be resolved, a Framework event of type FrameworkEvent.
				 * ERROR is fired containing a BundleException with details of the
				 * reason this bundle could not be resolved. This method must then throw a
				 * ClassNotFoundException.
				 */
				FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, this, new BundleException(new StringBuilder("Bundle(id=").append(getBundleId()).append(") cannot be resolved.").toString()));
				framework.postFrameworkEvent(frameworkEvent);

				throw new ClassNotFoundException(new StringBuilder("Bundle(id=").append(getBundleId()).append(") cannot be resolved.").toString());
			}
		} else if (getState() == UNINSTALLED) {
			/*
			 * If this bundle’s state is UNINSTALLED, then an IllegalStateException is
		     * thrown.
			 */
			throw new IllegalStateException(new StringBuilder("Attempting to load a class from uninstalled Bundle(id=").append(getBundleId()).append(").").toString());
		}
		
		ClassLoader classLoader = getClassLoader();
		
		return classLoader.loadClass(name);
	}

	public void start(int options) throws BundleException {
        /*
         * 4.8.1.1
         */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.EXECUTE));
		}

		/*
		 * JavaDoc  Starts this bundle.
		 * 
		 * If this bundle's state is UNINSTALLED then an IllegalStateException is thrown.
		 * 
		 * 
		 * 4.3.2  UNINSTALLED – The bundle has been uninstalled. It cannot move into another state.
		 */    	
		if (getState() == UNINSTALLED) {
			throw new IllegalStateException(new StringBuilder(this.toString()).append(" has been uninstalled. It can't move to another state.").toString());
		}		

		/*
		 * JavaDoc  If the Framework implements the optional Start Level service and the
		 * current start level is less than this bundle's start level:
		 */
	    
	    if ((options & START_TRANSIENT) != 0) {
    		/*
			 * JavaDoc  If the START_TRANSIENT option is set, then a BundleException is thrown indicating 
			 * this bundle cannot be started due to the Framework's current start level.
			 */
	    	if (framework.getStartLevel() < framework.getBundleStartLevel(this)) {
	    		throw new BundleException(new StringBuilder(this.toString()).append(" cannot be started due to the Framework's current start level.").toString());
    	    }
    	} else {
	    	framework.setBundleAutostartSetting(this, options == START_ACTIVATION_POLICY ? AbstractBundle.STARTED_DECLARED : AbstractBundle.STARTED_EAGER);    	    		
    	}

		/*
		 * JavaDoc  If this bundle is in the process of being activated or deactivated
		 * then this method must wait for activation or deactivation to complete
		 * before continuing. If this does not occur in a reasonable time, a
		 * BundleException is thrown to indicate this bundle was unable to be started.
		 * 
		 * 
		 * JavaDoc  If the {@link #START_ACTIVATION_POLICY} option is set and this
         * bundle's declared activation policy is lazy then:
         * 
	     * If this bundle's state is STARTING then this method returns immediately.
		 */
		if (getState() == STARTING) {
			if (framework.isBundleActivationPolicyUsed(this)) {
				return;
			} else {
				/*
				 * 15 seconds
				 */
				synchronized (this) {
					try {
						wait(15 * 1000);
					} catch (InterruptedException e) {
						framework.log(LogService.LOG_ERROR, e.getMessage(), e);
					}
				}
				
				if (getState() != ACTIVE) {
				    throw new BundleException(new StringBuilder(this.toString()).append(" was unable to be started.").toString());
				}
			}
		} else if (getState() == STOPPING) {
			try {
				/*
				 * 1 minute
				 */
				Thread.sleep(60 * 1000);
			} catch (InterruptedException e) {
			}
			
			if (getState() != RESOLVED) {
			    throw new BundleException(new StringBuilder(this.toString()).append(" was unable to be stopped.").toString());
			}
			
			return;
		}
		
		/* 
		 * JavaDoc  If this bundle's state is ACTIVE then this method returns immediately.
		 */
		if (getState() == ACTIVE) {
			return;
		}
		
		/* 
		 * JavaDoc  If this bundle's state is not RESOLVED, an attempt is made to 
		 * resolve this bundle's package dependencies. If the Framework cannot resolve this bundle,
		 * a BundleException is thrown.
		 */				    
		else if (getState() == INSTALLED) {
			if (!resolve()) {
				throw new BundleException(new StringBuilder(this.toString()).append(" cannot be resolved.").toString());
			}
		}
		
		/*
		 * 8.2.6  The OSGi Framework must not actually start a bundle when the active 
		 * start level is less than the bundle’s start level. In that case, the state 
		 * must not change.
		 * 
		 * 
		 * JavaDoc  When the Framework's current start level becomes equal to or more than
		 * this bundle's start level, this bundle will be started.
		 */
	    if ((options & START_TRANSIENT) != 0) {
    		/*
			 * JavaDoc  If the START_TRANSIENT option is set, then a BundleException is thrown indicating 
			 * this bundle cannot be started due to the Framework's current start level.
			 */
	    	if (framework.getStartLevel() < framework.getBundleStartLevel(this)) {
	    		return;
    	    }
	    }
		/*
		 * 4.3.7.1  When a bundle is started using a lazy
		 * activation policy, the following steps must be taken:
		 * 
		 *   A Bundle Context is created for the bundle.
		 *   
		 *   The bundle state is moved to the STARTING state.
		 */
		BundleContext context = framework.createBundleContext(this);
		setBundleContext(context);
		
		setState(STARTING);
		
        if (framework.isBundleActivationPolicyUsed(this)) {
        	String activationPolicy = (String) getHeaders().get(Constants.BUNDLE_ACTIVATIONPOLICY);
        	if (activationPolicy != null) {
    			/*
    			 * 4.3.7.1  The LAZY_ACTIVATION event is fired.
    			 *   
    			 *          The system waits for a class load from the bundle to occur.
    			 */
    			BundleEvent eventStarting = new BundleEvent(BundleEvent.LAZY_ACTIVATION, this);
    			framework.postBundleEvent(eventStarting);
        	} else {
    			/*
    			 * 4.3.7  If no Bundle-ActivationPolicy header is specified, the bundle will use eager 
    			 * activation.
    			 */
        		activate();
        	}
        } else {
        	/*
        	 * 4.3.5  0 – Start the bundle with eager activation and set the autostart setting to
        	 * Started with eager activation. If the bundle was already started with the
        	 * lazy activation policy and is awaiting activation, then it must be activated
        	 * immediately.
        	 */
        	activate();
        }
	}
	
	void activate() throws BundleException {
		if (isActivationTriggered()) {
			return;
		}
		
		setActivationTriggered(true);
		
		/*
	     * 4.3.5  If the bundle is resolved, the bundle must be activated by calling its Bundle
	     * Activator object, if one exists.
	     */
		final String className = (String) getHeaders().get(Constants.BUNDLE_ACTIVATOR);
		if (className != null) {
			/*
			 * 4.3.7.1  The normal STARTING event is fired.
			 * 
			 * 
			 * JavaDoc  This event is only delivered to SynchronousBundleListeners. It is not delivered to 
			 * BundleListeners.
			 * 
			 * 
			 * see BundleContextImpl.SYNCHRONOUS_BUNDLE_EVENT_TOPICS
			 */
			BundleEvent eventStarting = new BundleEvent(BundleEvent.STARTING, this);
			framework.sendBundleEvent(eventStarting);

			/* 
			 * JavaDoc  The BundleActivator.start method of this bundle's BundleActivator, if
			 * one is specified, is called.
			 */
			try {
				/*
				 * 4.3.7.1  The bundle is activated.
				 */
				ProtectionDomain protectionDomain = framework.getProtectionDomain(this);
				
				AccessController.doPrivileged(new PrivilegedExceptionAction() {
					public Object run() throws Exception {
						/*
						 * Cannot use {@link Bundle#loadClass loadClass} method because
						 * it would lead to a never ending recursice call (see {@link #START_ACTIVATION_POLICY})
						 */
						ClassLoader classLoader = getClassLoader();
						
						Class cls = classLoader.loadClass(className);
						
						activator = (BundleActivator) cls.newInstance();
						
						activator.start(getBundleContext());
						
						setActivationTriggered(false);

						return null;
					}
				}, new AccessControlContext(new ProtectionDomain[] { protectionDomain }));
			} catch (Throwable t) {
				framework.log(LogService.LOG_ERROR, t.getMessage(), t);
				
				/* 
				 * JavaDoc  If the BundleActivator is invalid or throws an exception, this
				 * bundle's state is set back to RESOLVED.
				 */
				setState(RESOLVED);
				
				/* 
				 * JavaDoc  Any services registered by the bundle must be unregistered.
				 *     
				 * JavaDoc  Any services used by the bundle must be released.
				 *     
				 * JavaDoc  Any listeners registered by the bundle must be removed.
				 */
	
				framework.unregisterServices(this);	
				
				context = null;
				
				/* 
				 * JavaDoc  A BundleException is then thrown.
				 */
				throw new BundleException(new StringBuilder("A problem occurred starting ").append(this.toString()).append(".").toString(), t);
			}
		
			/* 
			 * JavaDoc  If this bundle's state is UNINSTALLED, because the
			 * bundle was uninstalled while the BundleActivator.start method was running, 
			 * a BundleException is thrown.
			 */
			if (getState() == UNINSTALLED) {
				throw new BundleException(new StringBuilder(this.toString()).append(" has been uninstalled while bundle start was running.").toString());
			}
		}
		
		/*
		 * 4.3.7.1  The bundle state is moved to ACTIVE.
		 */
		setState(ACTIVE);
		
		/*
		 * 4.3.7.1  The normal STARTING event is fired.
		 * 
		 * 
		 * JavaDoc  This event is only delivered to SynchronousBundleListeners. It is not delivered to 
		 * BundleListeners.
		 * 
		 * 
		 * see BundleContextImpl.SYNCHRONOUS_BUNDLE_EVENT_TOPICS
		 */
		BundleEvent eventStarted = new BundleEvent(BundleEvent.STARTED, this);
		framework.sendBundleEvent(eventStarted);
	}
	
	public void stop(int options) throws BundleException {	    
        /*
         * 4.8.1.1
         */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkPermission(new AdminPermission(this, AdminPermission.EXECUTE));
		}
		
		/* 
		 * JavaDoc  If this bundle's state is UNINSTALLED then an
		 * IllegalStateException is thrown.
		 */
		if (getState() == UNINSTALLED) {
			throw new IllegalStateException(new StringBuilder(this.toString()).append(" has been uninstalled.").toString());
		}		
		/* 
		 * JavaDoc  If this bundle's state is STARTING or
		 * STOPPING then this method must wait for this bundle to
		 * change state before continuing. If this does not occur in a reasonable
		 * time, a BundleException is thrown to indicate this bundle
		 * was unable to be stopped.
		 */
		else if ((getState() & (STARTING | STOPPING)) != 0) {
			/*
			 * 15 seconds
			 */
			synchronized (this) {
				try {
					wait(15 * 1000);
				} catch (InterruptedException e) {
					framework.log(LogService.LOG_ERROR, e.getMessage(), e);
				}
			}
			
			if ((getState() & (STARTING | STOPPING)) != 0) {
			    throw new BundleException(new StringBuilder(this.toString()).append(" was enabled to be stopped.").toString());
			}
		}
		/* 
		 * JavaDoc  Persistently record that this bundle has been stopped. When the
		 * Framework is restarted, this bundle must not be automatically started.
		 * 
	     * 4.3.5  The framework therefore maintains a persistent autostart setting for each
	     * bundle. This autostart setting can have the following values:
	     * 
	     *   Stopped – The bundle should not be started.
	     * 
	     */
	    if (options != STOP_TRANSIENT) {
			framework.setBundleAutostartSetting(this, AbstractBundle.STOPPED);
	    }
		
		/* JavaDoc If this bundle's state is not ACTIVE then this method returns immediately.
		 */
		if (getState() != ACTIVE) {
		    return;
		}
		
		/* 
		 * JavaDoc  This bundle's state is set to STOPPING.
		 */
		setState(STOPPING);
		
		/* 
		 * JavaDoc  A bundle event of type BundleEvent.STOPPING is fired. This event is
		 * only delivered to SynchronousBundleListeners. It is not delivered to BundleListeners.
		 */
		BundleEvent eventStopping = new BundleEvent(BundleEvent.STOPPING, this);
		framework.sendBundleEvent(eventStopping);
		
		BundleException bundleException = null;
		if (activator != null) {
			/* 
			 * JavaDoc  The BundleActivator.stop method of this bundle's
			 * BundleActivator, if one is specified, is called.
			 */
			try {
				ProtectionDomain protectionDomain = framework.getProtectionDomain(this);
				
				AccessController.doPrivileged(new PrivilegedExceptionAction() {
					public Object run() throws Exception {
						BundleContext bundleContext = getBundleContext();
						
						activator.stop(bundleContext);
						
						activator = null;

						return null;
					}
				}, new AccessControlContext(new ProtectionDomain[] { protectionDomain }));
			} catch (Exception e) {
				/*
				 * JavaDoc  If that method throws an exception, this method must continue
				 * to stop this bundle. A BundleException must be thrown after completion of the 
				 * remaining steps.
				 */
				bundleException = new BundleException("An error occurred during bundle stop procedure.", e);
			}
			/* 
			 * JavaDoc  Any services registered by this bundle must be unregistered.
			 * 
			 * JavaDoc  Any listeners registered by this bundle must be removed.
			 */
			framework.unregisterServices(this);
		}

		/* 
		 * JavaDoc  If this bundle's state is UNINSTALLED, because the
		 * bundle was uninstalled while the BundleActivator.stop
		 * method was running, a BundleException must be thrown.
		 */
		if (getState() == UNINSTALLED) {
			throw new BundleException(new StringBuilder(this.toString()).append(" has been uninstalled while bundle stop was running.").toString());
		}
		
		/* 
		 * JavaDoc  This bundle's state is set to RESOLVED.
		 */
		setState(RESOLVED);

		/*
		 * to force to recreate BundleClassLoader and BundleContent objects if one of the
		 * conditions below are matched 
		 * 
		 * 	permissions are changed
		 * 
		 *  bundle has been updated
		 *  
		 *  bundle has been uninstalled
		 */
		
		setBundleContext(null);
		
		/* 
		 * JavaDoc  A bundle event of type BundleEvent.STOPPED is fired.
		 */
		BundleEvent eventStopped = new BundleEvent(BundleEvent.STOPPED, this);
		framework.sendBundleEvent(eventStopped);
		
		if (bundleException != null) {
			throw bundleException;
		}
	}
	
	public void uninstall() throws BundleException {
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
		    securityManager.checkPermission(new AdminPermission(this, AdminPermission.LIFECYCLE));
		}
		
		/* 
		 * JavaDoc  If this bundle's state is UNINSTALLED then an IllegalStateException
		 * is thrown.
		 */
		if (getState() == UNINSTALLED) {
			throw new IllegalStateException(new StringBuilder(this.toString()).append(" has been uninstalled.").toString());
		}		
		/* 
		 * JavaDoc  If this bundle's state is ACTIVE, STARTING or STOPPING, this bundle is
		 * stopped as described in the Bundle.stop method.
		 */
		else if ((getState() & (ACTIVE | STARTING | STOPPING )) != 0) {
			try {
		        stop();
			} catch (BundleException e) {
				/* 
				 * JavaDoc  If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR
				 * is fired containing the exception.
				 */
				FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, this, e);
				framework.postFrameworkEvent(frameworkEvent);
			}
		}
		
		/*
         *
		 * JavaDoc  This bundle's state is set to UNINSTALLED.
		 */
		setState(UNINSTALLED);

		/*
		 * 4.3.8  the Framework must remove any resources related to the bundle.
		 * 
		 * JavaDoc  This bundle and any persistent storage area provided for
		 * this bundle by the Framework are removed.
		 */
		framework.setRemovalPending(this);
		
    	try {
		    boolean removable = true;
		    
		    ExportedPackage[] exportedPackages = framework.getExportedPackages(this);
		    if (exportedPackages != null) {
			    for (int i = 0; i < exportedPackages.length; i++) {
			    	ExportedPackage exportedPackage = exportedPackages[i];
			    	if (exportedPackage.getImportingBundles() != null) {
			    		removable = false;
					}
	
			    	((ExportedPackageImpl) exportedPackage).setRemovalPending0(true);
			    }
		    }
		    
		    RequiredBundle[] requiredBundles = framework.getRequiredBundles(getSymbolicName());
		    if (requiredBundles != null) {
			    for (int i = 0; i < requiredBundles.length; i++) {
			    	RequiredBundle requiredBundle = requiredBundles[i];
			    	
			    	if (requiredBundle.getBundle() == this) {
			    		removable = false;
			    		
			    		((RequiredBundleImpl) requiredBundle).setRemovalPending0(true);
			    		
			    		break;
			    	}
			    }
		    }
		    
			/* 
			 * JavaDoc  A bundle event of type BundleEvent.UNINSTALLED is fired.
			 */
			BundleEvent bundleEvent = new BundleEvent(BundleEvent.UNINSTALLED, this);
			framework.sendBundleEvent(bundleEvent);
			
			if (removable) {
				framework.unresolveBundle(this);
				
				setBundleClassLoader(null);
				
				framework.remove(this);
			}
		} catch (Exception e) {
			throw new BundleException(e.getMessage(), e);
		}
	}
		
	public void update(InputStream is) throws BundleException {
		try {
			SecurityManager securityManager = System.getSecurityManager();
			if (securityManager != null) {
			    securityManager.checkPermission(new AdminPermission(this, AdminPermission.LIFECYCLE));
			}
			
			/* 
			 * JavaDoc  If this bundle's state is ACTIVE, it must be stopped
			 * before the update and started after the update successfully completes.
			 */
			int oldState = getState();
			
			/* 
			 * JavaDoc  If this bundle's state is UNINSTALLED then an IllegalStateException
			 * is thrown.
			 */
			if (getState() == UNINSTALLED) {
				throw new IllegalStateException(new StringBuilder(this.toString()).append(" has been uninstalled.").toString());
			}		
			/* 
			 * JavaDoc  If this bundle's state is ACTIVE, STARTING or STOPPING, the bundle is
			 * stopped as described in the Bundle.stop method. If Bundle.stop throws an exception, the
			 * exception is rethrown terminating the update.
			 */
			if ((getState() & (ACTIVE | STARTING | STOPPING )) != 0) {
				stop(STOP_TRANSIENT);			
			}	
		    
		    setBundleClassLoader(null);
	
			/* 
			 * JavaDoc  The new version of this bundle is installed.
			 */ 
			BundleException bundleException = null;
			try {
				framework.update(this, is);
				/* 
				 * JavaDoc  If the bundle has declared an Bundle-RequiredExecutionEnvironment
				 * header, then the listed execution environments must be verified against the installed
				 * execution environments. If they do not all match, the original version of this bundle
				 * must be restored and a BundleException must be thrown after completion of the remaining
				 * steps.
				 * 
				 * 3.3.1  (...) The org.osgi.framework.executionenvironment property from
				 * BundleContext.getProperty(String) must contain a comma-separated list of
				 * execution environment names implemented by the Framework. This property
				 * is defined as volatile. A Framework implementation must not cache this
				 * information because bundles may change this system property at any time.
				 * The purpose of this volatility is testing and possible extension of the execution
				 * environments at run-time.
				 */
				String requiredEE = (String) getHeaders().get(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT);
				if (requiredEE != null) {
					String ee = System.getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);
					
					if (!ee.contains(requiredEE)) {
						throw new BundleException(new StringBuilder(this.toString()).append(" requires an unsopperted execution environment (=" + requiredEE + ").").toString());
					}
				}
				
				/*
				 * 4.4.9  The exports of an updated bundle must
				 * be immediately available to the Framework. If none of the old exports are
				 * used, then the old exports must be removed. Otherwise, all old exports must
				 * remain available for existing bundles and future resolves until the
				 * refreshPackages method is called or the Framework is restarted.
				 */
			    boolean removable = true;
			    
			    ExportedPackage[] exportedPackages = framework.getExportedPackages(this);
			    if (exportedPackages != null) {
				    for (int i = 0; i < exportedPackages.length; i++) {
				    	ExportedPackage exportedPackage = exportedPackages[i];
				    	if (exportedPackage.getImportingBundles() != null) {
				    		removable = false;
						}
		
				    	((ExportedPackageImpl) exportedPackage).setRemovalPending0(true);
				    }
			    }
			    
			    RequiredBundle[] requiredBundles = framework.getRequiredBundles(getSymbolicName());
			    if (requiredBundles != null) {
				    for (int i = 0; i < requiredBundles.length; i++) {
				    	RequiredBundle requiredBundle = requiredBundles[i];
				    	
				    	if (requiredBundle.getBundle() == HostBundle.this) {
				    		removable = false;
				    		
				    		((RequiredBundleImpl) requiredBundle).setRemovalPending0(true);
				    		
				    		break;
				    	}
				    }
			    }
			    
			    if (removable) {
			    	framework.unresolveBundle(this);
			    }
			} catch (BundleException e) {
				/* 
				 * JavaDoc  If the Framework is unable to install the new version of this 
				 * bundle, the original version of this bundle must be restored and a BundleException
				 * must be thrown after completion of the remaining steps.
				 * 
				 * {@link PersistentStorage}
				 */
				
				bundleException = e;
			}
			
			/* 
			 * JavaDoc  This bundle's state is set to INSTALLED.
			 */
			setState(Bundle.INSTALLED);
			
			/* 
			 * JavaDoc  If the new version of this bundle was successfully installed, a
			 * bundle event of type BundleEvent.UPDATED is fired.
			 */
			BundleEvent bundleEvent = new BundleEvent(BundleEvent.UPDATED, this);
			framework.sendBundleEvent(bundleEvent);
			
			if (oldState == ACTIVE) {
				/* 
				 * JavaDoc  If this bundle's state was originally ACTIVE, the
				 * updated bundle is started as described in the Bundle.start
				 * method.
				 */
				
				try {
					start();			
				} catch (BundleException e) {
					/* 
					 * JavaDoc  If Bundle.start throws an exception, a Framework event of
					 * type FrameworkEvent.ERROR is fired containing the exception.
					 */
					FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.ERROR, this, e);
					framework.postFrameworkEvent(frameworkEvent);
				}
		    }
			
			if (bundleException != null) {
				throw bundleException;
			}
		} finally {
			/*
			 * 6.1.4.33  This method must always close the InputStream when it is done, even if an
			 * exception is thrown.
			 */
			try {
				is.close();
			} catch (IOException e) {
				// do nothing
			} 
		}
	}
	
	public ServiceReference[] getServicesInUse() {
		BundleContext context = getBundleContext();
		if (context != null) {
		    return ((BundleContextImpl) context).getServicesInUse();
	    }
	
	    return null;
	}

	void setBundleContext(BundleContext context) {
		this.context = context;
	}
	
	public BundleContext getBundleContext() {
		/*
		 * 4.3.17
		 */
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
		    securityManager.checkPermission(new AdminPermission(this, AdminPermission.CONTEXT));
		}
		
		return context;
	}
	
	// XXX
	ClassLoader getClassLoader() {
		if (classLoader == null) {
			if (getState() == INSTALLED && getState() == UNINSTALLED) {
				throw new IllegalStateException("Cannot create a class loader for an unresolved Bundle.");
			}
			
			classLoader = framework.createBundleClassLoader(this);
		}
		
		return classLoader;
	}
	
	public void setBundleClassLoader(BundleClassLoader classLoader) {
		this.classLoader = classLoader;
	}
	
	protected boolean isActivationTriggered() {
		return activationTriggered;
	}

	boolean isActivationTriggered(String pkgName) {
    	boolean matched = true;

    	String activationPolicy = (String) getHeaders().get(Constants.BUNDLE_ACTIVATIONPOLICY);
    	
    	if (activationPolicy != null) {
			try {
				ManifestEntry entry = ManifestEntry.parse(activationPolicy)[0];
				
				if (entry.hasAttribute(Constants.EXCLUDE_DIRECTIVE)) {
					String[] exclude = entry.getAttributeValue(Constants.EXCLUDE_DIRECTIVE).split("\\,");
					for (int i = 0; i < exclude.length; i++) {
						String path = exclude[i];
						if (TextUtil.wildcardCompare(path, pkgName) == 0) {
							matched = false;
							
							break;
						}
					}
				}
				
				if (matched) {
					String[] include;
					if (entry.hasAttribute(Constants.INCLUDE_DIRECTIVE)) {
						include = entry.getAttributeValue(Constants.INCLUDE_DIRECTIVE).split("\\,");
						for (int i = 0; i < include.length; i++) {
							String path = include[i];
							if (TextUtil.wildcardCompare(path, pkgName) != 0) {
								matched = false;
								
								break;
							}
						}
					}
				}
			} catch (Exception e) {
				framework.log(LogService.LOG_ERROR, e.getMessage(), e);
				
				matched = false;
			}
    	}
		
		return matched;
	}

	protected void setActivationTriggered(boolean activationTriggered) {
		this.activationTriggered = activationTriggered;
	}
}
