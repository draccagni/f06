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

import java.security.Permission;
import java.util.Dictionary;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import f06.util.CaseSensitiveDictionary;

public final class AdminPermission extends Permission {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2696816351038444993L;

	private static final int ACTION_NONE = 0x00000000;

	private static final int ACTION_CLASS = 0x00000001;

	private static final int ACTION_CONTEXT = 0x00000002;

	private static final int ACTION_EXECUTE = 0x00000004;

	private static final int ACTION_EXTENSIONLIFECYCLE = 0x00000008;

	private static final int ACTION_LIFECYCLE = 0x00000010;

	private static final int ACTION_LISTENER = 0x00000020;

	private static final int ACTION_METADATA = 0x00000040;

	private static final int ACTION_RESOLVE = 0x00000080;
	
	private static final int ACTION_RESOURCE = 0x00000100;

	private static final int ACTION_STARTLEVEL = 0x00000200;

	private static final int ACTION_ALL = ACTION_CLASS | ACTION_CONTEXT | ACTION_EXECUTE | ACTION_EXTENSIONLIFECYCLE | ACTION_LIFECYCLE | ACTION_LISTENER | ACTION_METADATA | ACTION_RESOLVE | ACTION_RESOURCE | ACTION_STARTLEVEL;
	
	private Bundle bundle;
	
	private Dictionary bundleProperties;

	private String actions;

	private volatile int hashCode = 0;

	private static int getMask(String actions) {
		boolean seencomma = false;

		int mask = ACTION_NONE;

		if (actions == null) {
			return mask;
		} else if (actions.equals(Constants0.WILDCARD)) {
			return ACTION_ALL;
		}

		char[] a = actions.toCharArray();

		int i = a.length - 1;
		if (i < 0)
			return (mask);

		while (i != -1) {
			char c;

			// skip whitespace
			while ((i != -1)
					&& ((c = a[i]) == ' ' || c == '\r' || c == '\n'
							|| c == '\f' || c == '\t'))
				i--;

			int matchlen;

			if (i >= 4 && (a[i - 4] == 'c' || a[i - 4] == 'C')
					&& (a[i - 3] == 'l' || a[i - 3] == 'L')
					&& (a[i - 2] == 'a' || a[i - 2] == 'A')
					&& (a[i - 1] == 's' || a[i - 1] == 'S')
					&& (a[i] == 's' || a[i] == 'S')) {
				matchlen = 5;
				mask |= ACTION_CLASS;
			} else if (i >= 6 && (a[i - 6] == 'c' || a[i - 6] == 'C')
					    && (a[i - 5] == 'o' || a[i - 5] == 'O')
						&& (a[i - 4] == 'n' || a[i - 4] == 'N')
						&& (a[i - 3] == 't' || a[i - 3] == 'T')
						&& (a[i - 2] == 'e' || a[i - 2] == 'E')
						&& (a[i - 1] == 'x' || a[i - 1] == 'X')
						&& (a[i] == 't' || a[i] == 'T')) {
					matchlen = 7;
					mask |= ACTION_CONTEXT;
			} else if (i >= 6 && (a[i - 6] == 'e' || a[i - 6] == 'E')
				    && (a[i - 5] == 'x' || a[i - 5] == 'X')
					&& (a[i - 4] == 'e' || a[i - 4] == 'E')
					&& (a[i - 3] == 'c' || a[i - 3] == 'C')
					&& (a[i - 2] == 'u' || a[i - 2] == 'U')
					&& (a[i - 1] == 't' || a[i - 1] == 'T')
					&& (a[i] == 'e' || a[i] == 'E')) {
				matchlen = 7;
				mask |= ACTION_EXECUTE;
			} else if (i >= 17 && (a[i - 17] == 'e' || a[i - 17] == 'E')
					&& (a[i - 16] == 'x' || a[i - 16] == 'X')
					&& (a[i - 15] == 't' || a[i - 15] == 'T')
					&& (a[i - 14] == 'e' || a[i - 14] == 'E')
					&& (a[i - 13] == 'n' || a[i - 13] == 'N')
					&& (a[i - 12] == 's' || a[i - 12] == 'S')
					&& (a[i - 11] == 'i' || a[i - 11] == 'I')
					&& (a[i - 10] == 'o' || a[i - 10] == 'O')
					&& (a[i - 9] == 'n' || a[i - 9] == 'N')
					&& (a[i - 8] == 'l' || a[i - 8] == 'L')
					&& (a[i - 7] == 'i' || a[i - 7] == 'I')
					&& (a[i - 6] == 'f' || a[i - 6] == 'F')
				    && (a[i - 5] == 'e' || a[i - 5] == 'E')
					&& (a[i - 4] == 'c' || a[i - 4] == 'C')
					&& (a[i - 3] == 'y' || a[i - 3] == 'Y')
					&& (a[i - 2] == 'c' || a[i - 2] == 'C')
					&& (a[i - 1] == 'l' || a[i - 1] == 'L')
					&& (a[i] == 'e' || a[i] == 'E')) {
				matchlen = 18;
				mask |= ACTION_EXTENSIONLIFECYCLE;
			} else if (i >= 8 && (a[i - 8] == 'l' || a[i - 8] == 'L')
					&& (a[i - 7] == 'i' || a[i - 7] == 'I')
					&& (a[i - 6] == 'f' || a[i - 6] == 'F')
				    && (a[i - 5] == 'e' || a[i - 5] == 'E')
					&& (a[i - 4] == 'c' || a[i - 4] == 'C')
					&& (a[i - 3] == 'y' || a[i - 3] == 'Y')
					&& (a[i - 2] == 'c' || a[i - 2] == 'C')
					&& (a[i - 1] == 'l' || a[i - 1] == 'L')
					&& (a[i] == 'e' || a[i] == 'E')) {
				matchlen = 9;
				mask |= ACTION_LIFECYCLE;
			} else if (i >= 7 && (a[i - 7] == 'm' || a[i - 7] == 'M')
					&& (a[i - 6] == 'e' || a[i - 6] == 'E')
				    && (a[i - 5] == 't' || a[i - 5] == 'T')
					&& (a[i - 4] == 'a' || a[i - 4] == 'A')
					&& (a[i - 3] == 'd' || a[i - 3] == 'D')
					&& (a[i - 2] == 'a' || a[i - 2] == 'A')
					&& (a[i - 1] == 't' || a[i - 1] == 'T')
					&& (a[i] == 'a' || a[i] == 'A')) {
				matchlen = 8;
				mask |= ACTION_METADATA;
			} else if (i >= 6 && (a[i - 6] == 'r' || a[i - 6] == 'R')
				    && (a[i - 5] == 'e' || a[i - 5] == 'E')
					&& (a[i - 4] == 's' || a[i - 4] == 'S')
					&& (a[i - 3] == 'o' || a[i - 3] == 'O')
					&& (a[i - 2] == 'l' || a[i - 2] == 'L')
					&& (a[i - 1] == 'v' || a[i - 1] == 'V')
					&& (a[i] == 'e' || a[i] == 'E')) {
				matchlen = 7;
				mask |= ACTION_RESOLVE;
			} else if (i >= 7 && (a[i - 7] == 'r' || a[i - 7] == 'R')
					&& (a[i - 6] == 'e' || a[i - 6] == 'E')
				    && (a[i - 5] == 's' || a[i - 5] == 'S')
					&& (a[i - 4] == 'o' || a[i - 4] == 'O')
					&& (a[i - 3] == 'u' || a[i - 3] == 'U')
					&& (a[i - 2] == 'r' || a[i - 2] == 'R')
					&& (a[i - 1] == 'c' || a[i - 1] == 'C')
					&& (a[i] == 'e' || a[i] == 'E')) {
				matchlen = 8;
				mask |= ACTION_RESOURCE;
			} else {
				throw new IllegalArgumentException(new StringBuilder("invalid permission: ").append(actions).toString());
			}

			seencomma = false;
			while (i >= matchlen && !seencomma) {
				switch (a[i - matchlen]) {
					case ',' :
						seencomma = true;
						break;
					case ' ' :
					case '\r' :
					case '\n' :
					case '\f' :
					case '\t' :
						break;
					default :
						throw new IllegalArgumentException(new StringBuilder("invalid permission: ").append(actions).toString());
				}
				i--;
			}

			i -= matchlen;
		}

		if (seencomma) {
			throw new IllegalArgumentException(new StringBuilder("invalid permission: ").append(actions).toString());
		}

		return mask;
		
	}
	
	public AdminPermission() {
		this(Constants0.WILDCARD, Constants0.WILDCARD);
	}

	public AdminPermission(String filter, String actions) {
		super(filter);
		this.actions = actions;
		this.bundle = null;
	}

	public AdminPermission(Bundle bundle, String actions) {
		super(null);
		this.bundle = bundle;
		this.actions = actions;
	}

	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof AdminPermission) {
			AdminPermission ap = (AdminPermission) obj;
			if (getActions().equals(ap.getActions())) {
				if (bundle != null) {
					return bundle == ap.bundle;
				} else {
					return getName().equals(ap.getName());
				}
			}
		}

		return false;
	}

	public int hashCode() {
		if (hashCode == 0) {
			/*
			 * see java.util.Arrays.hashCode(Object[])
			 */
			
			hashCode = 31 + getMask(getActions());
			hashCode = 31 * hashCode + (bundle != null ? bundle.hashCode() : getName().hashCode());
		}
		
		return hashCode;
	}

	public String getActions() {
		return actions;
	}

	public boolean implies(Permission p) {
		if (p instanceof AdminPermission) {
			AdminPermission ap = (AdminPermission) p;

			int mask = getMask(ap.getActions());
			
			if ((getMask(getActions()) & mask) == mask) {
				if (bundle != null) {
					return bundle == ap.bundle;
				} else {
					try {
						return getName().equals(Constants0.WILDCARD)
								|| FrameworkUtil.createFilter(getName()).match(ap.getBundleProperties());
					} catch (Exception e) {
						throw new IllegalArgumentException(e.getMessage(), e);
					}
				}
			}
		}

		return false;
	}

    private Dictionary getBundleProperties() {
    	if (bundleProperties == null) {
    		bundleProperties = new CaseSensitiveDictionary(false);

    		bundleProperties.put("id", Long.valueOf(bundle.getBundleId()));
    		
    		if (bundle.getSymbolicName() != null) {
    			bundleProperties.put("name", bundle.getSymbolicName());
    		}
    		
    		bundleProperties.put("location", bundle.getLocation());

    		// TODO
    		// bundleProperties.put("signer", ...);
    	}
    	
    	return bundleProperties;
    }

}
