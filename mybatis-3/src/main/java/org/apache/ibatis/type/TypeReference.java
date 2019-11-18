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
package org.apache.ibatis.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * References a generic type.
 *
 * @param <T> the referenced type
 * @since 3.1.0
 * @author Simone Tripodi
 */
public abstract class TypeReference<T> {

	private final Type rawType;

	protected TypeReference() {
		rawType = getSuperclassTypeParameter(getClass());
	}

	Type getSuperclassTypeParameter(Class<?> clazz) {
		// 因为 TypeReference 中定义了一个 泛型<T>，所以，在继承 TypeReference 的类中，向上查找，
		// 一定可以解析得到 <T> 的具体类型
		Type genericSuperclass = clazz.getGenericSuperclass();
		if (genericSuperclass instanceof Class) {
			// try to climb up the hierarchy until meet something useful
			if (TypeReference.class != genericSuperclass) {
				return getSuperclassTypeParameter(clazz.getSuperclass());
			}
			
			// 如果一直查找到 TypeReference ，仍然没有泛型类，这说明，忽略了泛型，抛出异常
			// 忽略的泛型：class A extends TypeReference {...}， TypeReference 后边没有<XXX>
			throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
					+ "Remove the extension or add a type parameter to it.");
		}
		
		// 父类是泛型类，则泛型类的类型，就是需要的类型
		Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
		// TODO remove this when Reflector is fixed to return Types
		if (rawType instanceof ParameterizedType) {
			rawType = ((ParameterizedType) rawType).getRawType();
		}

		return rawType;
	}

	public final Type getRawType() {
		return rawType;
	}

	@Override
	public String toString() {
		return rawType.toString();
	}

}
