package org.springframework.web.filter;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

/**
 * A custom implementation of ShallowEtagHeaderFilter that uses SHA-512 for ETag generation
 * instead of the default MD5 algorithm.
 */
public class Sha512ShallowEtagHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_ETAG = "ETag";
    private static final String HEADER_IF_NONE_MATCH = "If-None-Match";
    private static final String HEADER_CACHE_CONTROL = "Cache-Control";
    private static final String DIRECTIVE_NO_STORE = "no-store";

    /**
     * The default value is false so that the filter may be used in default configuration.
     */
    private boolean writeWeakETag = false;

    /**
     * Generate the ETag header value from the given response body byte array.
     * <p>This implementation uses SHA-512 instead of MD5.
     * @param body the response body as byte array
     * @return the ETag header value
     */
    protected String generateETag(byte[] body) {
        final HashCode hash = Hashing.sha512().hashBytes(body);
        return (this.writeWeakETag ? "W/" : "") + "\"" + hash + "\"";
    }

    /**
     * Set whether the ETag should be written as a weak ETag.
     * @param writeWeakETag whether to write weak ETags
     */
    public void setWriteWeakETag(boolean writeWeakETag) {
        this.writeWeakETag = writeWeakETag;
    }

    /**
     * Return whether the ETag should be written as a weak ETag.
     */
    public boolean isWriteWeakETag() {
        return this.writeWeakETag;
    }

	@Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        HttpServletResponse responseToUse = response;

        boolean isEligibleForEtag = isEligibleForEtag(request, response);
        if (isEligibleForEtag) {
            responseToUse = new ContentCachingResponseWrapper(response);
        }

        try {
            filterChain.doFilter(request, responseToUse);
        }
        finally {
            if (isEligibleForEtag) {
                updateResponse(request, responseToUse);
            }
        }
    }

    /**
     * This method determines whether a response is eligible for ETag generation.
     * The default implementation returns {@code true} for responses with a status code
     * less than 300, and if the response does not specify a "no-store" Cache-Control directive.
     */
    protected boolean isEligibleForEtag(HttpServletRequest request, HttpServletResponse response) {
        if (response.getStatus() >= 300) {
            return false;
        }
        if (response.getHeader(HEADER_CACHE_CONTROL) != null &&
                response.getHeader(HEADER_CACHE_CONTROL).contains(DIRECTIVE_NO_STORE)) {
            return false;
        }
        return true;
    }

    private void updateResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ContentCachingResponseWrapper responseWrapper =
                WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (responseWrapper == null) {
            throw new IllegalStateException(
                    "ContentCachingResponseWrapper not found in response: " + response);
        }

        byte[] body = responseWrapper.getContentAsByteArray();
        if (body.length > 0) {
            String eTag = generateETag(body);
            response.setHeader(HEADER_ETAG, eTag);

            String ifNoneMatch = request.getHeader(HEADER_IF_NONE_MATCH);
            if (ifNoneMatch != null) {
                if (eTag.equals(ifNoneMatch) || ("*".equals(ifNoneMatch) && body.length > 0)) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setContentLength(0);
                    return;
                }
            }
        }

        // Release the cached response content
        responseWrapper.copyBodyToResponse();
	}
}
