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

package com.fluidops.iwb.api.datacatalog.impl;

import java.lang.reflect.Field;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openrdf.model.Graph;
import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.util.GraphUtil;
import org.openrdf.model.util.GraphUtilException;

import com.fluidops.iwb.api.datacatalog.ColumnDataType;
import com.fluidops.iwb.model.Vocabulary.RSO;


/**
 * Implementation of a {@link ColumnDataType} that constructs itself from an
 * RDF representation aligned with fluidOps Relational Schema Ontology.
 * 
 * @author msc
 */
public class ColumnDataTypeImpl implements ColumnDataType
{
	private static final Logger logger = 
    		Logger.getLogger(ColumnDataTypeImpl.class.getName());

	protected String name;
	protected int jdbcCode;
	protected static final Map<Integer, String> types = getJDBCTypes();
	
	public ColumnDataTypeImpl(Graph graph, URI columnDataTypeUri)
	throws InvalidSchemaSpecificationException
	{
		try
		{
			Literal nameLit = GraphUtil.getOptionalObjectLiteral(graph, columnDataTypeUri, RSO.PROP_COLUMN_DATATYPE_NAME);
			if (nameLit!=null)
				name = nameLit.stringValue();
	
			Literal jdbcCodeLit = GraphUtil.getOptionalObjectLiteral(graph, columnDataTypeUri, RSO.PROP_COLUMN_DATATYPE_JDBC_CODE);
			if (jdbcCodeLit!=null)
				jdbcCode = jdbcCodeLit.intValue();
		}
		catch (GraphUtilException e)
		{
			logger.warn(e.getMessage());
			throw new RuntimeException(e);
		}
		
		
		
	}
	
	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public int getJDBCCode() {
		return jdbcCode;
	}

	@Override
	public String getJDBCName() {
			return types.get(jdbcCode);
	}
	
	private static Map<Integer, String> getJDBCTypes(){
		Map<Integer, String> jdbctypes = new HashMap<Integer, String>();

		for (Field field : Types.class.getFields()){
			try {
				jdbctypes.put((Integer)field.get(null), field.getName());
			} catch (IllegalArgumentException e) {
				logger.warn(e.getMessage());;
			} catch (IllegalAccessException e) {
				logger.warn(e.getMessage());
			}
		}
		return jdbctypes;
	}
	
}
