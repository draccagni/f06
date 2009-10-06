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

import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class CaseSensitiveDictionary extends Dictionary {

	private static class CaseInsensitiveComparator implements Comparator {

		public int compare(Object o1, Object o2) {
			return o1.toString().compareToIgnoreCase(o2.toString());
		}
	}

	private Map map;

	public CaseSensitiveDictionary(boolean caseSensitive) {
		this.map = caseSensitive ? 
				(Map) new HashMap() : 
				new TreeMap(new CaseInsensitiveComparator());
	}

	public Enumeration elements() {
		return Collections.enumeration(map.values());
	}

	public Object get(Object key) {
		return map.get(key);
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public Enumeration keys() {
		return Collections.enumeration(map.keySet());
	}

	public Object put(Object key, Object value) {
		if (key == null) {
			throw new IllegalArgumentException(new StringBuilder("key(value=").append(value).append(") cannot be null.").toString());
		}

		return map.put(key, value);
	}

	public Object remove(Object key) {
		return map.remove(key);
	}

	public int size() {
		return map.size();
	}

	public String toString() {
		return map.toString();
	}

	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}
}
