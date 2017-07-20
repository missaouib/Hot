package be.solidx.hot.shows.rest;

import be.solidx.hot.groovy.GroovyClosure;
import be.solidx.hot.groovy.GroovyMapConverter;
import be.solidx.hot.js.JSClosure;
import be.solidx.hot.js.JsMapConverter;
import be.solidx.hot.nio.http.HttpDataSerializer;
import be.solidx.hot.promises.Promise;
import be.solidx.hot.python.PyDictionaryConverter;
import be.solidx.hot.python.PythonClosure;
import be.solidx.hot.shows.ClosureRequestMapping;
import be.solidx.hot.shows.RestRequest;
import be.solidx.hot.shows.groovy.GroovyRestRequest;
import be.solidx.hot.shows.javascript.JSRestRequest;
import be.solidx.hot.shows.python.PythonRestRequest;
import be.solidx.hot.shows.spring.ClosureRequestMappingHandlerMapping;
import be.solidx.hot.utils.GroovyHttpDataDeserializer;
import be.solidx.hot.utils.IOUtils;
import be.solidx.hot.utils.JsHttpDataDeserializer;
import be.solidx.hot.utils.PythonHttpDataDeserializer;
import hot.Response;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.python.core.PyDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SaveContextOnUpdateOrErrorResponseWrapper;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Created by dsolimando on 17/07/2017.
 */
public class RestClosureDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClosureServlet.class);

    private static final long serialVersionUID = 1534320093731293327L;

    protected static final String DEFAULT_CHARSET = "utf-8";
    protected static final String DEFAULT_ACCEPT = MediaType.TEXT_PLAIN.toString();

    ClosureRequestMappingHandlerMapping closureRequestMappingHandlerMapping;

    HttpDataSerializer httpDataSerializer;

    GroovyMapConverter groovyDataConverter;

    PyDictionaryConverter pyDictionaryConverter;

    JsMapConverter jsDataConverter;

    GroovyHttpDataDeserializer groovyHttpDataDeserializer;

    PythonHttpDataDeserializer pythonHttpDataDeserializer;

    JsHttpDataDeserializer jsHttpDataDeserializer;

    ExecutorService blockingTreadPool;

    ExecutorService						httpIOEventLoop;

    public RestClosureDelegate(ClosureRequestMappingHandlerMapping closureRequestMappingHandlerMapping, HttpDataSerializer httpDataSerializer, GroovyMapConverter groovyDataConverter, PyDictionaryConverter pyDictionaryConverter, JsMapConverter jsDataConverter, GroovyHttpDataDeserializer groovyHttpDataDeserializer, PythonHttpDataDeserializer pythonHttpDataDeserializer, JsHttpDataDeserializer jsHttpDataDeserializer, ExecutorService blockingTreadPool, ExecutorService httpIOEventLoop) {
        this.closureRequestMappingHandlerMapping = closureRequestMappingHandlerMapping;
        this.httpDataSerializer = httpDataSerializer;
        this.groovyDataConverter = groovyDataConverter;
        this.pyDictionaryConverter = pyDictionaryConverter;
        this.jsDataConverter = jsDataConverter;
        this.groovyHttpDataDeserializer = groovyHttpDataDeserializer;
        this.pythonHttpDataDeserializer = pythonHttpDataDeserializer;
        this.jsHttpDataDeserializer = jsHttpDataDeserializer;
        this.blockingTreadPool = blockingTreadPool;
        this.httpIOEventLoop = httpIOEventLoop;
    }

    public void asyncHandleRestRequest (final HttpServletRequest req, final HttpServletResponse resp, final AsyncContext async) {

        try {

            final ClosureRequestMapping closureRequestMapping;

            // Check if requestMapping is in thread local
            if (HotContext.getRequestMapping() == null) {
                closureRequestMapping = closureRequestMappingHandlerMapping.lookupRequestMapping(req);
            } else {
                closureRequestMapping = HotContext.getRequestMapping();
            }

            final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (closureRequestMapping != null) {

                final ExecutorService showEventLoop = closureRequestMapping.getEventLoop();
                // Async execution
                final long tr = System.currentTimeMillis();
                IOUtils.asyncRead(req, showEventLoop, showEventLoop)
                        .fail(new FailCallback<Exception>() {
                            @Override
                            public void onFail(Exception exception) {
                                if (LOGGER.isDebugEnabled())
                                    LOGGER.debug("",exception);
                                resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                                writeBytesToResponseAsync(resp, extractStackTrace(exception).getBytes(), async);
                            }
                        })
                        .done(new DoneCallback<byte[]>() {
                            @SuppressWarnings({ "rawtypes" })
                            @Override
                            public void onDone(byte[] body) {
                                try {
                                    //System.out.println("Reading request body "+(System.currentTimeMillis()-tr));
                                    final MediaType finalMediaType = extractMediaType(req);
                                    long t = System.currentTimeMillis();
                                    Object response = handleRequest(closureRequestMapping, req, body, authentication);
                                    //System.out.println("closure call time "+ (System.currentTimeMillis()-t));
                                    if (LOGGER.isDebugEnabled())
                                        LOGGER.debug("Response type: "+response.getClass());

                                    t = System.currentTimeMillis();
                                    if (response instanceof NativeJavaObject) {
                                        response = ((NativeJavaObject) response).unwrap();
                                    }
                                    if (response instanceof Promise) {
                                        Promise promise = (Promise) response;
                                        promise._done(new Promise.DCallback() {
                                            @Override
                                            public void onDone(Object result) {
                                                handleResponse(result, finalMediaType , resp, async, showEventLoop);
                                            }
                                        })._fail(new Promise.FCallback() {
                                            @Override
                                            public void onFail(Object object) {
                                                handleError(object,finalMediaType,resp,async,showEventLoop);
                                            }
                                        });
                                    } else {
                                        handleResponse(response, finalMediaType, resp, async, showEventLoop);
                                        //System.out.println("Handle response time "+ (System.currentTimeMillis()-t));
                                    }
                                } catch (Exception e) {
                                    if (LOGGER.isDebugEnabled())
                                        LOGGER.debug("",e);
                                    resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                                    writeBytesToResponseAsync(resp, extractStackTrace(e).getBytes(), async);
                                }
                            }
                        });

            } else {
                resp.setStatus(HttpStatus.NOT_FOUND.value());
                writeBytesToResponseAsync(resp, "No closure matching the request".getBytes(), async);
            }
        } catch (Exception e) {
            resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            writeBytesToResponseAsync(resp, extractStackTrace(e).getBytes(), async);
        }
    }

    private MediaType extractMediaType (HttpServletRequest req) {
        // Parse accept media types header
        String acceptMediaTypeAsString = req.getHeader(com.google.common.net.HttpHeaders.ACCEPT);

        // Parse accept encoding header
        Enumeration<String> acceptEncodings = req.getHeaders(com.google.common.net.HttpHeaders.ACCEPT_ENCODING);
        String charsetAsString = null;

        if (acceptMediaTypeAsString == null) {
            acceptMediaTypeAsString = DEFAULT_ACCEPT;
        }
        List<MediaType> acceptMediaTypes = MediaType.parseMediaTypes(acceptMediaTypeAsString);
        MediaType selectedMediatype = acceptMediaTypes.get(0);

        if (acceptEncodings.hasMoreElements()) {
            charsetAsString = acceptEncodings.nextElement();
            try {
                if (!Charset.isSupported(charsetAsString)) {
                    charsetAsString = DEFAULT_CHARSET;
                }
            } catch (IllegalCharsetNameException e) {
                charsetAsString = DEFAULT_CHARSET;
            }
        } else {
            charsetAsString = DEFAULT_CHARSET;
        }
        // We construct the final mediatype with encoding from request
        return new MediaType(selectedMediatype.getType(), selectedMediatype.getSubtype(), Charset.forName(charsetAsString));
    }

    private void handleError (final Object error, final MediaType finalMediaType, final HttpServletResponse resp, final AsyncContext async, final ExecutorService showEventLoop) {
        LOGGER.debug("Exception type "+error.getClass());
        Exception exception = null;
        Object o = null;
        try {
            // If multiple values in fail callback, we take the first one
            if (error instanceof Object[]) {
                o =  ((Object[])error)[0];
            } else {
                o = error;
            }
            if (o instanceof NativeJavaObject) {
                o = ((NativeJavaObject) o).unwrap();
            }
            if (o instanceof Throwable) {
                exception = new Exception((Throwable) o);
            } else {
                handleResponse(o, finalMediaType, resp, async, showEventLoop);
                return;
            }
        } catch (Exception e) {
            exception = e;
        }
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("",exception);
        resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        writeBytesToResponseAsync(resp, extractStackTrace(exception).getBytes(),async);
    }

    private Object handleRequest(ClosureRequestMapping closureRequestMapping, HttpServletRequest httpServletRequest, byte[] body, Authentication authentication) {

        RestRequest<?> restRequest;

        if (closureRequestMapping.getClosure() instanceof GroovyClosure) {
            restRequest = new GroovyRestRequest(closureRequestMapping.getOptions(), groovyDataConverter, groovyHttpDataDeserializer, httpServletRequest, body, authentication);
        } else if (closureRequestMapping.getClosure() instanceof PythonClosure) {
            restRequest = new PythonRestRequest(closureRequestMapping.getOptions(), pyDictionaryConverter, pythonHttpDataDeserializer, httpServletRequest, body, authentication);
        } else if (closureRequestMapping.getClosure() instanceof JSClosure) {
            restRequest = new JSRestRequest(closureRequestMapping.getOptions(), jsDataConverter, jsHttpDataDeserializer, httpServletRequest, body, authentication);
        } else {
            throw new RuntimeException("showClosure is in the wrong type "+closureRequestMapping.getClosure().getClass());
        }
        if (LOGGER.isDebugEnabled()) LOGGER.debug("Executing closure");
        return closureRequestMapping.getClosure().call(restRequest);
    }

    private void handleResponse (Object objectResponse,
                                 MediaType acceptContentType,
                                 HttpServletResponse resp,
                                 AsyncContext async,
                                 ExecutorService showEventLoop) {

        Object convertedResponse = objectResponse;

        // JS script responses
        if (objectResponse instanceof NativeObject) {
            convertedResponse = jsDataConverter.toMap((NativeObject) objectResponse);
        } else if (objectResponse instanceof NativeArray) {
            convertedResponse = jsDataConverter.toListMap((NativeArray) objectResponse);
        } else if (objectResponse instanceof PyDictionary) {
            convertedResponse = pyDictionaryConverter.toMap((PyDictionary) objectResponse);
        }

        try {
            if (convertedResponse == null)
                writeBytesToResponseAsync(resp, "".getBytes(), async);
            else if (convertedResponse instanceof Response) {
                Response response = (Response) convertedResponse;
                Map<?,?> headers = response.getHeaders();
                Object content = response.getBody();
                MediaType extractedResponseContentType = extractContentType(headers);

                resp.setStatus(response.getStatus());
                resp.setContentType(
                        extractedResponseContentType == null?acceptContentType.toString():extractedResponseContentType.toString());

                for (Map.Entry<?, ?> entry : headers.entrySet()) {
                    resp.setHeader(entry.getKey().toString(), entry.getValue().toString());
                }
                byte[] body = httpDataSerializer.serialize(
                        content,
                        extractedResponseContentType == null?acceptContentType:extractedResponseContentType);

                writeBytesToResponseAsync(resp, body, async);
            } else {
                byte[] body = httpDataSerializer.serialize(convertedResponse, acceptContentType);
                resp.setContentType(acceptContentType.toString());
                writeBytesToResponseAsync(resp, body, async);
            }
        } catch (Exception e) {
            resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            writeBytesToResponseAsync(resp, extractStackTrace(e).getBytes(), async);
        }
    }

    private MediaType extractContentType (Map<?, ?> headers) {
        for (Map.Entry<?, ?> entry : headers.entrySet()) {
            if (entry.getKey().toString().equals(com.google.common.net.HttpHeaders.CONTENT_TYPE)) {
                MediaType mediaType = MediaType.parseMediaType(entry.getValue().toString());
                if (mediaType.getCharSet() == null && !HttpDataSerializer.byteMediaTypes.contains(mediaType.toString())) {
                    return mediaType = MediaType.parseMediaType(entry.getValue().toString()+"; charset="+DEFAULT_CHARSET);
                }
                return mediaType;
            }
        }
        return null;
    }

    protected String extractStackTrace (Exception e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        printWriter.flush();
        return stringWriter.toString();
    }

    private void writeBytesToResponseAsync(HttpServletResponse httpServletResponse, byte[] bytes, final AsyncContext async) {

        try {
            final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            final ServletOutputStream outputStream;

            final long t = System.currentTimeMillis();

            if (httpServletResponse instanceof SaveContextOnUpdateOrErrorResponseWrapper) {
                ((SaveContextOnUpdateOrErrorResponseWrapper) httpServletResponse).getResponse();
                outputStream = ((SaveContextOnUpdateOrErrorResponseWrapper) httpServletResponse).getResponse().getOutputStream();
            } else {
                outputStream = httpServletResponse.getOutputStream();
            }

            outputStream.setWriteListener(new WriteListener() {

                @Override
                public void onWritePossible() throws IOException {
                    try {
                        byte[] buffer = new byte[2048];
                        int len = 0;
                        while (outputStream.isReady() && (len = bais.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, len);
                        }
                        if (len == -1) {
                            //System.out.println("Writing response time "+(System.currentTimeMillis()-t));
                            async.complete();
                        }
                    } catch (IOException e) {
                        LOGGER.error("", e);
                        async.complete();
                    }
                }
                @Override
                public void onError(Throwable t) {
                    LOGGER.error("", t);
                    async.complete();
                }
            });

        } catch (IOException e) {
            LOGGER.error("", e);
            async.complete();
        }
    }
}
