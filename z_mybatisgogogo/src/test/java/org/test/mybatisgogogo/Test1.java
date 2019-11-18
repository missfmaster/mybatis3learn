//package org.test.mybatisgogogo;
//
//import java.sql.Connection;
//import java.sql.DriverManager;
//import java.sql.SQLException;
//
//import org.junit.Test;
//
///**
// * 简单测试。
// * @author xman
// *
// */
//public class Test1 {
//
//	/**
//	 * 测试连接
//	 */
//	@Test
//	public void test1() {
//		try {
//			Class.forName(Config.DRIVER);
//			Connection conn = DriverManager.getConnection(Config.URL, Config.USER, Config.PASSWD);
//			System.out.println(conn);
//			conn.close();
//		} catch (ClassNotFoundException | SQLException e) {
//			e.printStackTrace();
//		}
//	}
//	
//	
//}