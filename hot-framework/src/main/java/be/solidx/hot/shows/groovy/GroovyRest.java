package be.solidx.hot.shows.groovy;

import java.util.concurrent.ExecutorService;

import be.solidx.hot.Closure;
import be.solidx.hot.groovy.GroovyClosure;
import be.solidx.hot.shows.AbstractRest;

public class GroovyRest extends AbstractRest<groovy.lang.Closure<?>> {

	public GroovyRest(ExecutorService eventLoop) {
		super(eventLoop);
	}

	@Override
	protected Closure buildShowClosure(groovy.lang.Closure<?> closure) {
		return new GroovyClosure(closure);
	}
}