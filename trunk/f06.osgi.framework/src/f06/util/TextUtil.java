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

public class TextUtil {
	
	public static String removeWhitespace(String in) {
		char[] out = in.toCharArray();
		int l = out.length;

		int i = 0;
		for (int k = 0; k < l; k++) {
			char c = out[k];

			if (Character.isWhitespace(c)) {
				continue;
			}

			out[i] = c;
			i++;
		}

		return i < l ? new String(out, 0, i) : in;
	}

	public static int wildcardCompare(String s, String z) {
		if (s.equals("*") || z.equals("*")) {
			return 0;
		}
		
		int l = s.length();
		int m = z.length();
		
		int i = 0;
		int j = 0;
		while (j < m && i < l) {
			char c = s.charAt(i);
			char k = z.charAt(j);
			
			if (c != '*' && k != '*') {
				if (k != c) {
					return ((int) k) - ((int) c) > 0 ? 1 : -1;
				}
				
				i++;
				j++;
			} else if (c == '*') {
				if (i == l - 1) {
					// s ends w/ a *
					return 0;
				} else if (i < l - 2 && s.charAt(i + 1) == k) {
					i += 2;
				}

				j++;
			} else { // if (k == '*') {
				if (j == m - 1) {
					// z ends w/ a *
					return 0;
				} else if (j < m - 2 && z.charAt(j + 1) == c) {
					j += 2;
				}

				i++;
			}
		}
		
		return i == l && j == m ? 0 : (i < l ? -1 : 1);
	}
	
	public static String toHexString(String str) {
		int hashCode = str.hashCode();
		
		return toHexString(hashCode);
	}
	
	public static String toHexString(int i) {
		StringBuilder builder = new StringBuilder();
		for (int j = 0; j < 4; j++) {
			int b = (int) (i & 0xFF);
			i >>= 8;			
		
			String s = Integer.toHexString(b);
			if (s.length() == 1) {
				builder.append('0');
			}
			
			builder.append(s);
		}

		return builder.toString();
	}
	
	public static String toHexString(long l) {
		StringBuilder builder = new StringBuilder();
		for (int j = 0; j < 8; j++) {
			int b = (int) (l & 0xFF);
			l >>= 8;			
		
			String s = Integer.toHexString(b);
			if (s.length() == 1) {
				builder.append('0');
			}
			
			builder.append(s);
		}

		return builder.toString();
	}

//	public static void main(String[] args) {
//		String ws = "*.jsp";
//		String s = "/web/test4.jsp";
//		
//		System.out.println(wildcardCompare(ws, s));
//		
//		ws = "/web/*.jsp";
//		s = "/web/test4.jsp";
//		
//		System.out.println(wildcardCompare(ws, s));
//		
//		ws = "/web2/*.jsp";
//		s = "/web/test4.jsp";
//		
//		System.out.println(wildcardCompare(ws, s));
//		
//		ws = "/*/test4.jsp";
//		s = "/web/test4.jsp";
//		
//		System.out.println(wildcardCompare(ws, s));
//		
//		ws = "*";
//		s = "/web/test4.jsp";
//		
//		System.out.println(wildcardCompare(ws, s));
//		
//		ws = "/*/test4.jsp";
//		s = "test4.jsp";
//		
//		System.out.println(wildcardCompare(ws, s));
//		
//		ws = "/*/test4.jsp";
//		s = "/web/*.jsp";
//		
//		System.out.println(wildcardCompare(ws, s));
//	}
}
