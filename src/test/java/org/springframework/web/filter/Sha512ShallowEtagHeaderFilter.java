package org.springframework.web.filter;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

public class Sha512ShallowEtagHeaderFilter implements Filter {

	@Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);

        chain.doFilter(httpRequest, responseWrapper);

        String responseETag = generateETag(responseWrapper);
        if (responseETag != null) {
            httpResponse.setHeader(HttpHeaders.ETAG, responseETag);
        }

        responseWrapper.copyBodyToResponse();
    }

    private String generateETag(ContentCachingResponseWrapper response) {
        byte[] responseBody = response.getContentAsByteArray();
        if (responseBody.length == 0) {
            return null;
        }
        HashCode hash = Hashing.sha512().hashBytes(responseBody);
		return "\"" + hash + "\"";
	}

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization code, if needed
    }

    @Override
    public void destroy() {
        // Cleanup code, if needed
    }
}
