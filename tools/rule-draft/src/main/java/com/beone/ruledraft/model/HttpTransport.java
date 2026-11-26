package com.beone.ruledraft.model;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * HTTP POST 한 번. 테스트는 호출되면 실패하는 구현을 넣어 실호출 0회를 보장한다.
 */
public interface HttpTransport {

	Result post(URI uri, Map<String, String> headers, String body) throws IOException, InterruptedException;

	record Result(int status, String body) {
	}

}
