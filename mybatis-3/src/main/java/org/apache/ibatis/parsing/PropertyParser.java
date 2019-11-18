/**
 *    Copyright 2009-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

	private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
	/**
	 * 表示是否在占位符上启用默认值的特殊属性键。
	 * <p>
	 * 默认值是{@code false} (表示禁用占位符上的默认值) 
	 * 如果您指定{@code true}，您可以指定占位符上的键和默认值 (e.g. {@code ${db.username:postgres}}).
	 * </p>
	 * 在mybatis-config.xml中 "properties" 节点下配置是否开启默认值功能的对应配置项
	 * 
	 * @since 3.4.2
	 */
	public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

	/**
	 * 在占位符上为键和默认值指定分隔符的特殊属性键。
	 * <p>
	 * 默认的分隔符是{@code ":"}。
	 * </p>
	 * 
	 * @since 3.4.2
	 */
	public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

	/**
	 * 默认情况下，关闭默认值的功能
	 */
	private static final String ENABLE_DEFAULT_VALUE = "false";
	/**
	 * 默认分隔符
	 */
	private static final String DEFAULT_VALUE_SEPARATOR = ":";

	private PropertyParser() {
		// Prevent Instantiation
	}

	public static String parse(String string, Properties variables) {
		VariableTokenHandler handler = new VariableTokenHandler(variables);
		GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
		return parser.parse(string);
	}

	private static class VariableTokenHandler implements TokenHandler {
		
		// <properties>下定义的键值对，用于解析占位符
		private final Properties variables;
		// 占位符使用启用默认值功能
		private final boolean enableDefaultValue;
		// 占位符和默认值分隔符
		private final String defaultValueSeparator;

		private VariableTokenHandler(Properties variables) {
			this.variables = variables;
			this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
			this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
		}

		// 从 variables 获取值
		private String getPropertyValue(String key, String defaultValue) {
			return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
		}

		@Override
		public String handleToken(String content) {
			// content情况1：key:defaultValue 含有默认值
			// content情况2：key 不含默认值 
			if (variables != null) {
				String key = content;
				if (enableDefaultValue) {
					// 如果启用默认值，首先查找是否含有默认值，如果有，当为null时，使用默认值
					final int separatorIndex = content.indexOf(defaultValueSeparator);
					String defaultValue = null;
					if (separatorIndex >= 0) {
						key = content.substring(0, separatorIndex);
						defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
					}
					if (defaultValue != null) {
						return variables.getProperty(key, defaultValue);
					}
				}
				if (variables.containsKey(key)) {
					return variables.getProperty(key);
				}
			}
			// <properties> 集合为空，直接返回
			return "${" + content + "}";
		}
	}

}
