package org.apache.ibatis.reflection.typeparam;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.TypeParameterResolver;

public class Level3List {

	public interface Level0Mapper<L, M, N> {

		void simpleSelectVoid(Integer param);

		double simpleSelectPrimitive(int param);

		Double simpleSelect();

		List<Double> simpleSelectList();

		Map<Integer, Double> simpleSelectMap();

		String[] simpleSelectArray();

		String[][] simpleSelectArrayOfArray();

		<K extends Calculator<?>> K simpleSelectTypeVar();

		List<? extends String> simpleSelectWildcard();

		N select(N param);

		List<N> selectList(M param1, N param2);

		List<? extends N> selectWildcardList();

		Map<N, M> selectMap();

		N[] selectArray(List<N>[] param);

		N[][] selectArrayOfArray();

		List<N>[] selectArrayOfList();

		Calculator<N> selectCalculator(Calculator<N> param);

		List<Calculator<L>> selectCalculatorList();

	}
	
	public interface Level1Mapper<E, F> extends Level0Mapper<E, F, String> {
		
	}
	
	public interface Level2Mapper extends Level1Mapper<Date, Integer>, Serializable, Comparable<Integer> {
		
	}
	
	public interface Level0<K, V> {
		
		V getValue();
		
	}
	
	public interface Level1<K, V> extends Level0<K, V> {
		
	}
	
	public interface Level2<K> extends Level1<K, String> {
		
	}
	
	
	public interface Level3 extends Level2<Integer> {
		
	}
	
	public static void main(String[] args) throws Exception {
		Method m = Level3.class.getMethod("getValue");
		Type type = TypeParameterResolver.resolveReturnType(m, Level3.class);
		System.out.println(type);
	}
	
	public static class Level4Mapper implements Level2Mapper {

		private static final long serialVersionUID = 1L;

		@Override
		public void simpleSelectVoid(Integer param) {
		}

		@Override
		public double simpleSelectPrimitive(int param) {
			return 0;
		}

		@Override
		public Double simpleSelect() {
			return null;
		}

		@Override
		public List<Double> simpleSelectList() {
			return null;
		}

		@Override
		public Map<Integer, Double> simpleSelectMap() {
			return null;
		}

		@Override
		public String[] simpleSelectArray() {
			return null;
		}

		@Override
		public String[][] simpleSelectArrayOfArray() {
			return null;
		}

		@Override
		public <K extends Calculator<?>> K simpleSelectTypeVar() {
			return null;
		}

		@Override
		public List<? extends String> simpleSelectWildcard() {
			return null;
		}

		@Override
		public String select(String param) {
			return null;
		}

		@Override
		public List<String> selectList(Integer param1, String param2) {
			return null;
		}

		@Override
		public List<? extends String> selectWildcardList() {
			return null;
		}

		@Override
		public Map<String, Integer> selectMap() {
			return null;
		}

		@Override
		public String[] selectArray(List<String>[] param) {
			return null;
		}

		@Override
		public String[][] selectArrayOfArray() {
			return null;
		}

		@Override
		public List<String>[] selectArrayOfList() {
			return null;
		}

		@Override
		public Calculator<String> selectCalculator(Calculator<String> param) {
			return null;
		}

		@Override
		public List<Calculator<Date>> selectCalculatorList() {
			return null;
		}

		@Override
		public int compareTo(Integer o) {
			return 0;
		}
		
	}
	
}
