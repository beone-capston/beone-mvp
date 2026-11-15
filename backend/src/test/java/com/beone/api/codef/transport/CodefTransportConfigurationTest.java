package com.beone.api.codef.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class CodefTransportConfigurationTest {

	@Test
	void missingKeysLogOneLineWithNamesOnlyAndCallsFailWithoutSending() {
		Logger logger = (Logger) LoggerFactory.getLogger(CodefTransportConfiguration.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			CodefClient client = new CodefTransportConfiguration().codefClient(new MockEnvironment(),
					Map.of("CODEF_CLIENT_ID", "synthetic-partial-value"));

			var result = (CodefCallResult.Failed<?>) client.startObservation().heldCards(Map.of());
			assertThat(result.reason()).isEqualTo(CodefCallResult.Reason.CREDENTIALS_MISSING);
		}
		finally {
			logger.detachAppender(appender);
		}
		List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(messages).containsExactly("CODEF 키 설정 안 됨 missing=[CODEF_CLIENT_SECRET]");
	}

	@Test
	void configuredKeysLogNothingAtAssembly() {
		Logger logger = (Logger) LoggerFactory.getLogger(CodefTransportConfiguration.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			new CodefTransportConfiguration().codefClient(new MockEnvironment(),
					Map.of("CODEF_CLIENT_ID", "synthetic-a", "CODEF_CLIENT_SECRET", "synthetic-b"));
		}
		finally {
			logger.detachAppender(appender);
		}
		assertThat(appender.list).isEmpty();
	}

}
