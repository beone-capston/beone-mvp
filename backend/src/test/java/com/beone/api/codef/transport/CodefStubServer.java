package com.beone.api.codef.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 합성 응답을 돌려주는 로컬 서버. 외부를 호출하지 않고 받은 요청을 기록한다.
 */
final class CodefStubServer implements AutoCloseable {

	static final String TOKEN_PATH = "/oauth/token";

	private final HttpServer server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private final Map<String, Responder> responders = new ConcurrentHashMap<>();

	private final CountDownLatch release = new CountDownLatch(1);

	CodefStubServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(executor);
		server.createContext("/", this::handle);
		server.start();
	}

	void respond(String path, int status, String body) {
		responders.put(path, exchange -> reply(exchange, status, body));
	}

	void respondWith(String path, Responder responder) {
		responders.put(path, responder);
	}

	/** 응답하지 않고 기다린다. 시간 초과 시험용이다. */
	void neverRespond(String path) {
		responders.put(path, exchange -> awaitRelease());
	}

	/** 헤더와 본문 일부만 보내고 기다린다. 시간 초과가 본문까지 적용되는지 시험한다. */
	void stallBody(String path) {
		responders.put(path, exchange -> {
			exchange.sendResponseHeaders(200, 0);
			exchange.getResponseBody().write('{');
			exchange.getResponseBody().flush();
			awaitRelease();
		});
	}

	/** 요청을 읽은 뒤 응답 없이 연결을 끊는다. */
	void dropConnection(String path) {
		responders.put(path, HttpExchange::close);
	}

	List<Received> requests() {
		return List.copyOf(received);
	}

	List<Received> requestsTo(String path) {
		List<Received> matching = new ArrayList<>();
		for (Received request : received) {
			if (request.path().equals(path)) {
				matching.add(request);
			}
		}
		return matching;
	}

	CodefTransportSettings settings(Duration responseTimeout) {
		URI host = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
		return new CodefTransportSettings(host.resolve(TOKEN_PATH), host, Duration.ofSeconds(2), responseTimeout, responseTimeout);
	}

	@Override
	public void close() {
		release.countDown();
		server.stop(0);
		executor.shutdownNow();
	}

	static void reply(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}

	private void handle(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		URI uri = exchange.getRequestURI();
		received.add(new Received(exchange.getRequestMethod(), uri.getPath(), uri.getQuery(), exchange.getRequestHeaders(), body));
		Responder responder = responders.get(uri.getPath());
		if (responder == null) {
			reply(exchange, 404, "");
			return;
		}
		responder.respond(exchange);
	}

	private void awaitRelease() {
		try {
			release.await();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	interface Responder {

		void respond(HttpExchange exchange) throws IOException;

	}

	record Received(String method, String path, String query, Headers headers, String body) {

		String header(String name) {
			return headers.getFirst(name);
		}

	}

}
