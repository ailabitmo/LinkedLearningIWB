/*
 * Copyright (C) 2008-2013, fluid Operations AG
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package com.fluidops.iwb.api.operator;

import java.util.List;
import java.util.Set;

import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.Value;

import com.fluidops.iwb.api.EndpointImpl;
import com.fluidops.iwb.api.NamespaceService;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Operator utility functions.
 * 
 * @author uli
 *
 */
public class OperatorUtil {	

	
	public static String removeEnclosingTicks(String serialized) {
		String token = serialized.startsWith("'") ? serialized.substring(1) : serialized;
		token = token.endsWith("'") ? token.substring(0, token.length()-1) : token;
		return token;
	}
	
	public static boolean isEnclosedByTicks(String serialized) {
		return serialized.startsWith("'") && serialized.endsWith("'") && serialized.length()>1;
	}
	
	/**
	 * Replace special tokens in the input string,
	 * 
	 * {{Pipe}} => |
	 * 
	 * @param input
	 * @return
	 */
	public static String replaceSpecialTokens(String input) {
		input = input.replaceAll("\\{\\{Pipe\\}\\}", "|");

		return input;
	}
	
	
	/**
	 * Tries a conversion of the given value to the expected target type.
	 * 
	 * Currently supports:
	 * String => {@link Value#stringValue()}
	 * 
	 * @param v
	 * @param targetType
	 * @return
	 * @throws IllegalArgumentException if the casting is not successful (e.g. Literal to URI)
	 */
	@SuppressWarnings("unchecked")
	public static Object toTargetType(Value v, Class<?> targetType) throws IllegalArgumentException {
		if (v==null)
			return null;
		if (targetType.equals(String.class))
			return v.stringValue();	
		if (targetType.equals(List.class))
			return Lists.newArrayList(v);
		if (targetType.equals(URI.class) || targetType.equals(Literal.class))
			return toValueTargetType(v, (Class<? extends Value>)targetType);		
		return v;	
	}	
	
	/**
	 * Convert the given value to a specific value and perform checks if this is possible. Throws
	 * an IllegalArgumentException if casting is not possible, i.e. value is a URI and target type
	 * is a Literal.
	 * 
	 * @param value
	 * @param valueTargetType
	 * @return
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Value> T toValueTargetType(Value value, Class<T> valueTargetType) throws IllegalArgumentException {
		if(value == null)
			return (T)value; //null is a valid return value and matches any targettype
		if (valueTargetType.equals(URI.class) && !(value instanceof URI))
			throw new IllegalArgumentException(value.getClass().getSimpleName() + " " + value + " cannot be cast to " + valueTargetType.getSimpleName());
		if (valueTargetType.equals(Literal.class) && !(value instanceof Literal))
			throw new IllegalArgumentException(value.getClass().getSimpleName() + " " + value + " cannot be cast to " + valueTargetType.getSimpleName());
		return (T)value;
	}
	
	/**
	 * Checks each element of the list if it is convertible to the given target type and
	 * returns the list. This method makes sure that all list elements are actual
	 * instances of the provided listGenericType.
	 * 
	 * Example:
	 * if List&lt;Literal&gt; is expected, all elements of the provided list have to
	 * be {@link Literal}s. Otherwise an exception is thrown.
	 * 
	 * @param res
	 * @param listGenericType
	 * @return
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("unchecked")
	public static <L> List<L> checkElementsAndGetList(List<Value> res, Class<L> listGenericType) throws IllegalArgumentException {
		if (listGenericType.equals(Value.class))
			return (List<L>) res;
		try {
			List<Object> l = Lists.newArrayList();		
			for (Value v : res)
				l.add( OperatorUtil.toTargetType(v, listGenericType));
			return (List<L>)l;
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Error during conversion to list of " + listGenericType.getSimpleName() + ": " + e.getMessage());
		}
	}
	
	/**
	 * Returns the serialized operator string for a value (without enclosing '):
	 * 
	 * Examples:
	 * 
	 * <pre>
	 * localName
	 * ns:localName
	 * <http://example.org/fullUri>
	 * "literal"
	 * </pre>
	 * 
	 * Note: this method throws an {@link IllegalArgumentException} for
	 * Blank Nodes.
	 * 
	 * @param value
	 * @return
	 */
	public static String valueToOperatorSerialization(Value value) {
		
		if (value instanceof Literal)
			return value.toString();
		
		if (value instanceof URI) {
			URI uri = (URI) value;
			NamespaceService ns = EndpointImpl.api().getNamespaceService();
			String abbreviatedUri = ns.getAbbreviatedURI(uri);
			if (abbreviatedUri!=null)
				return abbreviatedUri;
			return "<" + uri.stringValue() + ">";
		}
		
		throw new IllegalArgumentException("Type not supported: " + value.getClass());
	}
	
	private static final Set<Class<?>> primitives = Sets.<Class<?>>newHashSet(String.class, Integer.class, int.class, Boolean.class, boolean.class, Double.class, double.class, Long.class, long.class );
	
	/**
	 * Returns true if the target type represents a primitive constant according
	 * to the operator framework. Primitives are represented as {@link OperatorConstantNode}.
	 * The set of primitives corresponds to Java primitives and their Object representations,
	 * as well as enumerations and Strings.
	 * 
	 * @param targetType
	 * @return
	 */
	public static boolean isPrimitive(Class<?> targetType) {
		return primitives.contains(targetType) || targetType.isEnum();
	}
	
	/**
	 * Returns true if the given operator is an empty {@link OperatorStructNode}
	 * @param op
	 * @return
	 */
	public static boolean isEmptyStructOperator(Operator op) {
		if (op==null)
			return false;
		return op.isStructure() && ((OperatorStructNode)op.getRoot()).keySet().isEmpty();
	}
}
