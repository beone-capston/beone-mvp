package com.beone.ruledraft.model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * JDK HttpClient 전송. 실제 네트워크를 쓰는 유일한 구현이며 {@code --live}일 때만 쓰인다.
 */
public final class JdkHttpTransport implements HttpTransport {

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

	@Override
	public Result post(URI uri, Map<String, String> headers, String body) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofMinutes(10))
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		headers.forEach(builder::header);
		HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return new Result(response.statusCode(), response.body());
	}

}
