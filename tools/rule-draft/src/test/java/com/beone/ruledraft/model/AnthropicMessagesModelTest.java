package com.beone.ruledraft.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.beone.ruledraft.FailingTransport;
import com.beone.ruledraft.source.SourceDocument;
import com.beone.ruledraft.source.SourcePage;

/**
 * 실제 제공자 어댑터는 호출하지 않는다. 요청 본문 조립과 응답 해석만 전송 없이 확인한다.
 */
class AnthropicMessagesModelTest {

	private static final String FAKE_KEY = "fake-key-must-not-appear-0123456789";

	@Test
	void 요청_본문에_키가_없고_전송을_쓰지_않는다() {
		FailingTransport transport = new FailingTransport();
		AnthropicMessagesModel model = new AnthropicMessagesModel(transport, FAKE_KEY, AnthropicMessagesModel.DEFAULT_MODEL);
		DraftRequest request = new DraftRequest("99999",
				new SourceDocument("s.txt", "0".repeat(64), List.of(new SourcePage(1, "가"), new SourcePage(2, "나"))), "지시");

		String body = model.requestBody(request);

		assertFalse(body.contains(FAKE_KEY));
		assertTrue(body.contains("\"model\":\"claude-opus-5-5\""));
		assertTrue(body.contains("[[page 2]]\\n나"));
		assertEquals("anthropic:claude-opus-5-5", model.describe());
		assertEquals(0, transport.calls.get());
	}

	@Test
	void 응답_텍스트를_모으고_비정상_종료는_실패한다() throws ModelException {
		AnthropicMessagesModel model = new AnthropicMessagesModel(new FailingTransport(), FAKE_KEY, "m");
		assertEquals("{\"a\":1}", model.extractText("""
				{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\\"a\\":1}"}]}
				"""));
		ModelException e = assertThrows(ModelException.class,
				() -> model.extractText("{\"stop_reason\":\"max_tokens\",\"content\":[]}"));
		assertTrue(e.getMessage().contains("max_tokens"));
		assertThrows(ModelException.class, () -> model.extractText("{\"stop_reason\":\"refusal\",\"content\":[]}"));
	}

	@Test
	void 빈_키는_거부한다() {
		assertThrows(IllegalArgumentException.class, () -> new AnthropicMessagesModel(new FailingTransport(), " ", "m"));
	}

}
