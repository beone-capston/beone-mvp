package com.beone.ruledraft.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * PDF 또는 쪽 표시 텍스트를 읽어 {@link SourceDocument}로 만든다.
 *
 * <p>텍스트 형식: 각 쪽 앞에 {@code [[page N]]} 줄을 둔다. 첫 표시 앞의 내용은 허용하지 않는다.
 */
public final class SourceLoader {

	static final String PAGE_MARKER_PREFIX = "[[page ";
	static final String PAGE_MARKER_SUFFIX = "]]";
	private static final Pattern MARKER = Pattern.compile("^\\[\\[page (\\d+)]]\\s*$");

	private SourceLoader() {
	}

	public static SourceDocument load(Path path) throws IOException {
		byte[] bytes = Files.readAllBytes(path);
		String name = path.getFileName().toString();
		String sha = sha256(bytes);
		if (name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
			return new SourceDocument(name, sha, readPdf(bytes));
		}
		return new SourceDocument(name, sha, parseMarkedText(new String(bytes, StandardCharsets.UTF_8)));
	}

	/** PDF를 쪽마다 따로 추출한다. 쪽 번호가 근거 인용의 기준이 된다. */
	static List<SourcePage> readPdf(byte[] bytes) throws IOException {
		List<SourcePage> pages = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(bytes)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			int count = doc.getNumberOfPages();
			for (int i = 1; i <= count; i++) {
				stripper.setStartPage(i);
				stripper.setEndPage(i);
				pages.add(new SourcePage(i, stripper.getText(doc)));
			}
		}
		return pages;
	}

	static List<SourcePage> parseMarkedText(String text) {
		List<SourcePage> pages = new ArrayList<>();
		Integer current = null;
		StringBuilder body = new StringBuilder();
		for (String line : text.split("\\R", -1)) {
			Matcher m = MARKER.matcher(line);
			if (m.matches()) {
				if (current != null) {
					pages.add(new SourcePage(current, body.toString()));
				}
				int next = Integer.parseInt(m.group(1));
				int expected = pages.size() + 1;
				if (next != expected) {
					throw new IllegalArgumentException("쪽 표시 순서가 맞지 않는다: " + expected + "쪽 자리에 " + next + "쪽");
				}
				current = next;
				body.setLength(0);
			}
			else if (current == null) {
				if (!line.isBlank()) {
					throw new IllegalArgumentException("첫 [[page 1]] 표시 앞에 내용이 있다");
				}
			}
			else {
				body.append(line).append('\n');
			}
		}
		if (current != null) {
			pages.add(new SourcePage(current, body.toString()));
		}
		if (pages.isEmpty()) {
			throw new IllegalArgumentException("[[page N]] 표시가 없다");
		}
		return pages;
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
