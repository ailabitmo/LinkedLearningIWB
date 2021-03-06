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

package com.fluidops.iwb.layout;

import java.util.List;

import com.fluidops.ajax.components.FContainer;
import com.fluidops.iwb.page.PageContext;
import com.fluidops.iwb.ui.editor.LazyViewTabComponentHolder;
import com.fluidops.iwb.ui.editor.SemWiki;
import com.fluidops.iwb.widget.AbstractWidget;

/**
 * base interface for containers that act as IWB layouts
 * 
 * @author aeb
 */
public interface WidgetContainer
{
	/**
	 * FContainer is handled via object containment
	 */
	public FContainer getContainer();
	
	/**
	 * add widget to container
	 * @param widget	widget to add
	 * @param id		use this fajax / fcomponent ID
	 */
	public void add( AbstractWidget<?> widget, String id );
	
	/**
	 * this method is called after the last widget is added
	 * in case the contains needs to do some pre render setup
	 * that is not yet possible at container creation time
	 * (e.g. set active tab)
	 */
	public void postRegistration( PageContext pc );
	
	/**
	 * Returns a list of all Java Scripts required by widgets
	 * rendered as part of this {@link WidgetContainer}.
	 * 
	 * Note that the Java Scripts required by widgets contained 
	 * within {@link SemWiki} (i.e. embedded inside wiki pages)
	 * are retrieved lazily by {@link LazyViewTabComponentHolder}.
	 * 
	 * Note: implementations must not return null
	 */
	public List<String> jsUrls();
}
