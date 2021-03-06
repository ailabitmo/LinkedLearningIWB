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

package com.fluidops.iwb.ui.configuration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import com.fluidops.iwb.api.operator.Operator;
import com.fluidops.iwb.api.operator.OperatorNode;
import com.fluidops.iwb.model.ParameterConfigDoc;
import com.fluidops.iwb.model.ParameterConfigDoc.Type;
import com.fluidops.iwb.util.UIUtil;
import com.fluidops.util.AnnotationHelper;
import com.fluidops.util.StringUtil;
import com.fluidops.util.TemplateBuilder;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

/**
 * Configuration class for {@link ConfigurationFormElement}s containing
 * all relevant information.
 * 
 * @author as
 *
 */
public class FormElementConfig {
	
	
	public static ParameterConfigDoc toParameterConfigDoc(String description, Type type, boolean required) {
		return toParameterConfigDoc(null, description, type, required);
	}
	
	public static ParameterConfigDoc toParameterConfigDoc(String label, String description, Type type, boolean required) {
		return toParameterConfigDoc(label, description, type, required, null);
	}
	
	public static ParameterConfigDoc toParameterConfigDoc(String description, Type type, boolean required, List<String> selectValues) {
		return toParameterConfigDoc(null, description, type, required, null);
	}
	
	public static ParameterConfigDoc toParameterConfigDoc(String label, String description, Type type, boolean required, List<String> selectValues) {
		Map<String, Object> defaults = Maps.newHashMap();
		if (!StringUtil.isNullOrEmpty(label))
			defaults.put("displayName", label);
		defaults.put("desc", description);
		defaults.put("type", type);
		defaults.put("required", required);
		if (selectValues != null)
			defaults.put("selectValues", selectValues.toArray(new String[selectValues.size()]));
		return AnnotationHelper.createAnnotation(ParameterConfigDoc.class, defaults, DummyForParamConfig.getDefault());
	}
	
	
	/**
	 * Dummy class to retrieve the default {@link ParameterConfigDoc}
	 * values.
	 */
	private static class DummyForParamConfig { 
		@ParameterConfigDoc(desc="Dummy description")
		public String dummy;
		
		public static ParameterConfigDoc getDefault() {
			try {
				return DummyForParamConfig.class.getField("dummy").getAnnotation(ParameterConfigDoc.class);
			} catch (Exception ignore) {
				throw Throwables.propagate(ignore);
			} 
		}
	}
	
	public final String fieldName;
	public final String label;
	
	public final Class<?> targetType;
	public final Operator presetValue;
	
	public final ParameterConfigDoc parameterConfig;
	
	private final Map<Class<? extends Annotation>, Annotation> annotations = Maps.newHashMap();
	
	private boolean isDeprecated;
	
	static String getDisplayNameOrFieldName(String fieldName, ParameterConfigDoc parameterConfig) {
		if (parameterConfig == null)
			return fieldName;
		
		String displayName = parameterConfig.displayName();
		return getDisplayNameOrFieldName(fieldName, displayName);
	}
	
	static String getDisplayNameOrFieldName(String fieldName, String displayName) {
		if (displayName != null) {
			displayName = displayName.trim();
		}
		if (StringUtil.isNullOrEmpty(displayName)) {
			try {
				// split camelCase name and create capitalized version of field name
				displayName = StringUtil.splitCamelCaseWords(fieldName, true);
			}
			catch (Throwable t) {
				// ignore
			}
		}
		if (StringUtil.isNullOrEmpty(displayName)) {
			displayName = fieldName;
		}
		return displayName;
	}
	
	public FormElementConfig(Field field, Operator presetValue) {
		this(field.getName(), field.getAnnotation(ParameterConfigDoc.class), field.getType(), presetValue);
	}
	
	public FormElementConfig(String fieldName, ParameterConfigDoc parameterConfig, Class<?> targetType, Operator presetValue) {
		this(fieldName, getDisplayNameOrFieldName(fieldName, parameterConfig), parameterConfig, targetType, presetValue);
	}
	
	public FormElementConfig(String fieldName, String label, ParameterConfigDoc parameterConfig, Class<?> targetType, Operator presetValue) {
		super();
		this.fieldName = fieldName;
		this.label = getDisplayNameOrFieldName(fieldName, label);
		this.parameterConfig = parameterConfig;
		this.targetType = targetType;
		this.presetValue = presetValue;
	}
	
	public String help() {
		return UIUtil.configDescription(parameterConfig);
	}
	
	public boolean required() {
		return parameterConfig.required();
	}
	
	/**
	 * Returns true if this instance has some preset value, i.e. an
	 * {@link OperatorNode} different from the noop node.
	 * @return
	 */
	public boolean hasPresetValues() {
		return presetValue!=null && !presetValue.isNoop();
	}
	
	
	/**
	 * Return the default content from the {@link ParameterConfigDoc}
	 * annotation. This is either {@link ParameterConfigDoc#defaultContent()},
	 * or the content retrieved by resolving the
	 * {@link ParameterConfigDoc#defaultContentStringTemplate()}. If none of the
	 * above is set, this method returns the empty string.
	 * 
	 * @return the default content if set or the empty string
	 * @throws RuntimeException
	 *             if the location of a string template is specified but does not exist
	 */
	public String getDefaultContent() throws RuntimeException {
		
		if (!StringUtil.isNullOrEmpty(parameterConfig.defaultContent())) {
			return parameterConfig.defaultContent();
		}
		
		String template = parameterConfig.defaultContentStringTemplate();
		if (!StringUtil.isNullOrEmpty(template)) {			
			TemplateBuilder tb = new TemplateBuilder( "tplForClass", template);
			return tb.renderTemplate();
		}
		
		return "";
	}
	
	/**
	 * Create a copy of this instance with the exact same instance data, excluding
	 * the default value (i.e. clearing the default value)
	 * @return
	 */
	public FormElementConfig copyWithoutDefaults() {
		return copyWithNewDefault(Operator.createNoop());
	}
	
	/**
	 * Create a copy of this instance with the exact same instance data, adding 
	 * the specified value as new default.
	 * 
	 * @param newDefault
	 * @return
	 */
	public FormElementConfig copyWithNewDefault(Operator newDefault) {
		return new FormElementConfig(fieldName, label, parameterConfig, targetType, newDefault);
	}
	
	/**
	 * Register a non-null array of {@link Annotation}s e.g. retrieved from the field.
	 * 
	 * @param annnotations
	 */
	public void addAnnotations(Annotation[] annotations) {
		for (Annotation a : annotations) {
			this.annotations.put(a.annotationType(), a);
		}			
	}
	
	/**
	 * Returns the {@link Annotation} of the given class (if available),
	 * otherwise {@code null}.
	 * 
	 * @param annotationClass
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
		return (T) annotations.get(annotationClass);
	}

	public void setDeprecated(boolean deprecated) {

		this.isDeprecated = deprecated;
	}

	public boolean deprecated() {

		return isDeprecated;
	}
}