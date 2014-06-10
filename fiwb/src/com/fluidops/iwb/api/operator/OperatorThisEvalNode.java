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

import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;

import com.fluidops.iwb.Global;
import com.fluidops.iwb.api.ReadDataManager;
import com.fluidops.iwb.api.ReadDataManagerImpl;
import com.fluidops.iwb.provider.ProviderUtils;

/**
 * Operator for evaluation of properties of a given resource.
 * <p>
 * 
 * The operator is denoted as $this.&lt;MyProperty&gt;$, where my property can be
 * converted to a valid full URI. Note that valueContext is used as the subject,
 * i.e. it needs to be set prior to evaluation. Otherwise an OperatorException
 * is thrown.<p>
 * 
 * If there are multiple instances for the given property, an operator exception
 * is thrown (in case a single result is expected).<p>
 * 
 * If the query result is empty, {@code null} is returned.<p>
 * 
 * If the targetType does not match the runtime type of retrieved object, a
 * ClassCastException will be thrown.<p>
 * 
 * Examples: 
 * <pre>
 * $this.label$ $this.rdfs:label$ $this.&lt;http://example.org/property&gt;$
 * </pre>
 * 
 * Supported targetTypes are defined in {@link OperatorNodeListBase#supportedTargetTypes}:
 * 
 * <pre>
 * List&lt;Value&gt; (default), URI, Literal, List&lt;Value&gt;, String, Object (=> List&lt;Value&gt;)
 * </pre>
 * 
 * @author as
 */
public class OperatorThisEvalNode extends OperatorNodeListBase implements OperatorNode {

	private static final long serialVersionUID = -4124591126222019504L;
	
	private final String serialized;
	private Resource valueContext = null;
		
	OperatorThisEvalNode(String serialized)	{
		this.serialized = serialized;
	}

	@Override
	public <T> T evaluate(Class<T> targetType) throws OperatorException	{
		if (valueContext==null)
			throw new OperatorException("No valueContext specified for dynamic evaluation");
		if (!supportedTargetTypes.contains(targetType))
			throw new OperatorException("Target type " + targetType.getName() + " not supported.");
		
		if (serialized.equals("$this$"))
			return (T)toTargetType(valueContext, targetType);
		
		ReadDataManager dm = ReadDataManagerImpl.getDataManager(Global.repository);
		List<Value> values = dm.getProps(valueContext, getPredicate());
		
		return handleList(targetType, values);
	}

	@Override
	public String serialize() {
		return serialized;
	}

	@Override
	public void setValueContext(Value valueContext)	{
		if (!(valueContext instanceof Resource))
			throw new IllegalArgumentException("Value context must be a Resource");
		this.valueContext = (Resource)valueContext;			
	}
	
	private URI getPredicate() {
		// serialized is $this.MYPROPERTY$
		return ProviderUtils.objectToUri(serialized.substring(6, serialized.length()-1));
	}		
}
