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
package f06.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class ManifestEntry {
	
	private static Map parsedEntries = new HashMap();
	
	private String name;
	
	private Map attributes;
	
	public ManifestEntry(String name, Map attributes) {
		this.name = name;
		this.attributes = attributes;
	}

	public boolean hasAttribute(String attrName) {
		return attributes.containsKey(attrName);
	}
	
	public String[] getAttributeNames() {
		return (String[]) attributes.keySet().toArray(new String[0]);
	}
	
	public String[] getAttributeValues(String attrName) {
		return (String[]) attributes.get(attrName);
	}

	public String getAttributeValue(String attrName) {
		return ((String[]) attributes.get(attrName))[0];
	}
	
	public String getName() {
		return name;
	}

	public static ManifestEntry[] parse(Object entry) throws Exception {
		ManifestEntry[] entries = (ManifestEntry[]) parsedEntries.get(entry);
		if (entries == null) {
			entries = parse0(entry);
			parsedEntries.put(entry, entries);
		}
		
		return entries;
	}

	private static ManifestEntry[] parse0(Object entry) throws Exception {
		String s = (String) entry;
		
		if ((s == null) || (s.length() == 0)) {
			return null;
		}
		
		s = s.trim();
		
		char lastCh = s.charAt(s.length() - 1);
		
		if (lastCh == ';' || lastCh == ',') {
			throw new Exception(new StringBuilder("Manifest entry: ")
				.append(entry).append(" cannot terminate with ")
				.append(lastCh)
				.append(" character").toString());
		}
		
		List l = new ArrayList();
		
		List keys = new ArrayList();
		Map attrs = new HashMap();

		StringTokenizer st = new StringTokenizer(s, ";");
		while (st.hasMoreTokens()) {
			String t = st.nextToken();
			
			t = t.trim();
			int i = t.indexOf('=');
			
			boolean lastAttribute = false;
			if (i != -1) {
				/*
				 * parse an attribute
				 */
				String attrName;
				if (t.charAt(i - 1) == ':') {
					attrName = t.substring(0, i - 1);
				} else {
					attrName = t.substring(0, i);						
				}
				
				attrName = attrName.trim();
				
				String attrValue;
				if (t.charAt(i + 1) == '"') {
					/*
					 * attribute value is quoted
					 */
					int j = t.indexOf('"', i + 2);
					attrValue = t.substring(i + 2, j);
					
					t = t.substring(j + 1);
					
					j = t.indexOf(',');
					if (j != -1) {
						/*
						 * the token ends with this value 
						 */
						lastAttribute = true;
						
						t = t.substring(j + 1);
					}
				} else {
					int j = t.indexOf(',', i + 1);
					if (j != -1) {
						/*
						 * the token ends with this value 
						 */
						lastAttribute = true;
						
						attrValue = t.substring(i + 1, j);
						t = t.substring(j + 1);
					} else {
						attrValue = t.substring(i + 1, t.length());
					}
				}
				
				attrValue = attrValue.trim();
				
				String[] values = (String[]) attrs.get(attrName);
				if (values == null) {
					values = new String[] {
						attrValue	
					};
					attrs.put(attrName, values);
				} else {
					String[] newValues = (String[]) ArrayUtil.add(values, attrValue);
					attrs.put(attrName, newValues);
				}
			} else {
				/*
				 * a new key to set attributes
				 */
				int j = t.indexOf(',');
				if (j != -1) {
					/*
					 * the last key to set attributes
					 */
					lastAttribute = true;

					String key = t.substring(0, j);
					
					key = key.trim();
					
					keys.add(key);				

					t = t.substring(j + 1);
				} else {
					t = t.trim();
					
					keys.add(t);				
				}
			}
			
			if (lastAttribute) {
				for (int k = 0; k < keys.size(); k++) {
					ManifestEntry manifestEntry = new ManifestEntry((String) keys.get(k), attrs);
					l.add(manifestEntry);
				}

				keys = new ArrayList();
				attrs = new HashMap();
				
				lastAttribute = false;					

				StringTokenizer st2 = new StringTokenizer(t, ",");
				while (st2.hasMoreTokens()) {
					String t2 = st2.nextToken();
					
					t2 = t2.trim();
					if (st2.hasMoreTokens()) {
						ManifestEntry manifestEntry = new ManifestEntry(t2, attrs);
						l.add(manifestEntry);							

						attrs = new HashMap();
					} else {
						keys.add(t2);

						attrs = new HashMap();
					}
				}
			}
		}
		
		for (int k = 0; k < keys.size(); k++) {
			ManifestEntry manifestEntry = new ManifestEntry((String) keys.get(k), attrs);
			l.add(manifestEntry);
		}
		
		return (ManifestEntry[]) l.toArray(new ManifestEntry[0]);
	}
	
	public static void main(String[] args) throws Exception {
		parse("a;b;c=2");
	}
}
