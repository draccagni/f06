package f06.osgi.framework.launch;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

import f06.util.ManifestEntry;

public class Launcher {
	
	public void launch(String args[]) throws Exception {
		String fc_path = "etc/framework.config";

		String lc_path = "etc/launcher.config";

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("--framework-config")) {
				fc_path = args[++i];
			} else if (arg.equals("--launcher-config")) {
				fc_path = args[++i];
			}
		}

		Properties configuration = loadConfiguration(fc_path);		
		Framework framework = new f06.osgi.framework.launch.FrameworkFactory().newFramework(configuration);
		framework.start();

		BundleContext context = framework.getBundleContext();
		Properties lconf = loadConfiguration(lc_path);		
		Object bundles = lconf.get("org.osgi.framework.launch.additionalbundles");
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
	
	private void launch0(BundleContext context, Object bundles) throws Exception {
		ServiceReference packageAdminReference = context.getServiceReference(PackageAdmin.class.getName());
		ServiceReference startLevelReference = context.getServiceReference(StartLevel.class.getName());
		
		PackageAdmin packageAdmin = (PackageAdmin) context.getService(packageAdminReference);
		StartLevel startLevel = (StartLevel) context.getService(startLevelReference);

		if (bundles != null) {
			ManifestEntry[] entries = ManifestEntry.parseEntry(bundles);
			for (int i = 0; i < entries.length; i++) {
				ManifestEntry entry = entries[i];
				String location = entry.getName();
			
				try {
					Bundle bundle = context.installBundle(location);
					
					String startlevel = entry.getAttributeValue("startlevel");
					if (startlevel != null) {
						startLevel.setBundleStartLevel(bundle, Integer.parseInt(startlevel));
					}
					if (packageAdmin.getBundleType(bundle) != PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
						bundle.start();
					}
				} catch (Exception e) {
					// do nothing
				}
			}
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
