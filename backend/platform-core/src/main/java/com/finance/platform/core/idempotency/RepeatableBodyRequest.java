package com.finance.platform.core.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Buffers the request body so filters can hash it and controllers can still read JSON.
 */
final class RepeatableBodyRequest extends HttpServletRequestWrapper {

	private final byte[] cachedBody;

	RepeatableBodyRequest(HttpServletRequest request, byte[] cachedBody) {
		super(request);
		this.cachedBody = cachedBody != null ? cachedBody : new byte[0];
	}

	static RepeatableBodyRequest from(HttpServletRequest request) throws IOException {
		long declared = request.getContentLengthLong();
		if (declared > IdempotencyFingerprint.MAX_BODY_BYTES) {
			throw new IdempotencyPayloadTooLargeException();
		}
		return new RepeatableBodyRequest(request, readAtMost(request.getInputStream(), IdempotencyFingerprint.MAX_BODY_BYTES));
	}

	private static byte[] readAtMost(InputStream input, int max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int total = 0;
		int read;
		while ((read = input.read(buffer)) != -1) {
			total += read;
			if (total > max) {
				throw new IdempotencyPayloadTooLargeException();
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	byte[] body() {
		return cachedBody;
	}

	@Override
	public ServletInputStream getInputStream() {
		ByteArrayInputStream input = new ByteArrayInputStream(cachedBody);
		return new ServletInputStream() {
			@Override
			public boolean isFinished() {
				return input.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener readListener) {
				// no-op for in-memory body
			}

			@Override
			public int read() {
				return input.read();
			}
		};
	}

	@Override
	public BufferedReader getReader() {
		Charset charset = getCharacterEncoding() != null
				? Charset.forName(getCharacterEncoding())
				: StandardCharsets.UTF_8;
		return new BufferedReader(new InputStreamReader(getInputStream(), charset));
	}

	@Override
	public int getContentLength() {
		return cachedBody.length;
	}

	@Override
	public long getContentLengthLong() {
		return cachedBody.length;
	}
}
