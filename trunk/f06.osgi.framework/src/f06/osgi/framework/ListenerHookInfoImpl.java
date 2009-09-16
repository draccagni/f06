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
