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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import f06.util.ArrayUtil;
import f06.util.CaseSensitiveDictionary;
import f06.util.TextUtil;

class RFC1960Filter implements Filter {

	protected static final int NONE      = 0x0000;
	
	protected static final int EQUAL     = 0x0001;

	protected static final int LESS      = 0x0002;

	protected static final int GREATER   = 0x0004;

	protected static final int APPROX    = 0x0008;

	protected static final int PRESENT   = 0x0010;

	protected static final int SUBSTRING = 0x0020;

	protected static final int AND       = 0x0040;

	protected static final int OR        = 0x0080;

	protected static final int NOT       = 0x0100;

	protected int operation;

	protected Object[] operands;

	protected String filter;
	
	private static Map fetchedConstructors = new HashMap();

	public RFC1960Filter(String filter) throws InvalidSyntaxException {
		this.filter = filter;
		parse();
	}
	
	public boolean match(ServiceReference reference) {
		Dictionary d = new CaseSensitiveDictionary(false);

		/*
		 * 3.2.6  Attribute names are not case sensitive;
		 */
		String[] keys = reference.getPropertyKeys();
		
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			Object value = reference.getProperty(key);
			d.put(key, value);
		}

		return matchImpl(d);
	}

	public boolean match(Dictionary dictionary) {
		if (dictionary == null) {
			return false;
		}

		Dictionary d = new CaseSensitiveDictionary(false);

		/*
		 * 3.2.6  Attribute names are not case sensitive;
		 */
		for (Enumeration e = dictionary.keys(); e.hasMoreElements(); )  {
			String key = (String) e.nextElement();
			d.put(key, d.get(key));
		}
		
		return matchImpl(d);
	}

	public boolean matchCase(Dictionary dictionary) {
		if (dictionary == null) {
			return false;
		}
		
		return matchImpl(dictionary);
	}

	public String toString() {
		return filter;
	}

	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (!(obj instanceof RFC1960Filter)) {
			return false;
		}

		return filter.equals(((RFC1960Filter) obj).filter);
	}

	public int hashCode() {
		return filter.hashCode();
	}

	protected boolean matchImpl(Dictionary dictionary) {
		if (operation == AND) {
			RFC1960Filter[] filters = (RFC1960Filter[]) operands;

			for (int i = 0; i < filters.length; i++) {
				if (!filters[i].matchImpl(dictionary)) {
					return false;
				}
			}

			return true;
		} else if (operation == OR) {
			RFC1960Filter[] filters = (RFC1960Filter[]) operands;

			for (int i = 0; i < filters.length; i++) {
				if (filters[i].matchImpl(dictionary)) {
					return true;
				}
			}

			return false;
		} else if (operation == NOT) {
			RFC1960Filter filter = (RFC1960Filter) operands[0];

			return !filter.matchImpl(dictionary);
		} else if ((operation & (SUBSTRING | EQUAL | GREATER | LESS | APPROX)) != 0) {
			Object value = dictionary.get(operands[0]);

			if (value != null) {
				return matchImpl(value);				
			}

			return false;
		} else if (operation == PRESENT) {
			Object value = dictionary.get(operands[0]);

			return value != null;
		}

		return false;
	}
	
	private boolean matchImpl(Object value) {
		if (value instanceof String) {
			return matchImpl((String) value);
		} else if (value instanceof Long) {
			return matchImpl((Long) value);
		} else if (value instanceof Integer) {
			return matchImpl((Integer) value);
		} else if (value instanceof Short) {
			return matchImpl((Short) value);
		} else if (value instanceof Character) {
			return matchImpl((Character) value);
		} else if (value instanceof Byte) {
			return matchImpl((Byte) value);
		} else if (value instanceof Double) {
			return matchImpl((Double) value);
		} else if (value instanceof Float) {
			return matchImpl((Float) value);
		} else if (value instanceof Boolean) {
			return matchImpl((Boolean) value);
		} else if (value instanceof Comparable) {
			return matchImpl((Comparable) value);
		} else if (value instanceof Vector) {
			Iterator it = ((Vector) value).iterator();
			while (it.hasNext()) {
			    if (matchImpl(it.next())) {
			    	return true;
			    }
			}
		} else {
			Class clazz = value.getClass();

			if (clazz.isArray()) {
				Object[] array = (Object[]) value;
				for (int i = 0; i < array.length; i++) {
					if (matchImpl(array[i])) {
						return true;
					}
				}
			}
		}

		return false;
	}
	
	
	
	private boolean matchImpl(String value) {
		String strValue = (String) operands[1];
		
		if (operation == EQUAL) {
			return strValue.equals(value);
		} else if (operation == GREATER) {
			return strValue.compareTo(value) >= 0;
		} else if (operation == LESS) {
			return strValue.compareTo(value) <= 0;
		} else if (operation == APPROX) {
			return TextUtil.removeWhitespace(strValue).equalsIgnoreCase(TextUtil.removeWhitespace(value));
		} else if (operation == SUBSTRING) {
			return TextUtil.wildcardCompare(strValue, value) == 0;
		}
		
		return false;
	}
	
	private boolean matchImpl(Long value) {
		long lValue = value.longValue();
		long lOperand = Long.parseLong((String) operands[1]); 
		
		if ((operation & (EQUAL | APPROX)) != 0) {
			return lOperand == lValue;
		} else if (operation == GREATER) {
			return lOperand >= lValue;
		} else if (operation == LESS) {
			return lOperand <= lValue;
		} // else if (operation == SUBSTRING) {
		
		return false;
	}
	
	private boolean matchImpl(Integer value) {
		int iValue = value.intValue();
		int iOperand = Integer.parseInt((String) operands[1]); 
		
		if ((operation & (EQUAL | APPROX)) != 0) {
			return iOperand == iValue;
		} else if (operation == GREATER) {
			return iOperand >= iValue;
		} else if (operation == LESS) {
			return iOperand <= iValue;
		} // else if (operation == SUBSTRING) {
		
		return false;
	}
	
	private boolean matchImpl(Short value) {
		short shValue = value.shortValue();
		int shOperand = Short.parseShort((String) operands[1]); 
		
		if ((operation & (EQUAL | APPROX)) != 0) {
			return shOperand == shValue;
		} else if (operation == GREATER) {
			return shOperand >= shValue;
		} else if (operation == LESS) {
			return shOperand <= shValue;
		} // else if (operation == SUBSTRING) {
		
		return false;
	}
	
	private boolean matchImpl(Character value) {
		char cValue = value.charValue();
		String strValue = (String) operands[1];
		
		if (strValue.length() > 1) {
			return false;
		}
		
		char cOperand = strValue.charAt(0); 
		
		if (operation == EQUAL) {
			return cOperand == cValue;
		} else if ((operation & (EQUAL | APPROX)) != 0) {
			return Character.toLowerCase(cOperand) == Character.toLowerCase(cValue);
		} else if (operation == GREATER) {
			return cOperand >= cValue;
		} else if (operation == LESS) {
			return cOperand <= cValue;
		} // else if (operation == SUBSTRING) {
		
		return false;
	}
	
	private boolean matchImpl(Byte value) {
		byte bValue = value.byteValue();
		byte bOperand = Byte.parseByte((String) operands[1]);
		
		if ((operation & (EQUAL | APPROX)) != 0) {
			return bOperand == bValue;
		} else if (operation == GREATER) {
			return bOperand >= bValue;
		} else if (operation == LESS) {
			return bOperand <= bValue;
		} // else if (operation == SUBSTRING) {		
		
		return false;
	}
	
	private boolean matchImpl(Double value) {
		double dValue = value.doubleValue();
		double dOperand = Double.parseDouble((String) operands[1]); 
		
		if ((operation & (EQUAL | APPROX)) != 0) {
			return dOperand == dValue;
		} else if (operation == GREATER) {
			return dOperand >= dValue;
		} else if (operation == LESS) {
			return dOperand <= dValue;
		} // else if (operation == SUBSTRING) {		
		
		return false;
	}
	
	private boolean matchImpl(Float value) {
		float fValue = value.floatValue();
		float fOperand = Float.parseFloat((String) operands[1]); 
		
		if ((operation & (EQUAL | APPROX)) != 0) {
			return fOperand == fValue;
		} else if (operation == GREATER) {
			return fOperand >= fValue;
		} else if (operation == LESS) {
			return fOperand <= fValue;
		} // else if (operation == SUBSTRING) {		
		
		return false;
	}
	
	private boolean matchImpl(Boolean value) {
		boolean boolValue = value.booleanValue();
		boolean boolOperand = Boolean.parseBoolean((String) operands[1]); 
		
		if ((operation & (EQUAL | APPROX | GREATER | LESS)) != 0) {
			return boolOperand == boolValue;
		} // else if (operation == SUBSTRING) {		
		
		return false;
	}
	
	private boolean matchImpl(Comparable value) {
		try {
			Class cls = value.getClass();
			
			Constructor constructor = (Constructor) fetchedConstructors.get(cls);
			if (constructor == null) {
				constructor = cls.getConstructor(new Class[] { String.class });
				FrameworkUtil.setAccessible(constructor, true);
				
				fetchedConstructors.put(cls, constructor);
			}
			
			Comparable compOperand = (Comparable) constructor.newInstance(new Object[] { ((String) operands[1]).trim() });

			if ((operation & (EQUAL | APPROX)) != 0) {
				return compOperand.compareTo(value) == 0;
			} else if (operation == GREATER) {
				return compOperand.compareTo(value) >= 0;
			} else if (operation == LESS) {
				return compOperand.compareTo(value) <= 0;
			} // else if (operation == SUBSTRING) {
		} catch (SecurityException e) {
			throw new IllegalArgumentException(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getMessage());
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException(e.getMessage());
		} catch (InstantiationException e) {
			throw new IllegalArgumentException(e.getMessage());
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException(e.getMessage());
		} catch (InvocationTargetException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
		
		/*
		 * for any reason ...
		 */
		return false;
	}
	
	/*
	 * Network Working Group                                           T. Howes
	 * Request for Comments: 1960                        University of Michigan
	 * Obsoletes: 1558                                                June 1996
	 * Category: Standards Track
	 * 
	 *              A String Representation of LDAP Search Filters
	 * 
	 * Status of this Memo

	 *    This document specifies an Internet standards track protocol for the
	 *    Internet community, and requests discussion and suggestions for
	 *    improvements.  Please refer to the current edition of the "Internet
	 *    Official Protocol Standards" (STD 1) for the standardization state
	 *    and status of this protocol.  Distribution of this memo is unlimited.
	 * 
	 * 1.  Abstract
	 * 
	 *    The Lightweight Directory Access Protocol (LDAP) [1] defines a
	 *    network representation of a search filter transmitted to an LDAP
	 *    server.  Some applications may find it useful to have a common way of
	 *    representing these search filters in a human-readable form.  This
	 *    document defines a human-readable string format for representing LDAP
	 *    search filters.
	 * 
	 * 2.  LDAP Search Filter Definition
	 * 
   	 * An LDAP search filter is defined in [1] as follows:
	 * 
	 *      Filter ::= CHOICE {
	 *              and                [0] SET OF Filter,
	 *              or                 [1] SET OF Filter,
	 *              not                [2] Filter,
	 *              equalityMatch      [3] AttributeValueAssertion,
	 *              substrings         [4] SubstringFilter,
	 *              greaterOrEqual     [5] AttributeValueAssertion,
	 *              lessOrEqual        [6] AttributeValueAssertion,
	 *              present            [7] AttributeType,
	 *              approxMatch        [8] AttributeValueAssertion
	 *      }
	 * 
	 *      SubstringFilter ::= SEQUENCE {
	 *              type    AttributeType,
	 *              SEQUENCE OF CHOICE {
	 *                      initial        [0] LDAPString,
	 *                      any            [1] LDAPString,
	 *                      final          [2] LDAPString
	 *              }
	 *      }
	 * 
	 *      AttributeValueAssertion ::= SEQUENCE {
	 *              attributeType   AttributeType,
	 *              attributeValue  AttributeValue
	 *      }
	 * 
	 *      AttributeType ::= LDAPString
	 * 
	 *      AttributeValue ::= OCTET STRING
	 * 
	 *      LDAPString ::= OCTET STRING
	 * 
	 *    where the LDAPString above is limited to the IA5 character set.  The
	 *    AttributeType is a string representation of the attribute type name
	 *    and is defined in [1].  The AttributeValue OCTET STRING has the form
	 *    defined in [2].  The Filter is encoded for transmission over a
	 *    network using the Basic Encoding Rules defined in [3], with
	 *    simplifications described in [1].
	 * 
	 * 3.  String Search Filter Definition
	 * 
	 *    The string representation of an LDAP search filter is defined by the
	 *    following grammar.  It uses a prefix format.
	 * 
	 *      <filter> ::= '(' <filtercomp> ')'
	 *      <filtercomp> ::= <and> | <or> | <not> | <item>
	 *      <and> ::= '&' <filterlist>
	 *      <or> ::= '|' <filterlist>
	 *      <not> ::= '!' <filter>
	 *      <filterlist> ::= <filter> | <filter> <filterlist>
	 *      <item> ::= <simple> | <present> | <substring>
	 *      <simple> ::= <attr> <filtertype> <value>
	 *      <filtertype> ::= <equal> | <approx> | <greater> | <less>
	 *      <equal> ::= '='
	 *      <approx> ::= '~='
	 *      <greater> ::= '>='
	 *      <less> ::= '<='
	 *      <present> ::= <attr> '=*'
	 *      <substring> ::= <attr> '=' <initial> <any> <final>
	 *      <initial> ::= NULL | <value>
	 *      <any> ::= '*' <starval>
	 *      <starval> ::= NULL | <value> '*' <starval>
	 *      <final> ::= NULL | <value>
	 * 
	 *    <attr> is a string representing an AttributeType, and has the format
	 *    defined in [1].  <value> is a string representing an AttributeValue,
	 *    or part of one, and has the form defined in [2].  If a <value> must
	 *    contain one of the characters '*' or '(' or ')', these characters
   	 *    should be escaped by preceding them with the backslash '\' character.
	 * 
	 *    Note that although both the <substring> and <present> productions can
	 *    produce the 'attr=*' construct, this construct is used only to denote
	 *    a presence filter.
	 * 
	 * 4.  Examples
	 * 
	 *    This section gives a few examples of search filters written using
	 *    this notation.
	 * 
	 *      (cn=Babs Jensen)
	 *      (!(cn=Tim Howes))
	 *      (&(objectClass=Person)(|(sn=Jensen)(cn=Babs J*)))
	 *      (o=univ*of*mich*)
	 * 
	 * 5.  Security Considerations
	 * 
	 *    Security considerations are not discussed in this memo.
	 * 
	 * 6.  Bibliography
	 * 
	 *    [1] Yeong, W., Howes, T., and S. Kille, "Lightweight
	 *        Directory Access Protocol", RFC 1777, March 1995.
	 * 
	 *    [2] Howes, R., Kille, S., Yeong, W., and C. Robbins, "The String
	 *        Representation of Standard Attribute Syntaxes", RFC 1778,
	 *        March 1995.
	 * 
	 *    [3] Specification of Basic Encoding Rules for Abstract Syntax
	 *        Notation One (ASN.1).  CCITT Recommendation X.209, 1988.
	 * 
	 * 7.  Author's Address
	 * 
	 *    Tim Howes
	 *    University of Michigan
	 *    ITD Research Systems
	 *    535 W William St.
	 *    Ann Arbor, MI 48103-4943
	 *    USA
	 * 
	 *    Phone: +1 313 747-4454
	 *    EMail: tim@umich.edu
	 * 
	 */
	
	private void parse() throws InvalidSyntaxException {
		String str = filter;
		
		str = str.trim();
		
		if (str.charAt(0) != '(') {
			throw new InvalidSyntaxException("'(' not found", str);
		}

		int lastIndex = str.length() - 1;

		if (str.charAt(lastIndex) != ')') {
			throw new InvalidSyntaxException("')' not found", str);
		}
		
		int i = 1;
		
		char c = str.charAt(i);
		
		if (c == '&') {
			operation = AND;
		} else if (c == '|') {
			operation = OR;
		} else if (c == '!') {
			operation = NOT;
		} else { // == '('
			operands = new String[2];
			
			char[] charArray = str.toCharArray();
			
			int j = 1;
			for (i = 2; i < lastIndex; i++) {
				c = charArray[i];
				if (c == '=') {
					operands[0] = new String(charArray, j, i - j);
					j = i + 1;
					
					operation = EQUAL;
					
					continue;
				} else if (c == '~') {
					if (str.charAt(i + 1) != '=') {
						throw new InvalidSyntaxException("'~' is unknown", str);
					}
					
					operands[0] = new String(charArray, j, i - j);
					j = i + 2;
					
					operation = APPROX;
					
					i++;
					
					continue;
				} else if (c == '<') {
					if (str.charAt(i + 1) != '=') {
						throw new InvalidSyntaxException("'<' is unknown", str);
					}
					
					operands[0] = new String(charArray, j, i - j);
					j = i + 2;
					
					operation = LESS;
					
					i++;
					
					continue;
				} else if (c == '>') {
					if (str.charAt(i + 1) != '=') {
						throw new InvalidSyntaxException("'>' is unknown", str);
					}
					
					/*
					 * 3.2.6  Attribute names are not case sensitive;
					 */
					operands[0] = new String(charArray, j, i - j);
					j = i + 2;
					
					operation = GREATER;
					
					i++;
					
					continue;
				}
			}
			
			operands[1] = new String(charArray, j, i - j);
			
			if (((String) operands[1]).indexOf('*') != -1) {
				operation = SUBSTRING;
			}
			
			return;
		}
		
		i = 2;
		
		if (operation == NOT) {
			operands = new RFC1960Filter[] {
					new RFC1960Filter(str.substring(i,  lastIndex))
			};
		} else {
			int pCount = 0;

			StringBuilder builder = new StringBuilder();
			for (; i < lastIndex; i++) {
				c = str.charAt(i);
				
				if (Character.isWhitespace(c)) {
					continue;
				}
				
				builder.append(c);
				
				if (c == '(') {
					pCount++;
				} else if (c == ')') {
					if (pCount == 1) {
						if (operands == null) {
							operands = new RFC1960Filter[] {
									new RFC1960Filter(builder.toString())
							};
						} else {
							operands = ArrayUtil.add((RFC1960Filter[]) operands, new RFC1960Filter(builder.toString()));
						}
						
						builder = new StringBuilder();
					}

					pCount--;
				}
			}
		}
	}
}
