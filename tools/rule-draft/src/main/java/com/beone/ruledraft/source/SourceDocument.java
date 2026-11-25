package com.beone.ruledraft.source;

import java.util.List;
import java.util.Optional;

/**
 * 쪽 단위로 나눈 원문. 근거 인용은 쪽 번호와 이 텍스트로 확인한다.
 *
 * @param fileName 입력 파일 이름(경로 제외)
 * @param sha256 입력 파일 바이트의 sha256(소문자 16진수)
 * @param pages 쪽 목록(1쪽부터 순서대로)
 */
public record SourceDocument(String fileName, String sha256, List<SourcePage> pages) {

	public SourceDocument {
		pages = List.copyOf(pages);
		if (pages.isEmpty()) {
			throw new IllegalArgumentException("원문에 쪽이 없다");
		}
	}

	public Optional<SourcePage> page(int number) {
		return pages.stream().filter(p -> p.number() == number).findFirst();
	}

	/** 쪽 표시를 넣은 텍스트. {@link SourceLoader}가 다시 읽을 수 있는 형식이다. */
	public String toMarkedText() {
		StringBuilder sb = new StringBuilder();
		for (SourcePage page : pages) {
			sb.append(SourceLoader.PAGE_MARKER_PREFIX).append(page.number()).append(SourceLoader.PAGE_MARKER_SUFFIX).append('\n');
			for (String line : page.text().strip().split("\\R", -1)) {
				sb.append(SourceLoader.escapeLine(line)).append('\n');
			}
			sb.append('\n');
		}
		return sb.toString();
	}

}
