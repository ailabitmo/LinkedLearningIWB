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

package com.fluidops.iwb.service;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.openrdf.model.Value;

import com.fluidops.ajax.FClientUpdate;
import com.fluidops.ajax.FClientUpdate.Prio;
import com.fluidops.ajax.components.FComponent;
import com.fluidops.iwb.annotation.CallableFromWidget;
import com.fluidops.iwb.api.operator.OperatorUtil;
import com.fluidops.iwb.api.operator.SPARQLResultTable;
import com.fluidops.iwb.page.PageContext;
import com.fluidops.iwb.util.QueryResultUtil;
import com.fluidops.iwb.widget.ActionableResultWidget;
import com.fluidops.iwb.widget.CodeExecutionWidget;
import com.fluidops.iwb.widget.CodeExecutionWidget.WidgetCodeConfig;
import com.fluidops.iwb.widget.TableResultWidget;
import com.fluidops.util.StringUtil;
import com.fluidops.util.scripting.DynamicScriptingSupport;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;


/**
 * Service to execute a java or groovy method. Executes methods annotated with
 * {@link CallableFromWidget} annotation.<p>
 * 
 * The {@link #run(Config)} method throws the nested cause exception instead of an
 * {@link InvocationTargetException} in case of execution errors.
 * 
 * @author msc, as
 * @see CodeExecutionWidget
 * @see ActionableResultWidget
 * @see CodeExecutionTest
 */
public class CodeExecution implements Service<CodeExecution.Config>
{
	protected static final Logger logger = Logger.getLogger(CodeExecution.class.getName());
	
	/**
	 * Context for code execution from the table result
	 * 
	 * @author as
	 * @see TableResultWidget
	 * @see QueryResultUtil
	 */
	@edu.umd.cs.findbugs.annotations.SuppressWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification="Fields used externally")
	public static class CodeExecutionContext implements ExecutionContext {
		
		/**
		 * @deprecated Use {@link #getContextValue()} to retrieve the {@link URI} of the
		 * resource from which this code execution has been invoked.
		 */
		@Deprecated
		final public PageContext pc;					// the page context

		@Deprecated
		final public FComponent parentComponent;		// the parent component, e.g. FTable

		public CodeExecutionContext(PageContext pc, FComponent parentComponent)
		{
			super();
			this.pc = pc;
			this.parentComponent = parentComponent;
		}

		/**
		 * Retrieve the {@link URI} of the resource from which this code
		 *         execution has been invoked.
		 * 
		 * @return The {@link URI} of the resource from which this code
		 *         execution has been invoked, or a literal in some special
		 *         cases such as empty nodes.
		 */
		@Override
		public Value getContextValue() {
			if (pc==null || pc.value==null)
				throw new IllegalStateException("Context value is not available. Please report.");
			return pc.value;
		}

		/**
		 * Delegate to {@link #sendRedirect(String, Prio)} with priority set to {@link Prio#VERYEND}.
		 */
		public void sendRedirect(String url) {
			sendRedirect(url, Prio.VERYEND);
		}

		/**
		 * Sends a redirection using an {@link FClientUpdate}. The URL could be absolute, e.g. {@code http://www.fluidops.com/},
		 * relative to the installation's root, e.g. {@code /int/resource/Home} or relative to the page that triggered
		 * the invocation of this code (e.g. by means of a {@link CodeExecutionWidget}).
		 * 
		 * @param url The URL to which the user will be redirected.
		 * @param priority The priority of the {@link FClientUpdate}.
		 * @see FClientUpdate#redirect(String, Prio)
		 */
		public void sendRedirect(String url, Prio priority) {
			parentComponent.addClientUpdate(FClientUpdate.redirect(url, priority));
		}

	}
	
	
	/**
	 * Execute the provided config using this service
	 * 
	 * @param config
	 * @param ceCtx
	 * 			a CodeExecutionContext that is prepended to the arguments, if config.parseContext is true. May be null otherwise
	 * @throws IllegalArgumentException
	 * 				if the provided config is invalid
	 * @throws Exception
	 */
	public static Object execute(WidgetCodeConfig config, CodeExecutionContext ceCtx) throws IllegalArgumentException, Exception 
	{
 		CodeExecution ce = new CodeExecution();        
        return ce.run(widgetConfigToCodeConfig(config, ceCtx)); 
	}
	
	/**
	 * Map a {@link WidgetCodeConfig} to the {@link CodeExecution.Config}, which is used
	 * for actual method invocation.
	 * 
	 * @param config
	 * @param ceCtx
	 * 			a CodeExecutionContext that is prepended to the arguments, if config.parseContext is true. May be null otherwise
	 * @return
	 * @throws IllegalArgumentException
	 * 				if the provided configuration is invalid
	 */
	public static Config widgetConfigToCodeConfig(WidgetCodeConfig config, CodeExecutionContext ceCtx) throws IllegalArgumentException {
		config.args = config.args==null ? Collections.emptyList() : config.args;
    	
		if (StringUtil.isNullOrEmpty(config.method))
			throw new IllegalArgumentException("widgetCodeConfig.method must not be null.");
		if (StringUtil.isNullOrEmpty(config.clazz))
			throw new IllegalArgumentException("widgetCodeConfig.clazz must not be null.");
		
		List<Object> newArgs = new ArrayList<Object>();
		if (config.passContext!=null && config.passContext) 
		{
			if (ceCtx==null)
				throw new IllegalArgumentException("CodeExecutionContext must not be null, if config.parseConfig is true" );
			newArgs.add(ceCtx);
		}
		newArgs.addAll(config.args);
		
        CodeExecution.Config c = new CodeExecution.Config();
        c.clazz = config.clazz;
        c.method = config.method;
        c.args = CodeConfigUtil.computeArgs(config.clazz, config.method, newArgs);
        c.signature = new Class[c.args.length];
        for (int i=0; i<c.args.length; i++)
            c.signature[i] = c.args[i]==null ? null : c.args[i].getClass();
        return c;
	}
	
	/**
	 * Code Execution Configuration
	 */
    public static class Config implements Serializable, Cloneable
    {
		private static final long serialVersionUID = -6191105784376426754L;

		// the class name
        public String clazz;
        
        // the method name inside the class (must be static and
        // annotated with @CallableFromWidget
        public String method;
        
        // the signature of the method the call
        public Class<?>[] signature;
        
        /*
		 * the parameters of the method. Transient because it's manually
		 * serialized to handle the case of non-serializable arguments
		 */
        transient public Object[] args;
        
        /*
		 * overwrite writeObject/readObject to handle arguments that are not
		 * serializable. In this case the arguments are ignored for the
		 * serialization
		 */
        private void writeObject(ObjectOutputStream oos) throws IOException {            
            // check if arguments are all serializable
            // use null as fallback for non-serializable values
            if (hasSerializableArguments())
            	oos.writeObject(args);
            else
            	oos.writeObject(null);
            
            oos.defaultWriteObject();
        }
        
        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        	args = (Object[]) ois.readObject();
        	
        	ois.defaultReadObject();
        }

		public boolean hasSerializableArguments() {
            for (int i = 0; i < args.length; i++) {
				if (!(args[i] instanceof Serializable)) {
					logger.error("CodeExecution Config has non-serializable argument: " + args[i] + " (of type " + args[i].getClass() + ")");
					return false;
				}
			}
			return true;
		}
        
		@Override
    	public Config clone()
    	{
    		try
    		{
    			Config res = (Config) super.clone();
    			
    			//deep clone class array
	    		res.signature = new Class<?>[this.signature.length];
	    		for (int i = 0; i < this.signature.length; i++)
	    			res.signature[i] = this.signature[i];
	    		
	    		//deep clone object array
	    		res.args = new Object[this.args.length];
	    		for (int i = 0; i < this.args.length; i++)
	    			res.args[i] = this.args[i];
	    		return res;
			}
    		catch (CloneNotSupportedException e)
    		{
				throw Throwables.propagate(e);
			}
    	}
    	
    	@Override
    	public String toString()
    	{
    		StringBuilder sb = new StringBuilder();
    		sb.append(clazz);
    		sb.append(".");
    		sb.append(method);
    		sb.append("(");
    		if (signature != null)
    		{
	    		for (int i = 0; i < signature.length; i++)
	    		{
	    			if (i > 0) sb.append(", ");
	    			String sign = (signature[i] != null) ? signature[i].getSimpleName() : "null";
	    			sb.append(sign);
	    		}
    		}
    		sb.append(")");
    		return sb.toString();
    	}
    }

    @Override
    public Class<Config> getConfigClass()
    {
        return Config.class;
    }

    @Override
    public Object run(Config in) throws Exception
    {
    	// sanity checks
    	if (StringUtil.isNullOrEmpty(in.clazz))
    		throw new IllegalArgumentException("Config.clazz must not be null.");
    	if (StringUtil.isNullOrEmpty(in.method))
    		throw new IllegalArgumentException("Config.method must not be null.");
    	//initialization with null checks
    	if(in.args == null)
    		in.args = new Object[0];
    	if(in.signature==null)
    		in.signature = new Class<?>[0];
    	
    	// enable dynamic class loader for groovy (must be done because of different thread)
    	DynamicScriptingSupport.installDynamicClassLoader();
    	
    	Class<?> type = DynamicScriptingSupport.loadClass(in.clazz);
    	
    	// try to find method
    	for ( Method m : type.getMethods() ) 
    	{
    		if ( m.getName().equals( in.method ) ) 
    		{
    			if (m.getParameterTypes().length!=in.signature.length)
    				continue;
    			
    			// compare signature
    			boolean matches=true; // does the signature match
    			for (int i=0; i<m.getParameterTypes().length && matches; i++)
    			{
    				Class<?> param = m.getParameterTypes()[i];
    				
    				// if the argument is null, we accept any non-primitive parameter
    				if (in.args[i]==null && !param.isPrimitive())
    					continue;
    				
    				// if the argument is not null, the param must be assignable from the signature
    				if (in.args[i]!=null && param.isAssignableFrom(in.signature[i]))
    					continue;

    				// if none of the two conditions above holds, the method is not applicable
    				matches=false;
    			}
    			
    			if (!matches)
    				continue;	// does not match, just continue
    			
    			if ( m.getAnnotation( CallableFromWidget.class ) == null )
					throw new Exception( "Method " + m + " is not callable from a widget, CallableFromWidget annotation required." );
    			
    			// we found the corresponding method
    			Object obj = Modifier.isStatic( m.getModifiers() ) ? null : type.newInstance();
				
    			try {
    				return m.invoke(obj, in.args);
    			} catch (InvocationTargetException e) {
    				// get the actual cause of an invocation target exception
    				Throwable cause = e.getCause();
    				if (cause!=null && cause instanceof Exception)
    					throw (Exception)cause;
    				throw e;
    			}
    			
    		}    			
    	}
    	
    	throw new Exception("Method " + in.method + " not found, expected signature " + Arrays.toString(in.signature));
    	
    } 
    
    
    private static class CodeConfigUtil {    	
    	
		public static Object[] computeArgs(String clazz, String method, List<Object> args) throws IllegalArgumentException {

    		List<Method> methods = null;
			try	{
				methods = findMatchingMethods(clazz, method, args);
			} catch (ClassNotFoundException e)	{
				throw new IllegalArgumentException("No such class: " + clazz);
			}
    		if (methods.size()==0)
    			throw new IllegalArgumentException("No matching method found for " + method + " having exactly " + args.size() + " parameters.");
    		if (methods.size()>1)
    			throw new IllegalArgumentException("Method " + method + " is ambiguous for " + args.size() + " arguments.");
    		
    		Method m = methods.get(0);
    		java.lang.reflect.Type[] types = m.getGenericParameterTypes();
    		List<Object> newArgs = new ArrayList<Object>();

    		for (int i=0; i<args.size(); i++) {
    			Object arg = args.get(i);
    			if (arg==null) {
    				newArgs.add(null);
    				continue;
    			}    			
    			// if the object fits, just add to new args
    			Class<?> typeClazz = getClassFromType(types[i]);
    			if (List.class.isAssignableFrom(typeClazz))
    				newArgs.add(tryConvert(arg, types[i]));
    			else if (typeClazz.isAssignableFrom(arg.getClass()))
    				newArgs.add(arg);    			
    			else
    				newArgs.add( tryConvert(arg, types[i]));
    		}
    		
    		return newArgs.toArray();
    	}
    	
    	/**
    	 * Conversion rules
    	 * 
    	 * 1) actual=list && target!=list => take first item 
    	 *      (+ try convert to target clazz using {@link OperatorUtil#toTargetType(Value, Class)})
    	 * 2) actual=table && target=value => first item of table
    	 *      (+ try convert to target clazz using {@link OperatorUtil#toTargetType(Value, Class)})
    	 * 3) actual=table && target=list<value> => projection of first column
    	 * 4) actual=table && target=list<list> => entire table as 2d list
    	 * 
    	 * @param arg
    	 * @param type
    	 * @return
    	 */
    	@SuppressWarnings("unchecked")
		private static Object tryConvert(Object arg, java.lang.reflect.Type type)	{
			
    		Class<?> clazz = getClassFromType(type);
    		
    		if (arg instanceof List) {
    			List<Object> list = ((List<Object>)arg);
    			// if target type is not a list, take first item (check if list has single value)
    			if (!List.class.isAssignableFrom(clazz)) {    				
    				if (list.size()>1)
    					throw new IllegalArgumentException("Error during conversion to single value: more than 1 values: " + list);
    				Object firstItem = ((List<Object>)arg).get(0);   		
    				return firstItem instanceof Value ? OperatorUtil.toTargetType((Value)firstItem, clazz) : firstItem;
    			}
    			
    			// else: expected type is list => check list consistency for values
    			else {
    				Class<?> genericType = getGenericInformationFromType(type);
    				if (Value.class.isAssignableFrom(genericType))
    					return toListValueTargetType(list, (Class<? extends Value>)genericType);
    				return arg;
    			}
    		}
    		
    		if (arg instanceof Value) {
       			if (List.class.isAssignableFrom(clazz)) {
    				Class<?> genericType = getGenericInformationFromType(type);
    				if (Value.class.isAssignableFrom(genericType))
    					return OperatorUtil.checkElementsAndGetList(Lists.newArrayList((Value)arg), genericType);
    			}
    			return toTargetType((Value)arg, clazz);
    		}
    		
    		if (arg instanceof SPARQLResultTable) {
    			SPARQLResultTable t = (SPARQLResultTable)arg;
    			if (clazz.isAssignableFrom(SPARQLResultTable.class))
    				return arg;
    			if (Value.class.isAssignableFrom(clazz))
    				return toValueTargetType(t, (Class<? extends Value>) clazz);
    			if (List.class.isAssignableFrom(clazz)) {
    				Class<?> genericType = getGenericInformationFromType(type);
    				if (List.class.isAssignableFrom(genericType))
    					return t.data();
        			if (Value.class.isAssignableFrom(genericType))
    					return OperatorUtil.checkElementsAndGetList(t.column(t.getBindingNames().get(0)), genericType);    				
    			}
    			return toTargetType(t, clazz);
    		}    		
    		
    		return arg;
		}
    	
    	
    	
    	private static Class<?> getClassFromType(java.lang.reflect.Type type) {
    		if (type instanceof Class<?>)
				return (Class<?>)type;
			if (type instanceof ParameterizedType)
				return (Class<?>)((ParameterizedType)type).getRawType();
			throw new IllegalArgumentException("No class information could be found for " + type.toString());
    	}
    	
    	private static Class<?> getGenericInformationFromType(java.lang.reflect.Type type) {
    		if (type instanceof ParameterizedType)
    			return getClassFromType(((ParameterizedType)type).getActualTypeArguments()[0]);
    		throw new IllegalArgumentException("Type " + type.toString() + " is not parametrized");
    	}

    	
    	private static Object toTargetType(Value value, Class<?> genericType) {
    		try {
    			return OperatorUtil.toTargetType(value, genericType);
    		} catch (IllegalArgumentException e) {
    			throw new IllegalArgumentException("Error during conversion to single value: " + e.getMessage());
    		}
    	}
    	
    	private static Object toTargetType(SPARQLResultTable t, Class<?> genericType) {
    		try {
    			return OperatorUtil.toTargetType(t.firstBindingAssumeSingleRow(), genericType);
    		} catch (IllegalArgumentException e) {
    			throw new IllegalArgumentException("Error during conversion to single value: " + e.getMessage());
    		}
    	}  
    	
    	private static Value toValueTargetType(SPARQLResultTable t, Class<? extends Value> genericType) {
    		try {
    			return OperatorUtil.toValueTargetType(t.firstBindingAssumeSingleRow(), genericType);
    		} catch (IllegalArgumentException e) {
    			throw new IllegalArgumentException("Error during conversion to single value: " + e.getMessage());
    		}
    	}
    	
    	/**
    	 * Verify that the list is in accordance to the provided generic type. Uses
    	 * {@link OperatorUtil#checkElementsAndGetList(List, Class)}. If the list
    	 * contains non-Value objects, or if the list is not consistent with the
    	 * genericType (URI vs. Literal), an {@link IllegalArgumentException} is
    	 * thrown.
    	 * 
    	 * @param list
    	 * @param genericType
    	 * @return
    	 */
    	@SuppressWarnings("unchecked")
		private static List<Value> toListValueTargetType(List<Object> list, Class<? extends Value> genericType) throws IllegalArgumentException {
    		
    		List<Value> valueList = Lists.newArrayList();
    		for (Object o : list) {
    			if (!(o instanceof Value))
    				throw new IllegalArgumentException("Object " + o + " cannot be converted to Value.");
    			valueList.add((Value) o);
    		}
    		
    		return (List<Value>) OperatorUtil.checkElementsAndGetList(valueList, genericType);
    	}

		/**
    	 * Return all methods that match the given configuration, i.e. all methods with
    	 * the given name in clazz that have the correct number of parameters.
    	 * 
    	 * @param clazz
    	 * @param method
    	 * @param args
    	 * @return
    	 * @throws ClassNotFoundException 
    	 * @throws Exception
    	 */
    	private static List<Method> findMatchingMethods(String clazz, String method, List<Object> args) throws ClassNotFoundException  {
    		
    		List<Method> res = new ArrayList<Method>();
    		
    		// enable dynamic class loader for groovy (must be done because of different thread)
        	DynamicScriptingSupport.installDynamicClassLoader();
        	
    		Class<?> type = DynamicScriptingSupport.loadClass(clazz);
        	
        	// try to find method
        	for (Method m : type.getMethods())	{
        		if ( m.getName().equals(method) && m.getParameterTypes().length==args.size() 
        				&& m.getAnnotation(CallableFromWidget.class)!=null)
        			res.add(m);
        	}
        	
        	return res;
    	}
    }

    /**
	 * Service method to link to a specific resource URL. If openInNewWindow is
	 * true, the specified URL is opened in a new window or tab depending on 
	 * the browser.
	 * 
	 * {{#widget: com.fluidops.iwb.widget.CodeExecutionWidget
		| label = 'Click Me'
		| render = 'link'
		| clazz = 'com.fluidops.iwb.service.CodeExecution'
		| method = 'linkTo'
		| args = {{ 'http://www.google.de' | true }}
		| passContext = true
		| onFinish = none
		}}
	 * 
	 * @param ceCtx
	 * @param url
	 * @param openInNewWindow
	 */
	@CallableFromWidget
	public static void linkTo(CodeExecutionContext ceCtx, String url, Boolean openInNewWindow) {
		if (openInNewWindow) {
			ceCtx.parentComponent.doCallback("window.open('" + url + "', '_blank'); window.focus();");
		} else {
			ceCtx.parentComponent.doCallback("document.location = '" + url + "';");
		}
	}
}
