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
package f06.osgi.framework.launch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

import com.sun.org.apache.bcel.internal.generic.GETSTATIC;

public class Launcher {
	
	public void launch(String args[]) throws Exception {
		
		String fc_path = System.getProperty("framework.config", "etc/framework.config");

		printLicense();

		Properties configuration = loadConfiguration(fc_path);		
		Framework framework = new f06.osgi.framework.launch.FrameworkFactory().newFramework(configuration);

		framework.start();

		BundleContext context = framework.getBundleContext();
		autoStart(context);
		
		FrameworkEvent e = framework.waitForStop(0L);
		switch (e.getType()) {
		/*
		 * JavaDoc
		 * 
		 *         FrameworkEvent.STOPPED - This Framework has been stopped.
		 */
		case FrameworkEvent.STOPPED:
		/*
		 * JavaDoc
		 * 
		 *         FrameworkEvent.STOPPED_BOOTCLASSPATH_MODIFIED - This Framework 
		 *         has been stopped and a bootclasspath extension bundle has been 
		 *         installed or updated. The VM must be restarted in order for the
		 *         changed boot class path to take affect.
		 * 
		 * TODO
		 */
		case FrameworkEvent.STOPPED_BOOTCLASSPATH_MODIFIED:
		/*
		 * JavaDoc
		 * 
		 *         FrameworkEvent.STOPPED_UPDATE - This
		 *         Framework has been updated which has shutdown and will now
		 *         restart.
		 */
		case FrameworkEvent.STOPPED_UPDATE:
			System.out.println("Bye.");

			System.exit(0);
		/*
		 * JavaDoc
		 * 
		 *         FrameworkEvent.INFO - This method has timed out
		 *         and returned before this Framework has stopped.
		 */
		case FrameworkEvent.INFO:
			System.out.println("Why?");
			System.exit(1);
		/*
		 * JavaDoc
		 * 
		 *         FrameworkEvent.ERROR - The Framework
		 *         encountered an error while shutting down or an error has occurred
		 *         which forced the framework to shutdown.
		 */
		case FrameworkEvent.ERROR:
			System.out.println("Oooops.");
			System.exit(2);
		}
	}
	
	private void printLicense() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResource("license").openStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		System.out.println();
		System.out.flush();		
	}
	
	private void autoStart(BundleContext context) throws Exception {
		File[] files = new File("opt").listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".jar");
			}
		});
		
		if (files == null) {
			return;
		}
		
		ServiceReference packageAdminReference = context.getServiceReference(PackageAdmin.class.getName());
		PackageAdmin packageAdmin = (PackageAdmin) context.getService(packageAdminReference);

		ServiceReference startLevelReference = context.getServiceReference(StartLevel.class.getName());
		StartLevel startLevel = (StartLevel) context.getService(startLevelReference);
		
		NEXT_BUNDLE: for (int i = 0; i < files.length; i++) {
			try {
				Bundle[] bundles = context.getBundles();
				
				String location = files[i].toURI().toURL().toString();
				for (int j = 0; j < bundles.length; j++) {
					if (bundles[j].getLocation().equals(location)) {
						bundles[j].update();
						
						continue NEXT_BUNDLE;
					}
				}

				context.installBundle(location);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		
			try {
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Bundle[] bundles = context.getBundles();

		int resolved = 0;
		
		int prevResolved = 0;
		
		int startlevel = 2;
		
		while (resolved < bundles.length) {
			for (int i = 1; i < bundles.length; i++) {
				Bundle bundle = bundles[i];
				if (bundle.getState() == Bundle.INSTALLED) {
					if (packageAdmin.resolveBundles(new Bundle[] {
							bundles[i]
					})) {
						resolved++;
						startLevel.setBundleStartLevel(bundle, startlevel);
					}
				}
			}
			
			if (resolved == prevResolved) {
				break;
			}
			
			prevResolved = resolved;
			startlevel++;
		}
		

		context.ungetService(packageAdminReference);

		context.ungetService(startLevelReference);
	}
	
	private Properties loadConfiguration(String c_path) throws IOException {
		InputStream is;
		
		try {
			is = new URL(c_path).openStream();
		} catch (MalformedURLException e) {
			is = new FileInputStream(c_path);
		}
		
		Properties configuration = new Properties();
		configuration.load(is);

		is.close();
		
		return configuration;
	}

	public static void main(String args[]) throws Exception {
		new Launcher().launch(args);
	}
}
