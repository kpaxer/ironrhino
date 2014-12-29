package org.ironrhino.rest.client;

import java.net.URI;

import org.ironrhino.core.util.JsonUtils;
import org.ironrhino.rest.client.token.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;

public class RestTemplate extends org.springframework.web.client.RestTemplate {

	private Logger logger = LoggerFactory.getLogger(getClass());

	private Client client;

	private int retryTimes = 1;

	public RestTemplate(Client client) {
		super();
		this.client = client;
		setRequestFactory(new SimpleClientHttpRequestFactory(client));
		MappingJackson2HttpMessageConverter jackson2 = new MappingJackson2HttpMessageConverter();
		jackson2.setObjectMapper(JsonUtils.createNewObjectMapper());
		getMessageConverters().add(jackson2);
	}

	public int getConnectTimeout() {
		SimpleClientHttpRequestFactory cf = (SimpleClientHttpRequestFactory) getRequestFactory();
		return cf.getConnectTimeout();
	}

	public void setConnectTimeout(int connectTimeout) {
		SimpleClientHttpRequestFactory cf = (SimpleClientHttpRequestFactory) getRequestFactory();
		cf.setConnectTimeout(connectTimeout);
	}

	public int getReadTimeout() {
		SimpleClientHttpRequestFactory cf = (SimpleClientHttpRequestFactory) getRequestFactory();
		return cf.getReadTimeout();
	}

	public void setReadTimeout(int readTimeout) {
		SimpleClientHttpRequestFactory cf = (SimpleClientHttpRequestFactory) getRequestFactory();
		cf.setReadTimeout(readTimeout);
	}

	public int getRetryTimes() {
		return retryTimes;
	}

	public void setRetryTimes(int retryTimes) {
		this.retryTimes = retryTimes;
	}

	@Override
	protected <T> T doExecute(URI url, HttpMethod method,
			RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor) throws RestClientException {
		return doExecute(url, method, requestCallback, responseExtractor,
				retryTimes);
	}

	protected <T> T doExecute(URI url, HttpMethod method,
			RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor, int retryTimes)
			throws RestClientException {
		try {
			T result = super.doExecute(url, method, requestCallback,
					responseExtractor);
			return result;
		} catch (HttpClientErrorException e) {
			logger.error(e.getResponseBodyAsString(), e);
			if (e.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
				String response = e.getResponseBodyAsString().toLowerCase();
				Token token = client.getTokenStore().getToken();
				if (response.contains("invalid_token")) {
					client.getTokenStore().setToken(null);
				} else if (response.contains("expired_token")) {
					if (token != null) {
						token.setExpired(true);
						client.getTokenStore().setToken(token);
					}
				}
				if (retryTimes > 0) {
					return doExecute(url, method, requestCallback,
							responseExtractor, --retryTimes);
				}
			}
			throw e;
		}
	}

}