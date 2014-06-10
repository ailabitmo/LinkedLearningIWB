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

package com.fluidops.iwb.keywordsearch;

import org.openrdf.query.TupleQueryResult;
import com.fluidops.iwb.widget.QueryResultWidget;

/**
 * interface for search extensions.<p>
 * 
 * An underlying assumption for keyword query strings is that they contain the
 * projections {@value #SUBJECT}, {@value #PROPERTY}, {@value #VALUE}, and
 * {@value #TYPE}
 * 
 * 
 * @author tobias, christian.huetter
 * 
 */
public interface KeywordSearchProvider extends SearchProvider
{

	/**
	 * Binding name for the subject
	 */
	public static final String SUBJECT = "Subject";
	
	/**
	 * Binding name for the property
	 */
	public static final String PROPERTY = "Property";
	
	/**
	 * Binding name for the Value
	 */
	public static final String VALUE = "Value";
	
	/**
	 * Binding name for the Type
	 */
	public static final String TYPE = "Type";
	
	/**
	 * @param keywordString Keyword search String
	 * @return result of search, must contain projections {@value #SUBJECT}, {@value #PROPERTY}, {@value #VALUE}, {@value #TYPE}
	 * @see {@link QueryResultWidget}
	 * @throws Exception
	 */
	public TupleQueryResult search(String keywordString) throws Exception;

}
