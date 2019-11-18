/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 代表类型的元信息
 * @author Clinton Begin
 */
public class MetaClass {

	private final ReflectorFactory reflectorFactory;
	private final Reflector reflector;

	// 私有构造方法
	private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
		this.reflectorFactory = reflectorFactory;
		this.reflector = reflectorFactory.findForClass(type);
	}
	
	/**
	 * 使用静态方法创建MetaClass对象.
	 * @param type
	 * @param reflectorFactory
	 * @return
	 */
	public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
		return new MetaClass(type, reflectorFactory);
	}
	
	/**
	 * 创建类中属性的类型对应的MetaClass.
	 * @param name
	 * @return
	 */
	public MetaClass metaClassForProperty(String name) {
		// 根据属性名称，得到其类型
		Class<?> propType = reflector.getGetterType(name);
		return MetaClass.forClass(propType, reflectorFactory);
	}

	public String findProperty(String name) {
		StringBuilder prop = buildProperty(name, new StringBuilder());
		return prop.length() > 0 ? prop.toString() : null;
	}

	public String findProperty(String name, boolean useCamelCaseMapping) {
		if (useCamelCaseMapping) {
			name = name.replace("_", "");
		}
		return findProperty(name);
	}

	public String[] getGetterNames() {
		return reflector.getGetablePropertyNames();
	}

	public String[] getSetterNames() {
		return reflector.getSetablePropertyNames();
	}

	public Class<?> getSetterType(String name) {
		PropertyTokenizer prop = new PropertyTokenizer(name);
		if (prop.hasNext()) {
			MetaClass metaProp = metaClassForProperty(prop.getName());
			return metaProp.getSetterType(prop.getChildren());
		} else {
			return reflector.getSetterType(prop.getName());
		}
	}

	public Class<?> getGetterType(String name) {
		PropertyTokenizer prop = new PropertyTokenizer(name);
		if (prop.hasNext()) {
			MetaClass metaProp = metaClassForProperty(prop);
			return metaProp.getGetterType(prop.getChildren());
		}
		// issue #506. Resolve the type inside a Collection Object
		return getGetterType(prop);
	}
	
	/**
	 * 获取 PropertyTokenizer 中 name 对应的类型的 MetaClass
	 * @param prop
	 * @return
	 */
	private MetaClass metaClassForProperty(PropertyTokenizer prop) {
		Class<?> propType = getGetterType(prop);
		return MetaClass.forClass(propType, reflectorFactory);
	}

	/**
	 * 获取 PropertyTokenizer 中 name 对应的class
	 * @param prop
	 * @return
	 */
	private Class<?> getGetterType(PropertyTokenizer prop) {
		// 获取属性的类型
		Class<?> type = reflector.getGetterType(prop.getName());
		// 如果当前属性表示的是一个集合
		if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
			Type returnType = getGenericGetterType(prop.getName());
			if (returnType instanceof ParameterizedType) {
				Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
				if (actualTypeArguments != null && actualTypeArguments.length == 1) {
					returnType = actualTypeArguments[0];
					if (returnType instanceof Class) {
						type = (Class<?>) returnType;
					} else if (returnType instanceof ParameterizedType) {
						type = (Class<?>) ((ParameterizedType) returnType).getRawType();
					}
				}
			}
		}
		return type;
	}
	
	/**
	 * 查找出 propertyName 对应的类型
	 * @param propertyName
	 * @return
	 */
	private Type getGenericGetterType(String propertyName) {
		try {
			Invoker invoker = reflector.getGetInvoker(propertyName);
			if (invoker instanceof MethodInvoker) {
				// 如果有属性方法，根据属性方法的返回值，解析propertyName类型
				Field _method = MethodInvoker.class.getDeclaredField("method");
				_method.setAccessible(true);
				Method method = (Method) _method.get(invoker);
				return TypeParameterResolver.resolveReturnType(method, reflector.getType());
			} else if (invoker instanceof GetFieldInvoker) {
				// 如果未提供属性方法，通过字段的类型，解析propertyName的类型
				Field _field = GetFieldInvoker.class.getDeclaredField("field");
				_field.setAccessible(true);
				Field field = (Field) _field.get(invoker);
				return TypeParameterResolver.resolveFieldType(field, reflector.getType());
			}
		} catch (NoSuchFieldException e) {
		} catch (IllegalAccessException e) {
		}
		return null;
	}

	public boolean hasSetter(String name) {
		PropertyTokenizer prop = new PropertyTokenizer(name);
		if (prop.hasNext()) {
			if (reflector.hasSetter(prop.getName())) {
				MetaClass metaProp = metaClassForProperty(prop.getName());
				return metaProp.hasSetter(prop.getChildren());
			} else {
				return false;
			}
		} else {
			return reflector.hasSetter(prop.getName());
		}
	}
	
	/**
	 * 表达式中的属性，是否都可读
	 * @param name
	 * @return
	 */
	public boolean hasGetter(String name) {
		PropertyTokenizer prop = new PropertyTokenizer(name);
		// 表达式还存在子表达式，需要进入递归，继续判断
		if (prop.hasNext()) { 
			// 是否可读（通过getter读取、通过字段反射读取）
			if (reflector.hasGetter(prop.getName())) {
				MetaClass metaProp = metaClassForProperty(prop);
				// 检查子表达式是否可读
				return metaProp.hasGetter(prop.getChildren());
			} else {
				return false;
			}
		} else {
			return reflector.hasGetter(prop.getName());
		}
	}

	public Invoker getGetInvoker(String name) {
		return reflector.getGetInvoker(name);
	}

	public Invoker getSetInvoker(String name) {
		return reflector.getSetInvoker(name);
	}
	
	/**
	 * 将表达式比如：a[0].b 或者A[0].B 解析为简单/合法/统一的 a.b 表达式
	 */
	private StringBuilder buildProperty(String name, StringBuilder builder) {
		PropertyTokenizer prop = new PropertyTokenizer(name);
		if (prop.hasNext()) {
			// 查找合法的属性名称
			String propertyName = reflector.findPropertyName(prop.getName());
			if (propertyName != null) {
				builder.append(propertyName);
				builder.append(".");
				// 递归进入，拼接子表达式
				MetaClass metaProp = metaClassForProperty(propertyName);
				metaProp.buildProperty(prop.getChildren(), builder);
			}
		} else {
			String propertyName = reflector.findPropertyName(name);
			if (propertyName != null) {
				builder.append(propertyName);
			}
		}
		return builder;
	}

	public boolean hasDefaultConstructor() {
		return reflector.hasDefaultConstructor();
	}

}
