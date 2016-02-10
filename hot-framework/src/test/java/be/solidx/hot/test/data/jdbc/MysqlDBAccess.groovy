package be.solidx.hot.test.data.jdbc

import javax.sql.DataSource;

import org.springframework.context.support.ClassPathXmlApplicationContext

import be.solidx.hot.data.DB;
import be.solidx.hot.data.jdbc.sql.QueryBuilderFactory
import be.solidx.hot.data.jdbc.sql.QueryBuilderFactory.DBEngine;

class MysqlDBAccess {
	static def main (args) {
		def appContext = new ClassPathXmlApplicationContext("be/solidx/hot/test/data/jdbc/mysqlDS.xml")
		def test = new TestCollectionApi()

		test.db = appContext.getBean(DB.class)
		test.testFindAll()
		test.testFindJoinWhere1()
		test.testFindWhere1()
		test.testFindWhere2()
		test.testFindWhere3()
		test.testFindWhere4()
		test.testFindWhere5()
		test.testFindWhereLike()
		test.testIn()
		test.testJoinIn()
		test.testFindWhereModulo()
		test.testFindWhereModuloIn()
		test.testJoinInModulo()
		test.testJoinInModuloLimit()
		test.testJoinInModuloLimitOffset()
	}
}