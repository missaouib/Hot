package be.icode.hot.data.scripting;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyFunction;
import org.python.core.PyList;
import org.python.core.PyObject;

import be.icode.hot.data.AbstractAsyncDB;
import be.icode.hot.data.AsyncCollection;
import be.icode.hot.data.AsyncCursor;
import be.icode.hot.data.Collection;
import be.icode.hot.data.Cursor;
import be.icode.hot.data.DB;
import be.icode.hot.promises.Deferred;
import be.icode.hot.promises.Promise;
import be.icode.hot.promises.python.PythonDeferred;
import be.icode.hot.python.PythonClosure;

public class PyAsyncDB extends AbstractAsyncDB<PyFunction, PyDictionary> {

	public PyAsyncDB(DB<PyDictionary> db, ExecutorService blockingTasksThreadPool, ExecutorService eventLoop) {
		super(db, blockingTasksThreadPool, eventLoop);
	}

	@Override
	public Object executeClosure(PyFunction closure, Object... args) {
		PythonClosure pythonClosure = new PythonClosure(closure);
		return pythonClosure.call(args);
	}

	@Override
	protected Deferred<PyFunction> buildDeferred() {
		return new PythonDeferred();
	}

	@Override
	protected AsyncCursor<PyFunction, PyDictionary> buildAsyncCursor(Cursor<PyDictionary> cursor) {
		return new PyAsyncCursor(cursor);
	}

	@Override
	protected AsyncCollection<PyFunction, PyDictionary> buildAsyncCollection(Collection<PyDictionary> collection) {
		return new PyAsyncCollection(collection);
	}
	
	public PyObject __getattr__(String name) {
		return Py.java2py(getCollection(name));
	}
	
	public class PyAsyncCollection extends AbstractAsyncCollection {

		public PyAsyncCollection(Collection<PyDictionary> collection) {
			super(collection);
		}
	}

	public class PyAsyncCursor extends AbstractAsyncCursor {

		public PyAsyncCursor(Cursor<PyDictionary> cursor) {
			super(cursor);
		}

		@Override
		public Promise<PyFunction> promise(PyFunction successClosure, PyFunction failClosure) {
			return deferredBlockingCall(new Callable<Object>() {

				@Override
				public Object call() throws Exception {
					PyList results = new PyList();
					for (Object it : cursor) {
						results.add(it);
					}
					return results;
				}
			}, successClosure, failClosure);
		}
	}
}
