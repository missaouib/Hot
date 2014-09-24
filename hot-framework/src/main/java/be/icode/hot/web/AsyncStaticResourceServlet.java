package be.icode.hot.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdeferred.Deferred;
import org.jdeferred.DoneCallback;
import org.jdeferred.DonePipe;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.support.WebApplicationContextUtils;

import be.icode.hot.Script;
import be.icode.hot.js.transpilers.CoffeeScriptCompiler;
import be.icode.hot.js.transpilers.JsTranspiler;
import be.icode.hot.js.transpilers.LessCompiler;
import be.icode.hot.spring.config.HotConfig;
import be.icode.hot.utils.FileLoader;
import be.icode.hot.utils.FileLoader.Buffer;

import com.gs.collections.impl.map.mutable.ConcurrentHashMap;
import com.sun.nio.zipfs.ZipFileSystem;

public class AsyncStaticResourceServlet extends HttpServlet {

	private static final long serialVersionUID = 3391406628540589609L;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AsyncStaticResourceServlet.class);

	private ExecutorService eventLoop;
	
	private ExecutorService blockingThreadPool;
	
	private FileLoader fileLoader;
	
	LessCompiler lessCompiler;
	
	CoffeeScriptCompiler coffeeScriptCompiler;
	
	private HotConfig hotConfig;
	
	Map<URI, FileSystem> jarFileSystemCache = new HashMap<>();
	
	Map<String, byte[]> transpiledScriptCache = new ConcurrentHashMap<>();
	
	@Override
	protected synchronized void doGet(HttpServletRequest servletRequest, HttpServletResponse resp) throws ServletException, IOException {
		
		AsyncContext async = servletRequest.startAsync();
		
		if (eventLoop == null) {
			try {
				ApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(this.getServletContext());
				eventLoop = (ExecutorService) applicationContext.getBean("staticResourcesEventLoop");
				fileLoader = applicationContext.getBean(FileLoader.class);
				hotConfig = applicationContext.getBean(HotConfig.class);
				lessCompiler = applicationContext.getBean(LessCompiler.class);
				coffeeScriptCompiler = applicationContext.getBean(CoffeeScriptCompiler.class);
				blockingThreadPool = (ExecutorService) applicationContext.getBean("blockingTasksThreadPool");
			} catch (BeansException e) {
				LOGGER.error("",e);
			}
		}
		
		try {
			String requestPath = servletRequest.getRequestURL().toString().toLowerCase();
			if (requestPath.endsWith(".js") || requestPath.endsWith(".js.map")) {
				asyncLoadResource(servletRequest, resp, "text/javascript", async);
			} else if (requestPath.endsWith(".html") || servletRequest.getPathInfo().equals("/")) {
				asyncLoadResource(servletRequest, resp, "text/html; charset=utf-8", async);
			} else if (requestPath.endsWith(".css") || requestPath.endsWith(".css.map")) {
				asyncLoadResource(servletRequest, resp, "text/css; charset=utf-8", async);
			} else if (requestPath.endsWith(".png")) {
				asyncLoadResource(servletRequest, resp, "image/png", async);
			} else if (requestPath.endsWith(".jpg") || requestPath.endsWith(".jpeg")) {
				asyncLoadResource(servletRequest, resp, "image/jpg", async);
			} else if (requestPath.endsWith(".woff")) {
				asyncLoadResource(servletRequest, resp, "application/font-woff", async);
			} else if (requestPath.endsWith(".otf")) {
				asyncLoadResource(servletRequest, resp, "font/opentype", async);
			} else if (requestPath.endsWith(".ttf")) {
				asyncLoadResource(servletRequest, resp, "application/x-font-ttf", async);
			} else if (requestPath.endsWith(".eot")) {
				asyncLoadResource(servletRequest, resp, "application/vnd.ms-fontobject", async);
			} else if (requestPath.endsWith(".svg")) {
				asyncLoadResource(servletRequest, resp, "image/svg+xml", async);
			} else if (requestPath.endsWith(".swf")) {
				asyncLoadResource(servletRequest, resp, "application/x-shockwave-flash", async);
			} else if (requestPath.endsWith(".appcache")) {
				asyncLoadResource(servletRequest, resp, "application/x-shockwave-flash", async);
			} else if (requestPath.endsWith(".less")) {
				asyncLoadTranspiledScriptResource(servletRequest, resp, "text/css; charset=utf-8", lessCompiler, async);
			} else if (requestPath.endsWith(".coffee")) {
				asyncLoadTranspiledScriptResource(servletRequest, resp, "text/javascript", coffeeScriptCompiler, async);
			} else {
				resp.setStatus(HttpStatus.NOT_FOUND.value());
				writeBytesToResponse(resp,(requestPath+ " not found").getBytes());
				async.complete();
			}
		} catch (URISyntaxException e) {
			LOGGER.error("",e);
		}
	}
	
	protected void asyncLoadResource (
			final HttpServletRequest servletRequest,
			final HttpServletResponse servletResponse,
			final String contentType,
			final AsyncContext async) throws IOException, URISyntaxException {
		
		URL resourceUrl;
		if (servletRequest.getPathInfo().equals("/")) {
			resourceUrl = getClass().getResource("/index.html");
		} else {
			resourceUrl = getClass().getResource(servletRequest.getPathInfo());
		}
		
		if (resourceUrl == null) {
			servletResponse.setStatus(HttpStatus.NOT_FOUND.value());
			async.complete();
			return;
		}
		final URI uri = resourceUrl.toURI();
		
		eventLoop.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Path path = getPath(uri);
					if (path.getFileSystem() instanceof ZipFileSystem) {
						try {
							byte[] bytes = Files.readAllBytes(path);
							servletResponse.setStatus(HttpStatus.OK.value());
							servletResponse.setHeader(com.google.common.net.HttpHeaders.CONTENT_TYPE, contentType);
							servletResponse.getOutputStream().write(bytes);
							
						} catch (Exception e) {
							servletResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
							servletResponse.getOutputStream().write(extractStackTrace(e).getBytes());
						}
						async.complete();
					} else {
						Promise<Void, Exception, Buffer> promise = fileLoader.loadResourceAsync(path,!hotConfig.isDevMode());
						final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
						
						promise.progress(new ProgressCallback<FileLoader.Buffer>() {
							public void onProgress(Buffer progress) {
								outputStream.write(progress.getContent(), 0, progress.getLength());
							}
						}).done(new DoneCallback<Void>() {
							public void onDone(Void result) {
								servletResponse.setStatus(HttpStatus.OK.value());
								servletResponse.setHeader(com.google.common.net.HttpHeaders.CONTENT_TYPE, contentType);
								writeBytesToResponse(servletResponse, outputStream.toByteArray());
								async.complete();
							}
						}).fail(new FailCallback<Exception>() {
							public void onFail(Exception e) {
								servletResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
								writeBytesToResponse(servletResponse, extractStackTrace(e).getBytes());
								async.complete();
							}
						});
					}
				} catch (Exception e) {
					servletResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
					writeBytesToResponse(servletResponse, extractStackTrace(e).getBytes());
					async.complete();
				}
			}
		});
	}
	
	private void asyncLoadTranspiledScriptResource (
			final HttpServletRequest servletRequest,
			final HttpServletResponse servletResponse,
			final String contentType,
			final JsTranspiler jsTranspiler,
			final AsyncContext async) throws IOException {
		
		
		final String scriptName = servletRequest.getPathInfo();
		
		if (!hotConfig.isDevMode() && transpiledScriptCache.keySet().contains(scriptName)) {
			servletResponse.setStatus(HttpStatus.OK.value());
			servletResponse.setHeader(com.google.common.net.HttpHeaders.CONTENT_TYPE, contentType);
			writeBytesToResponse(servletResponse, transpiledScriptCache.get(scriptName));
		}
		
		eventLoop.execute(new Runnable() {
			
			@Override
			public void run() {
				try {
					Path path = Paths.get(getClass().getResource(servletRequest.getPathInfo()).toURI());
					final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					Promise<Void, Exception, Buffer> promise = fileLoader.loadResourceAsync(path,false);
					
					promise.progress(new ProgressCallback<FileLoader.Buffer>() {
						public void onProgress(final Buffer progress) {
							outputStream.write(progress.getContent(), 0, progress.getLength());
						}
					}).fail(new FailCallback<Exception>() {
						public void onFail(Exception e) {
							servletResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
							writeBytesToResponse(servletResponse, extractStackTrace(e).getBytes());
							async.complete();
						}
					}).then(new DonePipe<Void, byte[], Exception, Void>() {
	
						@Override
						public Promise<byte[], Exception, Void> pipeDone(Void voidd) {
							final Deferred<byte[], Exception, Void> deferred = new DeferredObject<>();
							final byte[] fileBytes = outputStream.toByteArray();
							
							blockingThreadPool.execute(new Runnable() {
								@Override
								public void run() {
									try {
										Thread.currentThread().setContextClassLoader(blockingThreadPool.getClass().getClassLoader());
										Script<String> script = new Script<>(fileBytes, scriptName);
										final String css = jsTranspiler.compile(script);
										eventLoop.execute(new Runnable() {
											@Override
											public void run() {
												System.out.println(css);
												deferred.resolve(css.getBytes());
											}
										});
									} catch (Exception e) {
										deferred.reject(e);
									}
								}
							});
							return deferred.promise();
						}
					}).done(new DoneCallback<byte[]>() {
						@Override
						public void onDone(byte[] result) {
							transpiledScriptCache.put(scriptName, result);
							servletResponse.setStatus(HttpStatus.OK.value());
							servletResponse.setHeader(com.google.common.net.HttpHeaders.CONTENT_TYPE, contentType);
							writeBytesToResponse(servletResponse, result);
							async.complete();
						}
					}).fail(new FailCallback<Exception>() {
						@Override
						public void onFail(Exception e) {
							servletResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
							writeBytesToResponse(servletResponse, extractStackTrace(e).getBytes());
							async.complete();
						}
					});
					
				} catch (Exception e) {
					servletResponse.setStatus(HttpStatus.NOT_FOUND.value());
					writeBytesToResponse(servletResponse, extractStackTrace(e).getBytes());
					async.complete();
				}
			}
		});
	}
	
	private void writeBytesToResponse(HttpServletResponse httpServletResponse, byte[] bytes) {
		try {
			httpServletResponse.getOutputStream().write(bytes);
		} catch (IOException e) {
			LOGGER.error("",e);
		}
	}
	
	protected String extractStackTrace (Exception e) {
		StringWriter stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		e.printStackTrace(printWriter);
		printWriter.flush();
		return stringWriter.toString();
	}
	
	private Path getPath(URI uri) throws IOException {
		if (uri.toString().startsWith("jar:") && !jarFileSystemCache.keySet().contains(uri)) {
			LOGGER.warn("accessing content of jar file => blocking IO needed");
			final String[] tokens = uri.toString().split("!");
			final FileSystem fs = FileSystems.newFileSystem(URI.create(tokens[0]), new HashMap<String,Object>());
			jarFileSystemCache.put(uri, fs);
			return fs.getPath(tokens[1]);
		}
		return Paths.get(uri);
	}
}
