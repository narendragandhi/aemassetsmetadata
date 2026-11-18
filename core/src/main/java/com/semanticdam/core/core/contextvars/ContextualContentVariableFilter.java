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
import java.util.Locale;

/**
 * Opt-in response rewrite for {@code ((prefix.property))} tokens in page HTML and model JSON, the
 * ACS AEM Commons Contextual Content Variables syntax, without ACS AEM Commons.
 *
 * <p>Only GET requests below the configured paths are buffered; every other request streams
 * through untouched, and so does the page editor (edit and design mode), so that authors keep
 * seeing and editing the tokens. A buffered response that is not HTML or model JSON is written back
 * byte for byte. Values are HTML-escaped in HTML and JSON-escaped in JSON.</p>
 */
@Component(service = Filter.class, configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST,
                "service.ranking:Integer=-2500"})
@Designate(ocd = ContextualContentVariableConfig.class)
public final class ContextualContentVariableFilter implements Filter {
    @Reference
    private ContextualContentVariableServiceComponent variableService;
    private volatile ContextualContentVariableConfig config;

    @org.osgi.service.component.annotations.Activate
    @org.osgi.service.component.annotations.Modified
    void activate(ContextualContentVariableConfig config) { this.config = config; }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ContextualContentVariableConfig current = config;
        if (!(request instanceof SlingHttpServletRequest slingRequest)
                || !(response instanceof SlingHttpServletResponse slingResponse)
                || current == null || !current.enabled() || !applies(slingRequest, current)) {
            chain.doFilter(request, response);
            return;
        }
        BufferingResponse buffered = new BufferingResponse(slingResponse);
        chain.doFilter(request, buffered);
        byte[] original = buffered.bytes();
        String contentType = buffered.getContentType() == null ? "" : buffered.getContentType().toLowerCase(Locale.ROOT);
        String selectors = slingRequest.getRequestPathInfo().getSelectorString();
        boolean json = contentType.contains("json") && current.rewriteModelJson() && selectors != null && selectors.contains("model");
        boolean html = contentType.contains("text/html") && current.rewriteHtml();
        if (!json && !html) {
            write(slingResponse, original);
            return;
        }
        String body = new String(original, StandardCharsets.UTF_8);
        if (!body.contains("((")) {
            write(slingResponse, original);
            return;
        }
        String rewritten = variableService.replace(body, slingRequest.getResource(),
                json ? ContextualContentVariableService.Escaping.JSON : ContextualContentVariableService.Escaping.HTML);
        write(slingResponse, rewritten.getBytes(StandardCharsets.UTF_8));
    }

    static boolean applies(SlingHttpServletRequest request, ContextualContentVariableConfig config) {
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        // In the page editor the tokens must stay visible: inline editing would otherwise save the
        // substituted value in place of the token.
        com.day.cq.wcm.api.WCMMode mode = com.day.cq.wcm.api.WCMMode.fromRequest(request);
        if (mode == com.day.cq.wcm.api.WCMMode.EDIT || mode == com.day.cq.wcm.api.WCMMode.DESIGN) {
            return false;
        }
        String path = request.getRequestPathInfo().getResourcePath();
        if (path == null || path.startsWith("/content/dam/")) {
            return false;
        }
        for (String prefix : config.paths()) {
            if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void write(SlingHttpServletResponse response, byte[] body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static final class BufferingResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private PrintWriter writer;
        private ServletOutputStream outputStream;

        BufferingResponse(HttpServletResponse response) { super(response); }

        byte[] bytes() {
            if (writer != null) {
                writer.flush();
            }
            return bytes.toByteArray();
        }

        @Override public void setContentLength(int length) { }
        @Override public void setContentLengthLong(long length) { }
        @Override public void flushBuffer() { if (writer != null) writer.flush(); }

        @Override public ServletOutputStream getOutputStream() {
            if (outputStream == null) outputStream = new ServletOutputStream() {
                @Override public void write(int value) { bytes.write(value); }
                @Override public void write(byte[] buffer, int offset, int length) { bytes.write(buffer, offset, length); }
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
