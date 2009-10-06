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
import org.osgi.service.packageadmin.ExportedPackage;

class ExportedPackageImpl implements ExportedPackage {

	private Bundle exportingBundle;
	
	private volatile Bundle[] importingBundles;
	
	private String name;
	
	private String specificationVersion;
	
	private Version version;
	
	private volatile boolean removalPending;
	
	private String company;
	
	private String[] uses;
	
	private String[] mandatory;
	
	private String[] include;
	
	private String[] exclude;
	
	// XXX out of specs
	final ClassLoader classLoader;
	
	public ExportedPackageImpl(Bundle exportingBundle, String name, String specificationVersion, Version version, String company, String[] uses, String[] mandatory, String[] include, String[] exclude, ClassLoader classLoader) {
		this.exportingBundle = exportingBundle;
		this.importingBundles = null;
		this.name = name;
		this.specificationVersion = specificationVersion;
		this.version = version;
		this.company = company;
		this.uses = uses;
		this.mandatory = mandatory;
		this.include = include;
		this.exclude = exclude;
		this.removalPending = false;
		// XXX
		this.classLoader = classLoader;
	}
	
	public Bundle getExportingBundle() {
		// XXX out of specs
		return ((AbstractBundle) exportingBundle).isStale0() ?
				null :
				exportingBundle;
	}

	void setImportingBundles0(Bundle[] importingBundles) {
		this.importingBundles = importingBundles;
	}
	
	public Bundle[] getImportingBundles() {
		// XXX out of specs
		return ((AbstractBundle) exportingBundle).isStale0() ?
				null : 
				importingBundles;
	}

	public String getName() {
		return name;
	}

	public String getSpecificationVersion() {
		return specificationVersion;
	}

	public Version getVersion() {
		return version;
	}

	public boolean isRemovalPending() {
		return removalPending;
	}

	public String getCompany() {
		return company;
	}

	public String[] getExclude() {
		return exclude;
	}

	public String[] getInclude() {
		return include;
	}

	public String[] getMandatory() {
		return mandatory;
	}

	public String[] getUses() {
		return uses;
	}

	void setRemovalPending0(boolean removalPending) {
		this.removalPending = removalPending;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder("ExportedPackage(name=").
			append(getName()).
			append(",version=").
			append(getVersion());
		
		String specificationVersion = getSpecificationVersion();
		if (specificationVersion != null) {
			sb.append(",specificationVersion=").
			append(specificationVersion);
		}
		
		sb.append(",exportingBundle=").append(getExportingBundle());

		Bundle[] importingBundles = getImportingBundles(); 
		
		if (importingBundles != null){
			sb.append(",importingBundles=[");
			for (int i = 0; i < importingBundles.length; i++) {
				if (i > 0) {
					sb.append(',');
				}

				sb.append(importingBundles[i]);
			}
			sb.append(']');
		}

		String[] include = getInclude(); 
		
		if (include != null){
			sb.append(",include=[");
			for (int i = 0; i < include.length; i++) {
				if (i > 0) {
					sb.append(',');
				}

				sb.append(include[i]);
			}
			sb.append(']');
		}

		String[] exclude = getInclude(); 
		
		if (exclude != null){
			sb.append(",exclude=[");
			for (int i = 0; i < exclude.length; i++) {
				if (i > 0) {
					sb.append(',');
				}

				sb.append(exclude[i]);
			}
			sb.append(']');
		}
		
		String[] mandatory = getInclude(); 
		
		if (mandatory != null){
			sb.append(",mandatory=[");
			for (int i = 0; i < mandatory.length; i++) {
				if (i > 0) {
					sb.append(',');
				}

				sb.append(mandatory[i]);
			}
			sb.append(']');
		}

		sb.append(",removalPending=").append(isRemovalPending());

		String company = getCompany();
		if (company != null) {
			sb.append(",company=").append(company);
		}

		sb.append(")");
		
		return sb.toString();
	}
	
	ClassLoader getClassLoader0() {
		return classLoader;
	}
}
