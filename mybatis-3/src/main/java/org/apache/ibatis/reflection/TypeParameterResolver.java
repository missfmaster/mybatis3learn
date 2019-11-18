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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * 可以解析出字段、方法参数、返回值的具体类型，即使含有泛型化，也会被解析出来。
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

	/**
	 * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
	 *         they will be resolved to the actual runtime {@link Type}s.
	 */
	public static Type resolveFieldType(Field field, Type srcType) {
		// 获取字段的声明类型
		Type fieldType = field.getGenericType();
		// 获取字段所在类的class对象
		Class<?> declaringClass = field.getDeclaringClass();
		return resolveType(fieldType, srcType, declaringClass);
	}

	/**
	 * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
	 *         they will be resolved to the actual runtime {@link Type}s.
	 */
	public static Type resolveReturnType(Method method, Type srcType) {
		Type returnType = method.getGenericReturnType();
		Class<?> declaringClass = method.getDeclaringClass();
		return resolveType(returnType, srcType, declaringClass);
	}

	/**
	 * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the
	 *         declaration,<br>
	 *         they will be resolved to the actual runtime {@link Type}s.
	 */
	public static Type[] resolveParamTypes(Method method, Type srcType) {
		Type[] paramTypes = method.getGenericParameterTypes();
		Class<?> declaringClass = method.getDeclaringClass();
		Type[] result = new Type[paramTypes.length];
		for (int i = 0; i < paramTypes.length; i++) {
			result[i] = resolveType(paramTypes[i], srcType, declaringClass);
		}
		return result;
	}

	/**
	 * 根据类型的层级关系，解析出类型更准确的类型
	 * @param type 具体type
	 * @param srcType 此type起始的位置
	 * @param declaringClass 此type定义的class
	 * @return
	 */
	private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
		if (type instanceof TypeVariable) {
			// 解析变量类型
			return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
		} else if (type instanceof ParameterizedType) {
			// 解析参数化类型，
			return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
		} else if (type instanceof GenericArrayType) {
			// 解析数组类型
			return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
		} else {
			return type;
		}
		// 唯独没有通配符类型（WildcardType）
	}

	private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
		Type componentType = genericArrayType.getGenericComponentType();
		Type resolvedComponentType = null;
		if (componentType instanceof TypeVariable) {
			resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
		} else if (componentType instanceof GenericArrayType) {
			resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
		} else if (componentType instanceof ParameterizedType) {
			resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
		}
		if (resolvedComponentType instanceof Class) {
			return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
		} else {
			return new GenericArrayTypeImpl(resolvedComponentType);
		}
	}
	
	/**
	 * @param parameterizedType 字段
	 * @param srcType 字段出现在的类（可能是从父类继承过来的字段）
	 * @param declaringClass 实际定义字段的类（可能在srcType中，也可能在srcType的父类中）
	 * @return
	 */
	private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
		// Map<k, V> -> Map.class
		Class<?> rawType = (Class<?>) parameterizedType.getRawType();
		// Map<K, V> -> [K, V]
		Type[] typeArgs = parameterizedType.getActualTypeArguments();
		Type[] args = new Type[typeArgs.length];
		for (int i = 0; i < typeArgs.length; i++) {
			if (typeArgs[i] instanceof TypeVariable) {
				// 如果是简单的类型变量，直接解析
				args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
			} else if (typeArgs[i] instanceof ParameterizedType) {
				// 如果是嵌套的泛型变量，如Map<K, List<V>>
				args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
			} else if (typeArgs[i] instanceof WildcardType) {
				// 如果是通配符
				args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
			} else {
				args[i] = typeArgs[i];
			}
		}
		return new ParameterizedTypeImpl(rawType, null, args);
	}

	private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
		Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
		Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
		return new WildcardTypeImpl(lowerBounds, upperBounds);
	}

	private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
		Type[] result = new Type[bounds.length];
		for (int i = 0; i < bounds.length; i++) {
			if (bounds[i] instanceof TypeVariable) {
				result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
			} else if (bounds[i] instanceof ParameterizedType) {
				result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
			} else if (bounds[i] instanceof WildcardType) {
				result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
			} else {
				result[i] = bounds[i];
			}
		}
		return result;
	}

	private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
		Type result = null;
		Class<?> clazz = null; // 类型变量所在类
		if (srcType instanceof Class) { // 如果类型变量所在类是一个普通类
			clazz = (Class<?>) srcType;
		} else if (srcType instanceof ParameterizedType) { // 如果类型变量所在类是一个泛型类
			ParameterizedType parameterizedType = (ParameterizedType) srcType;
			clazz = (Class<?>) parameterizedType.getRawType(); // 取得此泛型类的具体类型
		} else {
			throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
		}

		if (clazz == declaringClass) { 
			// 如果类型变量所在的类，就是声明他的类，此处不含有层级继承关系
			// 此直接可以从本类中，获取到类型变量的具体类型
			Type[] bounds = typeVar.getBounds();
			if (bounds.length > 0) {
				return bounds[0];
			}
			return Object.class;
		}
		
		// 如果不是从本类中定义，则相上级，查找是否在父类中定义
		Type superclass = clazz.getGenericSuperclass();
		result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
		if (result != null) {
			return result;
		}
		
		// 如果不是继承而来，则查找接口中是否存在
		Type[] superInterfaces = clazz.getGenericInterfaces();
		for (Type superInterface : superInterfaces) {
			// 扫描父接口，查看是否在父接口声明此类型
			result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
			if (result != null) {
				return result;
			}
		}
		return Object.class;
	}

	/**
	 * 递归扫描其父级。
	 * 此处需要明确一个概念：
	 * 1、泛型，在某个类被声明；
	 * 2、继承此类的类会继承泛型，且一般会在继承时（extends或者implements后边）说明具体类型；
	 * 3、继承泛型类时，继承过来的方法，获取到的方法签名，仍然是泛型化的，和声明此泛型方法的类获取的方法签名一致；
	 * 4、若想获取子类中泛型变量的具体类型，必须先查找到此泛型变量在顶层类声明其时的位置，然后再根据此位置，去继承此泛型类的声明去查找相应位置的类型; 
	 * @param typeVar 类型变量
	 * @param srcType 类型变量出现在的类
	 * @param declaringClass 定义此变量的类
	 * @param clazz 等同srcType，但是非泛型
	 * @param superclass clazz的上一级
	 * @return
	 */
	private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
		Type result = null;
		if (superclass instanceof ParameterizedType) { // 如果父类是泛型类，则可能是从父类继承来
			ParameterizedType parentAsType = (ParameterizedType) superclass;
			Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
			// 如果父类和定义此类型变量的类是同一个类，说明此类型变量的具体类型可能在此父类中
			if (declaringClass == parentAsClass) {
				// 此type含有具体类型
				Type[] typeArgs = parentAsType.getActualTypeArguments();
				// 此type只是泛化的参数
				TypeVariable<?>[] declaredTypeVars = declaringClass.getTypeParameters();
				for (int i = 0; i < declaredTypeVars.length; i++) {
					if (declaredTypeVars[i] == typeVar) { 
						// 通过寻找 继承而来的类型变量 在声明他的类中的位置，去含有具体类型的 typeArgs 中查找具体是何类型
						if (typeArgs[i] instanceof TypeVariable) {
							TypeVariable<?>[] typeParams = clazz.getTypeParameters();
							for (int j = 0; j < typeParams.length; j++) {
								if (typeParams[j] == typeArgs[i]) {
									if (srcType instanceof ParameterizedType) {
										result = ((ParameterizedType) srcType).getActualTypeArguments()[j];
									}
									break;
								}
							}
						} else {
							result = typeArgs[i];
						}
					}
				}
			} else if (declaringClass.isAssignableFrom(parentAsClass)) {
				// 泛型可继承，如果此父类从定义此类型变量的类继承而来，则向上推进一层
				result = resolveTypeVar(typeVar, parentAsType, declaringClass);
			}
		} else if (superclass instanceof Class) {
			if (declaringClass.isAssignableFrom((Class<?>) superclass)) {
				result = resolveTypeVar(typeVar, superclass, declaringClass);
			}
		}
		return result;
	}

	private TypeParameterResolver() {
		super();
	}

	static class ParameterizedTypeImpl implements ParameterizedType {
		
		private Class<?> rawType;

		private Type ownerType;

		private Type[] actualTypeArguments;

		public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
			super();
			this.rawType = rawType;
			this.ownerType = ownerType;
			this.actualTypeArguments = actualTypeArguments;
		}

		@Override
		public Type[] getActualTypeArguments() {
			return actualTypeArguments;
		}

		@Override
		public Type getOwnerType() {
			return ownerType;
		}

		@Override
		public Type getRawType() {
			return rawType;
		}

		@Override
		public String toString() {
			return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments="
					+ Arrays.toString(actualTypeArguments) + "]";
		}
	}

	static class WildcardTypeImpl implements WildcardType {
		private Type[] lowerBounds;

		private Type[] upperBounds;

		private WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
			super();
			this.lowerBounds = lowerBounds;
			this.upperBounds = upperBounds;
		}

		@Override
		public Type[] getLowerBounds() {
			return lowerBounds;
		}

		@Override
		public Type[] getUpperBounds() {
			return upperBounds;
		}
	}

	static class GenericArrayTypeImpl implements GenericArrayType {
		private Type genericComponentType;

		private GenericArrayTypeImpl(Type genericComponentType) {
			super();
			this.genericComponentType = genericComponentType;
		}

		@Override
		public Type getGenericComponentType() {
			return genericComponentType;
		}
	}
}
