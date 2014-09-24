package be.icode.hot.js;

import java.util.List;

import be.icode.hot.JSR223ScriptExecutor;

public class JS223ScriptExecutor extends JSR223ScriptExecutor {

	public JS223ScriptExecutor() {
		super("javascript");
	}

	public JS223ScriptExecutor(List<String> globalScriptsPaths) {
		super("javascript", globalScriptsPaths);
	}
}
