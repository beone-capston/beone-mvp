package com.beone.ruledraft.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 녹화된 응답 텍스트를 그대로 돌려준다. 네트워크를 쓰지 않는다(테스트·재현용).
 */
public final class RecordedModel implements RuleDraftModel {

	private final Path responseFile;

	public RecordedModel(Path responseFile) {
		this.responseFile = responseFile;
	}

	@Override
	public String draft(DraftRequest request) throws ModelException {
		try {
			return Files.readString(responseFile, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new ModelException("녹화 응답을 읽지 못했다: " + responseFile.getFileName(), e);
		}
	}

	@Override
	public String describe() {
		return "recorded:" + responseFile.getFileName();
	}

}
