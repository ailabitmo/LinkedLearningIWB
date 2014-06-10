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

package com.fluidops.iwb.widget.config;

import com.fluidops.iwb.model.ParameterConfigDoc;

public class WidgetBaseConfig
{
	@ParameterConfigDoc(
			displayName = "Asynchronously",
			desc = "Load the widget asynchronously", 
			defaultValue="false")  
			public Boolean asynch = false;
	
	@ParameterConfigDoc(
			displayName = "Width",
			desc = "Width of the widget in px",
			required = false)
    public String width;

	@ParameterConfigDoc(
			displayName = "Height",
			desc = "Height of the widget in px",
			required = false)
    public String height;
}
