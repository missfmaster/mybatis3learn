package org.test1;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

public class Main {

	public static void main(String[] args) throws IOException {
		
		String resource = "/mybatis.test1.cfg.xml";
		InputStream inputStream = Main.class.getResourceAsStream(resource);
		
		SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
		
		SqlSession session = sqlSessionFactory.openSession();
		Map<String, Object> parameter = new HashMap<>();
		parameter.put("id", 1);
		BiuBiu biu = session.selectOne("org.test1.BiuBiuMapper.selectBiuBiu", parameter);
		System.out.println(biu);
		
		session.close();
		inputStream.close();
	}

}
