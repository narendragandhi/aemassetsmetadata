package com.semanticdam.core.core.contextvars;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.engine.EngineConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Opt-in response rewrite for ACS-compatible HTML and model JSON tokens. */
@Component(service = Filter.class, configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST)
@Designate(ocd = ContextualContentVariableConfig.class)
public final class ContextualContentVariableFilter implements Filter {
    @Reference
    private ContextualContentVariableServiceComponent variableService;
    private volatile ContextualContentVariableConfig config;

    @org.osgi.service.component.annotations.Activate
    void activate(ContextualContentVariableConfig config) { this.config = config; }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ContextualContentVariableConfig current = config;
        if (!(request instanceof SlingHttpServletRequest slingRequest)
                || !(response instanceof SlingHttpServletResponse slingResponse)
                || current == null || !current.enabled()) {
            chain.doFilter(request, response);
            return;
        }
        BufferingResponse buffered = new BufferingResponse(slingResponse);
        chain.doFilter(request, buffered);
        String contentType = buffered.getContentType();
        boolean json = contentType != null && contentType.toLowerCase().contains("json")
                && current.rewriteModelJson()
                && slingRequest.getRequestPathInfo().getSelectorString() != null
                && slingRequest.getRequestPathInfo().getSelectorString().contains("model");
        boolean html = contentType != null && contentType.toLowerCase().contains("html") && current.rewriteHtml();
        String body = new String(buffered.bytes.toByteArray(), StandardCharsets.UTF_8);
        String rewritten = (json || html)
                ? variableService.replace(body, slingRequest.getResource(), json) : body;
        slingResponse.setContentLength(rewritten.getBytes(StandardCharsets.UTF_8).length);
        slingResponse.getOutputStream().write(rewritten.getBytes(StandardCharsets.UTF_8));
    }

    private static final class BufferingResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private PrintWriter writer;
        private ServletOutputStream outputStream;
        BufferingResponse(HttpServletResponse response) { super(response); }
        @Override public ServletOutputStream getOutputStream() {
            if (outputStream == null) outputStream = new ServletOutputStream() {
                @Override public void write(int value) { bytes.write(value); }
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener listener) { }
            };
            return outputStream;
        }
        @Override public PrintWriter getWriter() {
            if (writer == null) writer = new PrintWriter(new OutputStreamWriter(bytes, StandardCharsets.UTF_8));
            return writer;
        }
    }
}
