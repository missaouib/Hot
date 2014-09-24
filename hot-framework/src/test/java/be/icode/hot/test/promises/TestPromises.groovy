package be.icode.hot.test.promises

import static org.junit.Assert.*

import java.util.concurrent.Executors;

import javax.script.CompiledScript

import org.apache.commons.io.IOUtils
import org.junit.Test

import be.icode.hot.Script
import be.icode.hot.js.JSScriptExecutor
import be.icode.hot.promises.groovy.GroovyDeferred
import be.icode.hot.promises.js.JSDeferred
import be.icode.hot.promises.python.PythonDeferred
import be.icode.hot.python.PythonScriptExecutor

class TestPromises {

	@Test
	void testGroovyPromisesSucceed() {
		def deferred = new GroovyDeferred()
		def promise = deferred.promise()
		
		promise.done { result ->
			println result
		}
		
		promise.fail { ex ->
			print ex
		}
		
		promise.always {
			println "always"
		}
		
		promise.then { result ->
			"Filtered "+result
		}.done { result ->
			println result
		}
		
		deferred.resolve "Done"
	}
	
	@Test
	void testGroovySubPromisesSucceed() {
		
		Executors.newFixedThreadPool(1).submit(new Runnable(){
			void run () {
				
			}
		})
		
		def deferred = new GroovyDeferred()
		def promise = deferred.promise()
		
		def subDeferred = new GroovyDeferred();
		
		promise.then { result ->
			return subDeferred.promise()
		}.then { result ->
			print result.getClass()
			result + " Done"
		}.done { result ->
			println result
		}
		
		deferred.resolve "Done"
		Thread.sleep(1000)
		subDeferred.resolve "Sub"
	}
	
	@Test
	void testGroovyPromisesFailed() {
		def deferred = new GroovyDeferred()
		def promise = deferred.promise()
		
		promise.done { result ->
			println result
		}
		
		promise.fail { ex ->
			println ex
		}
		
		promise.always {
			println "always"
		}
		
		def p2 = promise.then { result ->
			"Filtered "+result
		}.done { result ->
			println result
		}.fail { e ->
			println "then "+ e
		}
		
		deferred.reject "Ooops"
	}
	
	@Test
	void testPythonPromises() {
		PythonScriptExecutor pythonScriptExecutor = new PythonScriptExecutor();
		String scriptString = IOUtils.toString(TestPromises.class.getResourceAsStream("/be/icode/hot/promise/promises.py"));
		Script<CompiledScript> script = new Script<>(scriptString.getBytes(), "promises.py");
		pythonScriptExecutor.execute(script);
	}
	
	@Test
	void testPythonPromises2() {
		def params = [deferred:new PythonDeferred(),subdeferred:new PythonDeferred()]
		PythonScriptExecutor pythonScriptExecutor = new PythonScriptExecutor();
		String scriptString = IOUtils.toString(TestPromises.class.getResourceAsStream("/be/icode/hot/promise/promises2.py"));
		Script<CompiledScript> script = new Script<>(scriptString.getBytes(), "promises2.py");
		pythonScriptExecutor.execute script,params;
	}
	
	@Test
	void testPythonPromises3() {
		def params = [deferred:new PythonDeferred(),subdeferred:new PythonDeferred()]
		PythonScriptExecutor pythonScriptExecutor = new PythonScriptExecutor();
		String scriptString = IOUtils.toString(TestPromises.class.getResourceAsStream("/be/icode/hot/promise/promises3.py"));
		Script<CompiledScript> script = new Script<>(scriptString.getBytes(), "promises3.py");
		pythonScriptExecutor.execute script,params;
	}
	
	@Test
	void testJsPromises() {
		JSScriptExecutor jsScriptExecutor = new JSScriptExecutor();
		def params = [deferred:new JSDeferred(jsScriptExecutor.globalScope)]
		String scriptString = IOUtils.toString(TestPromises.class.getResourceAsStream("/be/icode/hot/promise/promises.js"));
		Script<CompiledScript> script = new Script<>(scriptString.getBytes(), "promises.js");
		jsScriptExecutor.execute(script,params);
	}
	
	@Test
	void testJsPromises2() {
		JSScriptExecutor jsScriptExecutor = new JSScriptExecutor();
		def params = [deferred:new JSDeferred(jsScriptExecutor.globalScope),subdeferred:new JSDeferred(jsScriptExecutor.globalScope)]
		String scriptString = IOUtils.toString(TestPromises.class.getResourceAsStream("/be/icode/hot/promise/promises2.js"));
		Script<CompiledScript> script = new Script<>(scriptString.getBytes(), "promises2.js");
		jsScriptExecutor.execute script,params;
	}
	
	@Test
	void testJsPromises3() {
		JSScriptExecutor jsScriptExecutor = new JSScriptExecutor();
		def params = [deferred:new JSDeferred(jsScriptExecutor.globalScope),subdeferred:new JSDeferred(jsScriptExecutor.globalScope)]
		String scriptString = IOUtils.toString(TestPromises.class.getResourceAsStream("/be/icode/hot/promise/promises3.js"));
		Script<CompiledScript> script = new Script<>(scriptString.getBytes(), "promises3.js");
		jsScriptExecutor.execute script,params;
	}
}
