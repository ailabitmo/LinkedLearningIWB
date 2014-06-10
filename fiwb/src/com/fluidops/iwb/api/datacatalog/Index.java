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

package com.fluidops.iwb.api.datacatalog;

import java.util.List;

/**
 * Interface for relational database indices.
 * 
 * @author msc
 */
public interface Index 
{		
	// index types
	public static enum IndexType 
	{
		UNIQUE,
		PERFORMANCE,
		PRIMARY,
		UNKNOWN
	}

	/**
	 * @return idx plain name
	 */
	public String getName();

	/**
	 * @return tableName.idxName
	 */
	public String getShortName();

	/**
	 * @return schemaName.tableName.idxName
	 */
	public String getFullName();

	/**
	 * @return the type of the Index (UNIQUE, PERFORMANCE, PRIMARY, UNKNOWN)
	 */
	public IndexType getType();

	/**
	 * @return all columns referenced by the Index
	 */
	public List<Column> getColumns();
}
