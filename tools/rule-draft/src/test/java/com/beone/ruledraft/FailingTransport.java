package com.beone.ruledraft;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.beone.ruledraft.model.HttpTransport;

/** 호출되면 실패하는 가짜 전송. 테스트의 실호출 0회를 보장한다. */
public final class FailingTransport implements HttpTransport {

	public final AtomicInteger created = new AtomicInteger();
	public final AtomicInteger calls = new AtomicInteger();

	@Override
	public Result post(URI uri, Map<String, String> headers, String body) {
		calls.incrementAndGet();
		throw new AssertionError("테스트에서 네트워크 전송이 호출되었다");
	}

	/** Main에 넘길 공급자. 전송 객체가 만들어진 횟수도 센다. */
	public Supplier<HttpTransport> supplier() {
		return () -> {
			created.incrementAndGet();
			return this;
		};
	}

}
