/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.common.httpapi;

import okhttp3.*;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.platform.Platform;
import okio.Buffer;
import okio.BufferedSource;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LogInterceptor implements Interceptor {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    private final Logger logger;

    public LogInterceptor() {
        this(new Logger() {
            @Override
            public void log(String message) {
                Platform.get().log(Platform.WARN, message, null);
            }
        });
    }

    public LogInterceptor(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        final int id = ID_GENERATOR.incrementAndGet();
        {
            final String LOG_PREFIX = "[" + id + " request]";
            RequestBody requestBody = request.body();
            boolean hasRequestBody = requestBody != null;

            Connection connection = chain.connection();
            Protocol protocol = connection != null ? connection.protocol() : Protocol.HTTP_1_1;
            String requestStartMessage = "--> " + request.method() + ' ' + request.url() + ' ' + protocol;
            logger.log(LOG_PREFIX + requestStartMessage);

            if (hasRequestBody) {
                // Request body headers are only present when installed as a network interceptor. Force
                // them to be included (when available) so there values are known.
                if (requestBody.contentType() != null) {
                    logger.log(LOG_PREFIX + "Content-Type: " + requestBody.contentType());
                }
                if (requestBody.contentLength() != -1) {
                    logger.log(LOG_PREFIX + "Content-Length: " + requestBody.contentLength());
                }
            }

            Headers headers = request.headers();
            for (int i = 0, count = headers.size(); i < count; i++) {
                String name = headers.name(i);
                // Skip headers from the request body as they are explicitly logged above.
                if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                    logger.log(LOG_PREFIX + name + ": " + headers.value(i));
                }
            }

            if (!hasRequestBody) {
                logger.log(LOG_PREFIX + "--> END " + request.method());
            } else if (bodyEncoded(request.headers())) {
                logger.log(LOG_PREFIX + "--> END " + request.method() + " (encoded body omitted)");
            } else {
                Buffer buffer = new Buffer();
                requestBody.writeTo(buffer);

                Charset charset = UTF8;
                MediaType contentType = requestBody.contentType();
                if (contentType != null) {
                    charset = contentType.charset(UTF8);
                }

                if (isPlaintext(buffer)) {
                    final String bufferString = buffer.readString(charset);
                    logger.log(LOG_PREFIX + bufferString);
                    if (contentType != null && "json".equals(contentType.subtype())) {
                        logger.log(LOG_PREFIX + "\n" + JSONFormatter.formatJSON(bufferString));
                    }
                    logger.log(LOG_PREFIX + "--> END " + request.method()
                            + " (" + requestBody.contentLength() + "-byte body)");
                } else {
                    logger.log(LOG_PREFIX + "--> END " + request.method() + " (binary "
                            + requestBody.contentLength() + "-byte body omitted)");
                }
            }
        }

        {
            final String LOG_PREFIX = "[" + id + " response]";
            long startNs = System.nanoTime();
            Response response;
            try {
                response = chain.proceed(request);
            } catch (Exception e) {
                logger.log(LOG_PREFIX + "<-- HTTP FAILED: " + e);
                throw e;
            }
            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

            ResponseBody responseBody = response.body();
            long contentLength = responseBody.contentLength();
            logger.log(LOG_PREFIX + "<-- " + response.code() + ' ' + response.message() + ' ' + response.request().url() + " (" + tookMs + "ms" + "" + ')');

            Headers headers = response.headers();
            for (int i = 0, count = headers.size(); i < count; i++) {
                logger.log(LOG_PREFIX + headers.name(i) + ": " + headers.value(i));
            }

            if (!HttpHeaders.hasBody(response)) {
                logger.log(LOG_PREFIX + "<-- END HTTP");
            } else if (bodyEncoded(response.headers())) {
                logger.log(LOG_PREFIX + "<-- END HTTP (encoded body omitted)");
            } else {
                BufferedSource source = responseBody.source();
                source.request(Long.MAX_VALUE); // Buffer the entire body.
                Buffer buffer = source.buffer();

                Charset charset = UTF8;
                MediaType contentType = responseBody.contentType();
                if (contentType != null) {
                    try {
                        charset = contentType.charset(UTF8);
                    } catch (UnsupportedCharsetException e) {
                        logger.log(LOG_PREFIX + "");
                        logger.log(LOG_PREFIX + "Couldn't decode the response body; charset is likely malformed.");
                        logger.log(LOG_PREFIX + "<-- END HTTP");
                        return response;
                    }
                }

                if (!isPlaintext(buffer)) {
                    logger.log(LOG_PREFIX + "<-- END HTTP (binary " + buffer.size() + "-byte body omitted)");
                    return response;
                }

                if (contentLength != 0) {
                    final String bufferString = buffer.clone().readString(charset);
                    logger.log(LOG_PREFIX + bufferString);
                    if (contentType != null && "json".equals(contentType.subtype())) {
                        logger.log(LOG_PREFIX + "\n" + JSONFormatter.formatJSON(bufferString));
                    }
                }

                logger.log(LOG_PREFIX + "<-- END HTTP (" + buffer.size() + "-byte body)");
            }
            return response;
        }
    }

    static boolean isPlaintext(Buffer buffer) {
        try {
            Buffer prefix = new Buffer();
            long byteCount = buffer.size() < 64 ? buffer.size() : 64;
            buffer.copyTo(prefix, 0, byteCount);
            for (int i = 0; i < 16; i++) {
                if (prefix.exhausted()) {
                    break;
                }
                int codePoint = prefix.readUtf8CodePoint();
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false;
                }
            }
            return true;
        } catch (EOFException e) {
            return false; // Truncated UTF-8 sequence.
        }
    }

    private boolean bodyEncoded(Headers headers) {
        String contentEncoding = headers.get("Content-Encoding");
        return contentEncoding != null && !contentEncoding.equalsIgnoreCase("identity");
    }

    public interface Logger {
        void log(String message);
    }
}
