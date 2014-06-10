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


/**
 * Interface for columns in relational databases.
 * 
 * @author msc
 */
public interface Column
{
	/**
	 * @return the plain column name 
	 */
	public String getName();
	
	/**
	 * @return tableName.columnName
	 */
	public String getShortName();
	
	/**
	 * @return schemaName.tableName.columnName
	 */
	public String getFullName();
	
	/**
	 * @return column's data type information for the column
	 */
	public ColumnDataType getColumnDataType();
	
	/**
	 * @return column's ordinal ordering position, starting from 1
	 */
	public int getOrdinalPosition();
	
	/**
	 * @return whether NULL is allowed
	 */
	public boolean isNullable();
}
