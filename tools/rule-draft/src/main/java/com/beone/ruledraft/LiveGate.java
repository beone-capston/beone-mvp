package com.beone.ruledraft;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import com.beone.ruledraft.model.AnthropicMessagesModel;
import com.beone.ruledraft.model.HttpTransport;
import com.beone.ruledraft.model.RecordedModel;
import com.beone.ruledraft.model.RuleDraftModel;

/**
 * 모델 선택과 실제 호출 조건. 실제 호출은 {@code --live}와 키 환경 변수가 모두 있을 때만 연다(결정 41).
 *
 * <ul>
 *   <li>{@code --recorded <파일>}: 녹화 응답 재생. {@code --live}와 함께 쓸 수 없다.</li>
 *   <li>{@code --live}: 키 환경 변수 {@value #KEY_ENV}가 비어 있으면 실패한다.</li>
 *   <li>둘 다 없으면 실패한다. 키만 설정되어 있어도 호출하지 않는다.</li>
 * </ul>
 * 오류 메시지에는 환경 변수 이름만 넣고 값은 넣지 않는다.
 */
final class LiveGate {

	static final String KEY_ENV = "BEONE_RULE_DRAFT_API_KEY";
	static final String MODEL_ENV = "BEONE_RULE_DRAFT_MODEL";

	private LiveGate() {
	}

	static RuleDraftModel select(Path recorded, boolean live, Map<String, String> env, Supplier<HttpTransport> transport)
			throws UsageException {
		if (recorded != null && live) {
			throw new UsageException("--recorded와 --live는 함께 쓸 수 없다");
		}
		if (recorded != null) {
			return new RecordedModel(recorded);
		}
		if (!live) {
			throw new UsageException("모델이 지정되지 않았다. 녹화 응답은 --recorded, 실제 호출은 --live와 " + KEY_ENV + "가 모두 필요하다");
		}
		String key = env.get(KEY_ENV);
		if (key == null || key.isBlank()) {
			throw new UsageException("--live에는 환경 변수 " + KEY_ENV + "가 필요하다");
		}
		String model = env.getOrDefault(MODEL_ENV, AnthropicMessagesModel.DEFAULT_MODEL);
		return new AnthropicMessagesModel(transport.get(), key, model);
	}

}
