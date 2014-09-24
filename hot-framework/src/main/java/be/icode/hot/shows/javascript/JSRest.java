package be.icode.hot.shows.javascript;

import java.util.concurrent.ExecutorService;

import org.mozilla.javascript.NativeFunction;
import org.mozilla.javascript.Scriptable;

import be.icode.hot.Closure;
import be.icode.hot.js.JSClosure;
import be.icode.hot.shows.AbstractRest;

public class JSRest extends AbstractRest<NativeFunction> {
	
	Scriptable globalScope;
	
	public JSRest(ExecutorService eventLoop, Scriptable globalScope) {
		super(eventLoop);
		this.globalScope = globalScope;
	}

	@Override
	protected Closure buildShowClosure(NativeFunction closure) {
		return new JSClosure(closure,globalScope);
	}
}
