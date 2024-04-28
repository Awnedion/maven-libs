package awn.core.httpclient;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class SuperHttpClient {

	private CloseableHttpClient m_client;

	public SuperHttpClient() {

		int timeoutMillis = 90000;

		RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(timeoutMillis)
				.setConnectTimeout(timeoutMillis).setSocketTimeout(timeoutMillis).build();

		SocketConfig socketConfig = SocketConfig.custom().setSoKeepAlive(true).setTcpNoDelay(true).build();

		PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
		poolingHttpClientConnectionManager.setMaxTotal(10);
		poolingHttpClientConnectionManager.setDefaultMaxPerRoute(10);

		m_client = HttpClientBuilder.create().setConnectionManager(poolingHttpClientConnectionManager)
				.setDefaultRequestConfig(requestConfig).setDefaultSocketConfig(socketConfig).build();
	}

	public String executeRequest(HttpRequestBase request) {
		return executeRequest(request, 4);
	}

	public String executeRequest(HttpRequestBase request, int maxAttempts) {
		return (String) executeRequest(request, maxAttempts, (req) -> {
			ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

				@Override
				public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
					return consumeResponseBody(response);
				}
			};
			try {
				return m_client.execute(req, responseHandler);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	public CloseableHttpResponse executeRequestRaw(HttpRequestBase request, int maxAttempts) {
		return (CloseableHttpResponse) executeRequest(request, maxAttempts, (req) -> {
			try {
				return m_client.execute(req);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private Object executeRequest(HttpRequestBase request, int maxAttempts,
			Function<HttpRequestBase, Object> requestExecutor) {
		int attempts = 0;
		request.addHeader("Accept-Encoding", "gzip");
		request.setHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/109.0");

		while (true) {
			attempts++;

			Object result = null;
			try {
				result = requestExecutor.apply(request);
				return result;
			} catch (Exception e) {
				System.out.println("HC: Ignoring dangerous exception: " + e + " cause: " + e.getCause() + " at "
						+ new Timestamp(System.currentTimeMillis()) + " for uri: " + request.getURI());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			} finally {
				if (!(result instanceof CloseableHttpResponse))
					request.releaseConnection();
			}

			if (attempts >= maxAttempts)
				throw new RuntimeException("Query Max Tries Reached.");
		}
	}

	public String consumeResponseBody(HttpResponse resp) {
		InputStream unZip = wrapResponseStream(resp.getEntity());
		int temp;

		StringBuilder buffer = new StringBuilder(1000);
		try {
			while ((temp = unZip.read()) != -1)
				buffer.append((char) temp);
		} catch (IOException e) {
			System.out.println("Failed to consume response body: " + e);
		}

		return buffer.toString();
	}

	private InputStream wrapResponseStream(HttpEntity response) {
		InputStream result;

		try {
			Header encoding = response.getContentEncoding();
			if (encoding != null && "gzip".equalsIgnoreCase(encoding.getValue())) {
				result = new GZIPInputStream(response.getContent());
			} else {
				result = response.getContent();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

}
