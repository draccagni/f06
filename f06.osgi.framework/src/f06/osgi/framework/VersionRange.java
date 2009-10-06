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

import org.osgi.framework.Version;

public class VersionRange {
	
	private static final Version MAX_VERSION = new Version(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

	private String range;
	
	private Version min;
	
	private boolean minIncluded; 
	
	private Version max;
	
	private boolean maxIncluded;

	public VersionRange(String range) {
		this.range = range;
		
		if (range == null || range.length() == 0) {
			min = Version.emptyVersion;
			minIncluded = true;
			max = MAX_VERSION;
			maxIncluded = true;
		} else {
			range = range.trim();
			
			char first = range.charAt(0);
			if (first == '[' || first == '(') {
				minIncluded = first == '[';
				
				int comma = range.indexOf(',');
				if (comma == -1) {
					throw new IllegalArgumentException(", missed: " + range);
				}
				min = Version.parseVersion(range.substring(1, comma));
				
				char last = range.charAt(range.length() - 1);				
				if (last != ']' && last != ')') {
					throw new IllegalArgumentException(new StringBuilder("] or ) parenthesis missed: ").append(range).toString());
				}
				maxIncluded = last == ']';
				max = Version.parseVersion(range.substring(comma + 1, range.length() - 1));
			} else {
				min = Version.parseVersion(range);
				minIncluded = true;
				
				max = MAX_VERSION;
				maxIncluded = true;
			}
		}
	}

	public boolean isIncluded(Version version) {
		if (version == null)
			return false;

		return version.compareTo(min) >= (minIncluded ? 0 : 1) && 
			version.compareTo(max) <= (maxIncluded ? 0 : -1);
	}

	public boolean equals(Object object) {
		if (object instanceof VersionRange) {
			VersionRange versionRange = (VersionRange) object;
			if (min.equals(versionRange.min) && minIncluded == versionRange.minIncluded) {
				return max.equals(versionRange.max) && maxIncluded == versionRange.maxIncluded;
			}
		}
		
		return false;
	}

	public int hashCode() {
		return range.hashCode();
	}
	
	public String toString() {
		return range;
	}
}
