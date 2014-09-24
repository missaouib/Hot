package be.icode.hot.data.jdbc.groovy;

import groovy.transform.CompileStatic

import javax.sql.DataSource

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

import be.icode.hot.data.jdbc.AbstractDB
import be.icode.hot.data.jdbc.sql.QueryBuilder


@CompileStatic
public class DB extends AbstractDB<Map<String, Object>> {
	
	public DB(QueryBuilder queryBuilder, DataSource dataSource, String schema) {
		super(queryBuilder, dataSource, schema);
	}

	@Override
	public JoinableCollection getCollection(String name) {
		return new JoinableCollection(
				new NamedParameterJdbcTemplate(dataSource),
				queryBuilder,
				name,
				tableMetadataMap);
	}
	
	def getProperty (String name) {
		this.getCollection name
	}
}
