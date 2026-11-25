package com.beone.ruledraft.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceLoaderTest {

	@TempDir
	Path tmp;

	@Test
	void 쪽_표시_텍스트를_쪽별로_나눈다() {
		List<SourcePage> pages = SourceLoader.parseMarkedText("\n[[page 1]]\n가\n[[page 2]]\n나\n다\n");
		assertEquals(2, pages.size());
		assertEquals("가\n", pages.get(0).text());
		assertEquals("나\n다\n\n", pages.get(1).text());
	}

	@Test
	void 쪽_표시가_어긋나면_실패한다() {
		assertThrows(IllegalArgumentException.class, () -> SourceLoader.parseMarkedText("[[page 2]]\n가"));
		assertThrows(IllegalArgumentException.class, () -> SourceLoader.parseMarkedText("머리말\n[[page 1]]\n가"));
		assertThrows(IllegalArgumentException.class, () -> SourceLoader.parseMarkedText("표시 없음"));
	}

	@Test
	void PDF는_쪽_번호를_유지해_추출한다() throws IOException {
		Path pdf = tmp.resolve("sample.pdf");
		try (PDDocument doc = new PDDocument()) {
			for (String line : List.of("Monthly cap 10000 won", "Cinema 35 percent discount")) {
				PDPage page = new PDPage();
				doc.addPage(page);
				try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
					cs.beginText();
					cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
					cs.newLineAtOffset(72, 700);
					cs.showText(line);
					cs.endText();
				}
			}
			doc.save(pdf.toFile());
		}

		SourceDocument source = SourceLoader.load(pdf);

		assertEquals(2, source.pages().size());
		assertTrue(source.page(1).orElseThrow().text().contains("Monthly cap 10000 won"));
		assertTrue(source.page(2).orElseThrow().text().contains("Cinema 35 percent discount"));
		assertEquals(64, source.sha256().length());
		// text 명령 출력은 다시 읽을 수 있어야 한다
		List<SourcePage> reread = SourceLoader.parseMarkedText(source.toMarkedText());
		assertEquals(2, reread.size());
		assertTrue(reread.get(1).text().contains("Cinema 35 percent discount"));
	}

}
