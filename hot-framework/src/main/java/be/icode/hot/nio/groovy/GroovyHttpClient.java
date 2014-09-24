package be.icode.hot.nio.groovy;

import groovy.lang.Closure;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.springframework.core.convert.support.DefaultConversionService;

import be.icode.hot.nio.HttpClient;

public class GroovyHttpClient extends HttpClient<Closure<?>,Map<String, Object>> {

	

	public GroovyHttpClient(ExecutorService eventLoopPool, DefaultConversionService defaultConversionService) {
		super(eventLoopPool, defaultConversionService);
	}

	public GroovyHttpClient(ExecutorService bossExecutorService, ExecutorService eventLoopPool, DefaultConversionService defaultConversionService) {
		super(bossExecutorService, eventLoopPool, defaultConversionService);
	}

	@Override
	protected Request buildRequest(Map<String, Object> options, Closure<?> requestClosure) {
		return new GroovyRequest(options, requestClosure);
	}

	@Override
	protected Response<Closure<?>,Map<String, Object>> buildResponse() {
		return new GroovyResponse();
	}
	
	protected class GroovyRequest extends Request {

		public GroovyRequest(Map<String, Object> options, Closure<?> requestClosure) {
			super(options, requestClosure);
		}

		@Override
		protected RequestClosure<Closure<?>,Map<String, Object>> buildRequestClosure(Closure<?> requestClosure) {
			return new GroovyRequestClosure(requestClosure);
		}

		@Override
		protected RequestErrorClosure<Closure<?>> buildRequestErrorClosure(Closure<?> requestErrorClosure) {
			return new GroovyRequestErrorClosure(requestErrorClosure);
		}

		@Override
		protected RequestCanceledClosure<Closure<?>> buildRequestCanceledClosure(Closure<?> requestCanceledClosure) {
			return new GroovyRequestCanceledClosure(requestCanceledClosure);
		}
	}
	
	static class GroovyRequestClosure implements RequestClosure<Closure<?>,Map<String, Object>> {

		Closure<?> requestClosure;
		
		public GroovyRequestClosure(Closure<?> requestClosure) {
			this.requestClosure = requestClosure;
		}

		@Override
		public void call(Response<Closure<?>,Map<String, Object>> response) {
			requestClosure.call(response);
		}
	}
	
	static class GroovyRequestErrorClosure implements RequestErrorClosure<Closure<?>> {
		
		Closure<?> requestErrorClosure;
		
		public GroovyRequestErrorClosure(Closure<?> requestErrorClosure) {
			this.requestErrorClosure = requestErrorClosure;
		}

		@Override
		public void call(String message) {
			requestErrorClosure.call(message);
		}
	}
	
	static class GroovyRequestCanceledClosure implements RequestCanceledClosure<Closure<?>> {

		Closure<?> requestCanceledClosure;
		
		public GroovyRequestCanceledClosure(Closure<?> requestCanceledClosure) {
			this.requestCanceledClosure = requestCanceledClosure;
		}

		@Override
		public void call(String message) {
			requestCanceledClosure.call(message);
		}
	}
	
	static class GroovyResponseClosure implements ResponseClosure<Closure<?>> {

		Closure<?> responseClosure;
		
		public GroovyResponseClosure(Closure<?> responseClosure) {
			this.responseClosure = responseClosure;
		}

		@Override
		public void call(String chunck) {
			responseClosure.call(chunck);
		}
	}
	
	static class GroovyResponseEndClosure implements ResponseEndClosure<Closure<?>> {

		Closure<?> closure;
		
		public GroovyResponseEndClosure(Closure<?> closure) {
			this.closure = closure;
		}

		@Override
		public void call() {
			closure.call();
		}
	}
	
	static class GroovyResponse extends Response<Closure<?>,Map<String, Object>> {
		
		@Override
		public Map<String, Object> getHeaders() {
			return headers;
		}
		
		@Override
		protected ResponseClosure<Closure<?>> buildResponseClosure(Closure<?> closure) {
			return new GroovyResponseClosure(closure);
		}

		@Override
		protected ResponseEndClosure<Closure<?>> buildResponseEndClosure(Closure<?> closure) {
			return new GroovyResponseEndClosure(closure);
		}
	}
}
