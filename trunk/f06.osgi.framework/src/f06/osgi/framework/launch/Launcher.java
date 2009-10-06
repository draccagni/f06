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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.service.startlevel.StartLevel;

import f06.util.ManifestEntry;

public class Launcher {

	private static final Launcher instance = new Launcher();
	
	public static Launcher getInstance() {
		return instance;
	}
	
	Launcher() {
		// XXX singleton
	}
	
	public void launch(String args[]) throws Exception {
		
		String lp_path = "config/launcher.properties";
		if (args.length > 0) {
			lp_path = args[0];
		}
		Properties lconfiguration = loadConfiguration(lp_path);		

		printLicense();

		String fp_path = lconfiguration.getProperty("org.osgi.framework.launch.configuration",
				"config/framework.properties");

		Properties configuration = loadConfiguration(fp_path);		
		Framework framework = new f06.osgi.framework.launch.FrameworkFactory().newFramework(configuration);

		framework.start();

		BundleContext context = framework.getBundleContext();
		Object bundles = lconfiguration.get("org.osgi.framework.launch.bundles.auto");
		if (bundles != null) {
			launch0(context, bundles);
		}
		
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
	
	private void launch0(BundleContext context, Object bundles) throws Exception {
		if (bundles != null) {
			ServiceReference startLevelReference = context.getServiceReference(StartLevel.class.getName());
			StartLevel startLevel = (StartLevel) context.getService(startLevelReference);

			Bundle[] bundles0 = context.getBundles();
			
			ManifestEntry[] entries = ManifestEntry.parse(bundles);
			NEXT_BUNDLE: for (int i = 0; i < entries.length; i++) {
				ManifestEntry entry = entries[i];
				String location = entry.getName();
				
				for (int j = 0; j < bundles0.length; j++) {
					if (bundles0[j].getLocation().equals(location)) {
						continue NEXT_BUNDLE;
					}
				}
				
				context.installBundle(location);
			}
				
			Bundle[] bundles1 = context.getBundles();
			for (int i = 0; i < entries.length; i++) {
				ManifestEntry entry = entries[i];
				String location = entry.getName();
				
				for (int j = 0; j < bundles1.length; j++) {
					Bundle bundle = bundles1[j];
					if (bundle.getLocation().equals(location)) {
						try {
							String startlevel = entry.getAttributeValue("startlevel");
							if (startlevel != null) {
								startLevel.setBundleStartLevel(bundle, Integer.parseInt(startlevel));
							} else {
								String useActivationPalocy = entry.getAttributeValue("use-activation-policy");
								bundle.start(Boolean.parseBoolean(useActivationPalocy) ? 0 : Bundle.START_ACTIVATION_POLICY);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}

			context.ungetService(startLevelReference);
		}
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
		Launcher.getInstance().launch(args);
	}
}
