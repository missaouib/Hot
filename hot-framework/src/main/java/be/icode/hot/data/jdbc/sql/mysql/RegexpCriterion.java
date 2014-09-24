package be.icode.hot.data.jdbc.sql.mysql;

import be.icode.hot.data.criterion.Operator;
import be.icode.hot.data.jdbc.sql.criterion.CriterionImpl;

public class RegexpCriterion extends CriterionImpl {
	
	private static final String CRITERION_PATTERN = "%s REGEXP :%s_%s";

	public RegexpCriterion(String parameterName, int index) {
		super(Operator.$regex, parameterName, index);
	}

	@Override
	public String toString() {
		return String.format(CRITERION_PATTERN, parameterName,substitutionParameterName,index);
	}
}
