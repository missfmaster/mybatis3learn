/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 抽象对象的属性信息，定义了一系列查询/更新对象属性信息的方法.
 * @author Clinton Begin
 */
public interface ObjectWrapper {
	
	/**
	 * 根据表达式，获取属性值.
	 */
	Object get(PropertyTokenizer prop);
	
	/**
	 * 根据表达式，设置属性值.
	 */
	void set(PropertyTokenizer prop, Object value);
	
	/**
	 * 查找表达式指定的属性.
	 * @param name 
	 * @param useCamelCaseMapping 是否忽略表达式中的下划线
	 * @return
	 */
	String findProperty(String name, boolean useCamelCaseMapping);
	
	/**
	 * 获取所有可读属性的名称集合
	 */
	String[] getGetterNames();
	
	/**
	 * 获取所有可写属性的名称集合
	 */
	String[] getSetterNames();
	
	/**
	 * 返回表达式指定属性的setter方法的参数类型
	 * @param name
	 * @return
	 */
	Class<?> getSetterType(String name);
	
	/**
	 * 返回表达式指定属性的getter方法的返回值类型
	 * @param name
	 * @return
	 */
	Class<?> getGetterType(String name);
	
	/**
	 * 判断表达式是否有setter方法
	 * @param name
	 * @return
	 */
	boolean hasSetter(String name);
	
	/**
	 * 判断表达式是否有getter方法
	 * @param name
	 * @return
	 */
	boolean hasGetter(String name);
	
	/**
	 * 为表达式指定的属性，创建MetaObject对象.
	 * @param name
	 * @param prop
	 * @param objectFactory
	 * @return
	 */
	MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);
	
	/**
	 * 封装的对象是否是集合
	 * @return
	 */
	boolean isCollection();
	
	/**
	 * 调用集合的add方法
	 * @param element
	 */
	void add(Object element);
	
	/**
	 * 调用集合的addAll方法
	 * @param <E>
	 * @param element
	 */
	<E> void addAll(List<E> element);

}
