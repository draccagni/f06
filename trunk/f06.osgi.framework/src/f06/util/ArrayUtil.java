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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ArrayUtil {
	
	public static Object[] add(Object[] array, Object item) {
		synchronized (array) {
			Object[] newArray = copyOf(array, array.length + 1);
			newArray[array.length] = item;
			
			return newArray;			
		}
	}

	public static Object[] add(Object[] array, Object[] items) {
		synchronized (array) {
			Object[] newArray = copyOf(array, array.length + items.length);
			System.arraycopy(items, 0, newArray, array.length, items.length);
			
			return newArray;			
		}
	}

	public static Object[] remove(Object[] array, Object item) {
		Object[] newArray = array;
		
		for (int i = 0; i < array.length; i++) {
			if (array[i].equals(item)) {
				int newLength = array.length - 1;
				
				newArray = (Object[]) Array.newInstance(array.getClass().getComponentType(), newLength);
				
				System.arraycopy(array, 0, newArray, 0, i);
				
				if (i < newLength) {
					System.arraycopy(array, i + 1, newArray, i, newLength - i);
				}
				
				break;
			}
		}
		
		return newArray;			
	}
	
	public static boolean contains(Object[] array, Object item) {
		for (int i = 0; i < array.length; i++) {
			if (array[i].equals(item)) {
				return true;
			}
		}
		
		return false;			
	}

	public static boolean equals(Object[] array0, Object[] array1) {
		if (array0.length == array1.length) {
			for (int i = 0; i < array0.length; i++) {
				if (!array0[i].equals(array1[1])) {
					return false;
				}
			}
			
			return true;
		}
		
		return false;			
	}

	public static Object[] copyOf(Object[] original, int newLength) {
    	Object[] copy = (Object[]) Array.newInstance(original.getClass().getComponentType(), newLength);
    			
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }
    
    public static Object[] toArray(Class cls, Enumeration e) {
		List l = new ArrayList();
		while (e.hasMoreElements()) {
			Object key = e.nextElement();
			l.add(key);
		}

		return l.toArray((Object[]) Array.newInstance(cls, 0));
    }
    
    public static Enumeration toEnumeration(final Object[] array) {
    	return new Enumeration() {
    		private int i;
    		
    		{
    			i = 0;
    		}
    		
    		public boolean hasMoreElements() {
    			return i < array.length;
    		}
    		
    		public Object nextElement() {
    			return array[i++];
    		}
    	};
    }
}
