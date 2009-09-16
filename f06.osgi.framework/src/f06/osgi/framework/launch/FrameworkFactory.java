package f06.osgi.framework.launch;

import java.util.Map;

import org.osgi.framework.launch.Framework;

public class FrameworkFactory implements
		org.osgi.framework.launch.FrameworkFactory {

	public Framework newFramework(Map configuration) {
		Framework framework = new f06.osgi.framework.Framework(configuration);
		
		return framework;
	}
}
