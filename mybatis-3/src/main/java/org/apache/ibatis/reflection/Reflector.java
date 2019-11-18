/**
 *    Copyright 2009-2018 the original author or authors.
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * 这个类表示一组缓存的类定义信息，允许在属性名和getter/setter方法之间进行简单的映射。
 * 
 * 这个类为某个class进行反射操作提供便利性。
 * 
 * @author Clinton Begin
 */
public class Reflector {

	// Class类型
	private final Class<?> type;
	
	// 可读属性集合，存在相应 getter 方法的属性
	private final String[] readablePropertyNames;
	// 可写属性集合，存在相应 setter 方法的属性
	private final String[] writeablePropertyNames;
	
	// 属性和其setter方法的集合（还含有调用未提供setter方法的字段的调用对象）
	private final Map<String, Invoker> setMethods = new HashMap<String, Invoker>();
	// 属性和其getter方法的集合
	private final Map<String, Invoker> getMethods = new HashMap<String, Invoker>();
	// 属性和其setter方法的参数值类型
	private final Map<String, Class<?>> setTypes = new HashMap<String, Class<?>>();
	// 属性和其getter方法的返回值类型
	private final Map<String, Class<?>> getTypes = new HashMap<String, Class<?>>();
	
	// 默认构造方法
	private Constructor<?> defaultConstructor;

	private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();

	public Reflector(Class<?> clazz) {
		type = clazz;
		addDefaultConstructor(clazz);
		addGetMethods(clazz);
		addSetMethods(clazz);
		addFields(clazz);
		
		readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
		writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
		for (String propName : readablePropertyNames) {
			caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
		}
		for (String propName : writeablePropertyNames) {
			caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
		}
	}

	private void addDefaultConstructor(Class<?> clazz) {
		Constructor<?>[] consts = clazz.getDeclaredConstructors();
		for (Constructor<?> constructor : consts) {
			if (constructor.getParameterTypes().length == 0) {
				// 查找到不含有参数的 缺省 构造函数
				if (canAccessPrivateMethods()) {
					try {
						constructor.setAccessible(true);
					} catch (Exception e) {
						// Ignored. This is only a final precaution, nothing we can do.
					}
				}
				if (constructor.isAccessible()) {
					this.defaultConstructor = constructor;
				}
			}
		}
	}

	private void addGetMethods(Class<?> cls) {
		Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>();
		Method[] methods = getClassMethods(cls);
		for (Method method : methods) {
			if (method.getParameterTypes().length > 0) {
				// JavaBean规范要求getter方法无参
				continue;
			}
			String name = method.getName();
			if ((name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2)) {
				name = PropertyNamer.methodToProperty(name);
				// 如果一个属性对于多个方法，放入集合，等待处理
				addMethodConflict(conflictingGetters, name, method);
			}
		}
		// 处理冲突的方法，比如：
		// 子类中的  ArrayList#getList() 和 父类 List#getList() 冲突
		resolveGetterConflicts(conflictingGetters);
	}

	/**
	 * 解析冲突的方法，决定使用哪一个方法；
	 * 比如：子类覆盖超类方法，但是返回值不同，则会出现两个方法签名，需要判断使用哪一个；
	 * @param conflictingGetters
	 */
	private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
		for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
			Method winner = null;
			String propName = entry.getKey();
			for (Method candidate : entry.getValue()) {
				if (winner == null) {
					winner = candidate;
					continue;
				}
				Class<?> winnerType = winner.getReturnType();
				Class<?> candidateType = candidate.getReturnType();
				if (candidateType.equals(winnerType)) { 
					// 如果返回值类型是一样的
					if (!boolean.class.equals(candidateType)) {
						// 如果不是 boolean 类型，也就是说：
						// 一个属性，返回值不是 boolean，但是 getter 方法却是使用的 getXxx 或者 isXxx（前边过滤非get/is），这不符合JavaBeans规范.
						throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property " + propName
								+ " in class " + winner.getDeclaringClass()
								+ ". This breaks the JavaBeans specification and can cause unpredictable results.");
					} else if (candidate.getName().startsWith("is")) {
						// 如果返回值是 boolean 类型，则优先使用 isXxx，这符合JavaBeans规范，但是也允许 getXxx 的方式
						winner = candidate;
					}
				} else if (candidateType.isAssignableFrom(winnerType)) {
					// 如果存在派生关系，使用 顶层类型，比如List和ArrayList，使用List.
				} else if (winnerType.isAssignableFrom(candidateType)) {
					// 如果存在派生关系，使用 顶层类型，比如List和ArrayList，使用List.
					winner = candidate;
				} else {
					// 其他，getter 方法不可能返回两个毫不相干的类型，这违反JavaBeans规范
					throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property " + propName
							+ " in class " + winner.getDeclaringClass()
							+ ". This breaks the JavaBeans specification and can cause unpredictable results.");
				}
			}
			addGetMethod(propName, winner);
		}
	}

	private void addGetMethod(String name, Method method) {
		if (isValidPropertyName(name)) {
			getMethods.put(name, new MethodInvoker(method));
			Type returnType = TypeParameterResolver.resolveReturnType(method, type);
			getTypes.put(name, typeToClass(returnType));
		}
	}

	private void addSetMethods(Class<?> cls) {
		Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
		Method[] methods = getClassMethods(cls);
		for (Method method : methods) {
			String name = method.getName();
			if (name.startsWith("set") && name.length() > 3) {
				if (method.getParameterTypes().length == 1) {
					name = PropertyNamer.methodToProperty(name);
					addMethodConflict(conflictingSetters, name, method);
				}
			}
		}
		resolveSetterConflicts(conflictingSetters);
	}

	private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
		List<Method> list = conflictingMethods.get(name);
		if (list == null) {
			list = new ArrayList<Method>();
			conflictingMethods.put(name, list);
		}
		list.add(method);
	}

	private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
		for (String propName : conflictingSetters.keySet()) {
			List<Method> setters = conflictingSetters.get(propName);
			Class<?> getterType = getTypes.get(propName);
			Method match = null;
			ReflectionException exception = null;
			for (Method setter : setters) {
				Class<?> paramType = setter.getParameterTypes()[0];
				if (paramType.equals(getterType)) {
					// should be the best match
					match = setter;
					break;
				}
				if (exception == null) {
					try {
						match = pickBetterSetter(match, setter, propName);
					} catch (ReflectionException e) {
						// there could still be the 'best match'
						match = null;
						exception = e;
					}
				}
			}
			if (match == null) {
				throw exception;
			} else {
				addSetMethod(propName, match);
			}
		}
	}

	private Method pickBetterSetter(Method setter1, Method setter2, String property) {
		if (setter1 == null) {
			return setter2;
		}
		Class<?> paramType1 = setter1.getParameterTypes()[0];
		Class<?> paramType2 = setter2.getParameterTypes()[0];
		if (paramType1.isAssignableFrom(paramType2)) {
			return setter2;
		} else if (paramType2.isAssignableFrom(paramType1)) {
			return setter1;
		}
		throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '" + setter2.getDeclaringClass()
				+ "' with types '" + paramType1.getName() + "' and '" + paramType2.getName() + "'.");
	}

	private void addSetMethod(String name, Method method) {
		if (isValidPropertyName(name)) {
			setMethods.put(name, new MethodInvoker(method));
			Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
			setTypes.put(name, typeToClass(paramTypes[0]));
		}
	}

	private Class<?> typeToClass(Type src) {
		Class<?> result = null;
		if (src instanceof Class) {
			result = (Class<?>) src;
		} else if (src instanceof ParameterizedType) {
			result = (Class<?>) ((ParameterizedType) src).getRawType();
		} else if (src instanceof GenericArrayType) {
			Type componentType = ((GenericArrayType) src).getGenericComponentType();
			if (componentType instanceof Class) {
				result = Array.newInstance((Class<?>) componentType, 0).getClass();
			} else {
				Class<?> componentClass = typeToClass(componentType);
				result = Array.newInstance((Class<?>) componentClass, 0).getClass();
			}
		}
		if (result == null) {
			result = Object.class;
		}
		return result;
	}

	private void addFields(Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			if (canAccessPrivateMethods()) {
				try {
					field.setAccessible(true);
				} catch (Exception e) {
					// Ignored. This is only a final precaution, nothing we can do.
				}
			}
			if (field.isAccessible()) {
				if (!setMethods.containsKey(field.getName())) {
					// issue #379 - removed the check for final because JDK 1.5 allows
					// modification of final fields through reflection (JSR-133). (JGB)
					// pr #16 - final static can only be set by the classloader
					// 上边说：
					// JDK1.5允许通过反射修改 final 的字段；
					// 但是 final + static 的字段只能通过 classloader 来设置
					int modifiers = field.getModifiers();
					// 所以此处 过滤 final + static 修饰的字段
					if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
						addSetField(field);
					}
				}
				if (!getMethods.containsKey(field.getName())) {
					addGetField(field);
				}
			}
		}
		if (clazz.getSuperclass() != null) {
			addFields(clazz.getSuperclass());
		}
	}

	private void addSetField(Field field) {
		if (isValidPropertyName(field.getName())) {
			setMethods.put(field.getName(), new SetFieldInvoker(field));
			Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
			setTypes.put(field.getName(), typeToClass(fieldType));
		}
	}

	// 添加到 getter 集合 和 返回值 集合
	private void addGetField(Field field) {
		if (isValidPropertyName(field.getName())) {
			getMethods.put(field.getName(), new GetFieldInvoker(field));
			Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
			getTypes.put(field.getName(), typeToClass(fieldType));
		}
	}

	private boolean isValidPropertyName(String name) {
		return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
	}

	/*
	 * 此方法返回一个数组，其中包含该类中声明的所有方法和任何超类（所实现的接口和继承的类）中的方法。
	 * 我们使用这个方法，而不是简单的Class.getMethods()，因为我们也想寻找私有方法。
	 *
	 * @param cls The class
	 * 
	 * @return An array containing all methods in this class
	 */
	private Method[] getClassMethods(Class<?> cls) {
		// 记录类中定义的全部方法的 唯一签名 以及对应的 Method 对象
		Map<String, Method> uniqueMethods = new HashMap<String, Method>();
		Class<?> currentClass = cls;
		while (currentClass != null && currentClass != Object.class) {
			// getDeclaredMethods() 返回当前类所有 public、protected、default、private方法，不包含父类方法
			addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

			// 我们还需要寻找接口方法，因为类可能是抽象的
			Class<?>[] interfaces = currentClass.getInterfaces();
			for (Class<?> anInterface : interfaces) {
				// getMethods() 返回当前类及超类、接口的所有public方法.
				// 此处没有使用递归查找父接口，正是因为使用了此方法
				addUniqueMethods(uniqueMethods, anInterface.getMethods());
			}
			// 查找父类中的方法
			currentClass = currentClass.getSuperclass();
		}

		Collection<Method> methods = uniqueMethods.values();

		return methods.toArray(new Method[methods.size()]);
	}

	private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
		for (Method currentMethod : methods) {
			// 桥接方法，与泛型和泛型擦除相关，实在使用泛型时，为了保证兼容性，由编译器自动生成的方法.
			if (!currentMethod.isBridge()) {
				String signature = getSignature(currentMethod);
				// 检测是否在子类中已经添加过该方法，
				// 如果在子类中已经添加过，表示子类覆盖了该方法，
				// 无须再向 uniqueMethods 集合中添加该方法
				if (!uniqueMethods.containsKey(signature)) {
					if (canAccessPrivateMethods()) {
						try {
							currentMethod.setAccessible(true);
						} catch (Exception e) {
							// Ignored. This is only a final precaution, nothing we can do.
						}
					}

					uniqueMethods.put(signature, currentMethod);
				}
			}
		}
	}

	// 获取方法签名，比如方法 String getStr(String, String)，返回: 
	// java.lang.String#getStr:java.lang.String,java.lang.String
	private String getSignature(Method method) {
		StringBuilder sb = new StringBuilder();
		Class<?> returnType = method.getReturnType();
		if (returnType != null) {
			// 返回类型
			sb.append(returnType.getName()).append('#');
		}
		// 方法名
		sb.append(method.getName());
		Class<?>[] parameters = method.getParameterTypes();
		for (int i = 0; i < parameters.length; i++) {
			if (i == 0) {
				// 方法名后添加 :
				sb.append(':');
			} else {
				// 方法参数之间使用 ,
				sb.append(',');
			}
			sb.append(parameters[i].getName());
		}
		return sb.toString();
	}

	private static boolean canAccessPrivateMethods() {
		try {
			SecurityManager securityManager = System.getSecurityManager();
			if (null != securityManager) {
				// 检查是否可以访问 public、default、protected、private
				securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
			}
		} catch (SecurityException e) {
			return false;
		}
		return true;
	}

	/*
	 * Gets the name of the class the instance provides information for
	 *
	 * @return The class name
	 */
	public Class<?> getType() {
		return type;
	}

	public Constructor<?> getDefaultConstructor() {
		if (defaultConstructor != null) {
			return defaultConstructor;
		} else {
			throw new ReflectionException("There is no default constructor for " + type);
		}
	}

	public boolean hasDefaultConstructor() {
		return defaultConstructor != null;
	}

	public Invoker getSetInvoker(String propertyName) {
		Invoker method = setMethods.get(propertyName);
		if (method == null) {
			throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
		}
		return method;
	}
	
	/**
	 * 获取属性的调用者对象，通过此调用者，可以获取属性值。
	 */
	public Invoker getGetInvoker(String propertyName) {
		Invoker method = getMethods.get(propertyName);
		if (method == null) {
			throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
		}
		return method;
	}

	/*
	 * Gets the type for a property setter
	 *
	 * @param propertyName - the name of the property
	 * 
	 * @return The Class of the propery setter
	 */
	public Class<?> getSetterType(String propertyName) {
		Class<?> clazz = setTypes.get(propertyName);
		if (clazz == null) {
			throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
		}
		return clazz;
	}

	/**
	 * 根据 属性名称，获取其对应的 getter 方法的返回值类型，亦可以理解为，根据属性名得到其类型。
	 * @param propertyName
	 * @return
	 */
	public Class<?> getGetterType(String propertyName) {
		Class<?> clazz = getTypes.get(propertyName);
		if (clazz == null) {
			throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
		}
		return clazz;
	}

	/*
	 * Gets an array of the readable properties for an object
	 *
	 * @return The array
	 */
	public String[] getGetablePropertyNames() {
		return readablePropertyNames;
	}

	/*
	 * Gets an array of the writeable properties for an object
	 *
	 * @return The array
	 */
	public String[] getSetablePropertyNames() {
		return writeablePropertyNames;
	}

	/*
	 * Check to see if a class has a writeable property by name
	 *
	 * @param propertyName - the name of the property to check
	 * 
	 * @return True if the object has a writeable property by the name
	 */
	public boolean hasSetter(String propertyName) {
		return setMethods.keySet().contains(propertyName);
	}

	/**
	 * 检查类是否具有按名称可读的属性。
	 * 
	 * @param propertyName
	 * @return
	 */
	public boolean hasGetter(String propertyName) {
		return getMethods.keySet().contains(propertyName);
	}
	
	/**
	 * 根据name查找合法的属性名称，name可以是大写、小写，都会返回合法的属性名称.
	 * @param name
	 * @return
	 */
	public String findPropertyName(String name) {
		return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
	}
}
