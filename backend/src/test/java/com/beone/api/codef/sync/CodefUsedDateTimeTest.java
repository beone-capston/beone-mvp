package com.beone.api.codef.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.beone.api.codef.sync.CodefLedgerCandidateRow.Decision;
import com.beone.api.codef.sync.CodefUsedDateTime.ParseState;

/**
 * 사용일자·사용일시 해석(결정 R4). 정확히 8자리 yyyyMMdd이고 실제 있는 날짜, 정확히 6자리 HHmmss이고 유효한 시각일 때만 해석하고 그 밖은 추정하지 않는다.
 * 제안 형식이며 실계정 관측 전이다. 기대값은 결정 C R4 문장에서 구현 전에 정했다.
 */
class CodefUsedDateTimeTest {

	private static CodefUsedDateTime parse(String date, String time) {
		return CodefUsedDateTime.parse(Optional.ofNullable(date), Optional.ofNullable(time));
	}

	@Test
	void leapDayAndLastSecondParse() {
		CodefUsedDateTime parsed = parse("20240229", "235959");

		assertThat(parsed.date()).contains(LocalDate.of(2024, 2, 29));
		assertThat(parsed.time()).contains(LocalTime.of(23, 59, 59));
		assertThat(parsed.complete()).isTrue();
	}

	@Test
	void midnightParses() {
		assertThat(parse("20261015", "000000").time()).contains(LocalTime.MIDNIGHT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "20230229", "20261301", "20261100", "20261131", "2026101", "202610150", "2026-10-15",
			"2026101a", " 20261015", "20261015 ", "+2026101", "２０２６１０１５" })
	void invalidDatesAreUnparsed(String date) {
		CodefUsedDateTime parsed = parse(date, "100000");

		assertThat(parsed.date()).isEmpty();
		assertThat(parsed.dateState()).isEqualTo(ParseState.UNPARSED);
		assertThat(parsed.complete()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "240000", "235960", "236000", "10000", "1000000", "10000a", "10:00:00", " 100000",
			"-10000" })
	void invalidTimesAreUnparsed(String time) {
		CodefUsedDateTime parsed = parse("20261015", time);

		assertThat(parsed.time()).isEmpty();
		assertThat(parsed.timeState()).isEqualTo(ParseState.UNPARSED);
		assertThat(parsed.date()).contains(LocalDate.of(2026, 10, 15));
		assertThat(parsed.complete()).isFalse();
	}

	@Test
	void absentValuesAreAbsentNotGuessed() {
		CodefUsedDateTime parsed = parse(null, null);

		assertThat(parsed.dateState()).isEqualTo(ParseState.ABSENT);
		assertThat(parsed.timeState()).isEqualTo(ParseState.ABSENT);
		assertThat(parsed.date()).isEmpty();
		assertThat(parsed.time()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "\t" })
	void blankValuesAreAbsentNotUnparsed(String blank) {
		CodefUsedDateTime parsed = parse(blank, blank);

		assertThat(parsed.dateState()).isEqualTo(ParseState.ABSENT);
		assertThat(parsed.timeState()).isEqualTo(ParseState.ABSENT);
	}

	@Test
	void rowsWithUnparsedDateOrTimeAreIncomplete() {
		CodefLedgerCandidates candidates = LedgerFixture.build("ledger-approvals-dates.json");
		Decision undetermined = new Decision.Incomplete(CodefIncompleteReason.UNDETERMINED_ROW);

		assertThat(candidates.row(0).decision()).isInstanceOf(Decision.Accepted.class);
		assertThat(candidates.row(11).decision()).isInstanceOf(Decision.Accepted.class);
		for (int position = 1; position <= 9; position++) {
			assertThat(candidates.row(position).decision()).as("row %d", position).isEqualTo(undetermined);
		}
		// 사용일자가 없으면 키를 만들 수 없다(결정 R2).
		assertThat(candidates.row(10).decision())
			.isEqualTo(new Decision.Incomplete(CodefIncompleteReason.DUPLICATE_KEY_AMBIGUOUS));
		// 날짜를 해석하지 못한 행은 기간을 모르는 채로 그 카드에만 표시한다.
		assertThat(candidates.incompleteMarks()).filteredOn(mark -> mark.position() == 1)
			.singleElement()
			.satisfies(mark -> {
				assertThat(mark.cardName()).isEqualTo("SYN CARD A");
				assertThat(mark.usedDate()).isEmpty();
			});
		assertThat(candidates.incompleteMarks()).filteredOn(mark -> mark.position() == 4)
			.singleElement()
			.satisfies(mark -> assertThat(mark.usedDate()).contains(LocalDate.of(2026, 10, 15)));
	}

}
