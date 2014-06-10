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

package com.fluidops.iwb.widget;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.openrdf.model.Value;

import com.fluidops.iwb.annotation.CallableFromWidget;
import com.fluidops.iwb.model.ParameterConfigDoc;
import com.fluidops.iwb.model.TypeConfigDoc;
import com.fluidops.iwb.service.CodeExecution;
import com.fluidops.iwb.service.CodeExecution.CodeExecutionContext;
import com.fluidops.iwb.util.workflow.CodeExecutionHelper;
import com.fluidops.iwb.widget.BaseExecutionWidget.ExecutionWidgetConfig;

/**
 * Code execution widget for java and groovy code.
 * 
 * 1) leveraged nested parameter parsing
 * 2) supports the new groovy classloading
 * 3) uses regular java / groovy reflection (i.e. any method can be called)
 * 4) parameterize method calls with user input
 * 
 * For a specification and complete usage scenario (e.g. how types can be used),
 * see {@link CodeExecutionWidgetTest}. Additional examples can be found in
 * solutions/testBootstrap => test:CodeExecutionWidget
 * 
 * Component can be styled by using CSS class "CodeExecution".
 * 
 * Examples:
 * 
 * 1. Executing a Java method with signature
 * 
 * @CallableFromWidget
 * public static String testMe(String param, Value a, List<Value> b, List<Value> x)
    
 * <code>
 *	{{#widget: CodeWidget
 *  | label = 'Test Various'
 *  | clazz = 'com.fluidops.iwb.widget.CodeWidget'
 *  | method = 'testMe'
 *  | args = {{ 'Constant' | $this.a$ | $this.b$ | $select ?x where { ?? ?p ?x }$ }}
 *  | confirm = 'Do you really want to execute testMe()'
 *  | onFinish = 'reload'
 *  }}
 * </code> 
 * 
 * <ul>
 * 	<li>Rendered as button with label 'Test Various'. To render as image use type='img:/path/to/img'</li>
 *  <li>Confirmation method is optional</li>
 *  <li>onFinish is optional, in this example reloads page. Alternatives: onFinish=$this.a$, 
 *       onFinish='none', onFinish='http://myuri.com'</li>
 * </ul>
 * 
 * 
 * 2. Executing a groovy script 
 * 
 * <code>
 * {{#widget: CodeExecution 
 *  | label = 'Test Groovy'
 *  | clazz = 'dyn.GroovyTest' 
 *  | method = 'hello' 
 *  | args = {{ 'abc' }}
 *  | passContext = true
 * }}
 * </code>
 * 
 * File: scripts/dyn/GroovyTest.groovy
 * 
 * => an example can be found in solutions/testBootstrap* 
 * 
 * 
 * 3. Executing a method with CodeExecutionContext as first argument
 * 
 * When setting passContext to true, the {@link CodeExecutionContext} is
 * transmitted as the first argument, without having to explicitly add
 * it to the list of arguments. The corresponding signature to the method
 * below looks like
 * 
 * @CallableFromWidget
 * public static void testMe2(CodeExecutionContext ceCtx, Value value)
 * 
 * <code>
 * {{#widget: com.fluidops.iwb.widget.CodeExecutionWidget
 *  | label = 'Test 7'
 *  | clazz = 'com.fluidops.iwb.widget.CodeExecutionWidget'
 *  | method = 'testMe2'
 *  | args = {{ $this.a$ }}
 *  | passContext = true
 *  }}
 * </code>
 * 
 * 4. Letting the code execution appear as a usual link, with a code execution
 *    script that opens a given URL in a new TAB
 *    
 * see also {@link CodeExecution#linkTo(CodeExecutionContext, String, Boolean)}
 * 
 * <code>
	{{#widget: com.fluidops.iwb.widget.CodeExecutionWidget
	| label = 'Click Me'
	| render = 'link'
	| clazz = 'com.fluidops.iwb.service.CodeExecution'
	| method = 'linkTo'
	| args = {{ 'http://www.google.de' | true }}
	| passContext = true
	| onFinish = none
	}}
 * </code>
 * 
 * 5. Parameterize method calls with user input
 * 
 * <code>
	{{#widget: CodeExecution
	 | label = 'Test User Input'
	 | clazz = 'com.fluidops.iwb.widget.CodeExecutionWidget'
	 | method = 'testMe'
	 | args = {{ '%name' | '<http://www.fluidops.com/Constant>' | '%type' }}
	 | userInput = {{ 
	      {{ name = 'name'
	       | componentType = 'SIMPLE'
	       | presetValue = 'Hello, widget'
	      }} | 
	      {{ name = 'type'
	       | componentType = 'DROPDOWN'
	       | selectValues = $select distinct ?type where { ?x rdf:type ?type }$
	      }} }}
	}}
 * </code>
 * 
 * @author (msc), (aeb), as, christian.huetter
 * @see CodeExecutionWidgetTest
 */
@TypeConfigDoc("Widget to invoke pre-defined Java and Groovy methods with parameters specified in wiki notation or provided by the user.")
public class CodeExecutionWidget extends BaseExecutionWidget<CodeExecutionWidget.WidgetCodeConfig>
{
	
	protected static final Logger logger = Logger.getLogger(CodeExecutionWidget.class.getName());
	
	/**
	 * Configuration for coded execution to be used from widgets
	 * 
	 * @author as
	 */
	public static class WidgetCodeConfig extends ExecutionWidgetConfig
	{
    	@ParameterConfigDoc(
    			desc = "Class to be used, full qualified type, either java or groovy.",
    			required=true)
		public String clazz;
		
    	@ParameterConfigDoc(
    			desc = "Method to be executed.",
    			required=true)
		public String method;
    	
    	/* (non-Javadoc)
    	 * @see com.fluidops.iwb.widget.BaseExecutionWidget.ExecutionWidgetConfig#clone()
    	 */
    	@Override
    	public WidgetCodeConfig clone() {
    		return (WidgetCodeConfig)super.clone();
    	}
	}
	
	@Override
	public CodeExecutionHelper getCodeExecutionHelper()
	{
		return new CodeExecutionHelper();
	}

	@Override
	public Class<?> getConfigClass()
	{
		return WidgetCodeConfig.class;
	}

	@Override
	public String getTitle()
	{
		return "Call Java or Groovy";
	}
	
	@Override
	/**
	 * 
	 * @param config
	 * @param ceCtx
	 * @return creates a code config out of a widget config
	 */
	public CodeExecution.Config buildExecutionConfig(CodeExecutionContext ceCtx)
	{
		final CodeExecutionWidget.WidgetCodeConfig config = get();
		return CodeExecution.widgetConfigToCodeConfig(config, ceCtx);
	}
	
	@CallableFromWidget
	public Object test( Object o )
	{
		System.out.println( o );
		return o;
	}
	
    /**
     * This is an example static method that could be called,
     * the widget configuration for calling this method is as follows.
     * 
     * <code>
	 *	{{#widget: CodeWidget
	 *  | label = 'Test Various'
	 *  | clazz = 'com.fluidops.iwb.widget.CodeExecutionWidget'
	 *  | method = 'testMe'
	 *  | args = {{ 'Constant' | $this.a$ | $select ?x where { ?? ?p ?x }$ }}
	 *  | confirm = 'Do you really want to execute testMe()'
	 *  | onFinish = 'reload'
	 *  }}
	 * </code> 
     */
    @CallableFromWidget
    public static String testMe(String param, Value a, Value x)
    {
        System.out.println("Param: " + param);
        System.out.println("a: " + a);
        System.out.println("x: " + x);
        
        return "Executed method successfully!";
    }
    
    /**
     * This is an example static method that could be called,
     * the widget configuration for calling this method is as follows.
     * 
     * <code>
	 *	{{#widget: CodeExecution
	 * | label = 'Test 7'
	 * | clazz = 'com.fluidops.iwb.widget.CodeExecutionWidget'
	 * | method = 'testMe2'
	 * | args = {{ $this.a$ }}
	 * | passContext = true
	 * }}
	 * </code> 
     */
    @SuppressWarnings("deprecation")
	@CallableFromWidget
    public static void testMe2(CodeExecutionContext ceCtx, String value)
    {
		ceCtx.parentComponent.doCallback("alert('Clicked on "
				+ StringEscapeUtils.escapeHtml(value == null ? "(undefined)" : value.toString()
						) + "');");
	}
    
    @CallableFromWidget
    public static void testMe3(String value)
    {
    	System.out.println("Clicked on " + StringEscapeUtils.escapeHtml(value) );
    }

	/* (non-Javadoc)
	 * @see com.fluidops.iwb.widget.BaseExecutionWidget#buildExecutionConfig(com.fluidops.iwb.service.CodeExecution.CodeExecutionContext)
	 */



}
