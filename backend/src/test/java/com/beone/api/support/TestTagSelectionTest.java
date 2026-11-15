package com.beone.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.annotation.Testable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/**
 * Gradle 태스크는 태그로 테스트를 나눈다(test: integration·web 제외, webTest: web, integrationTest: integration).
 * 태그가 겹치거나 Spring 컨텍스트 테스트가 태그 없이 test 태스크로 새지 않는지 확인한다.
 */
class TestTagSelectionTest {

	private static final String WEB = "web";

	private static final String INTEGRATION = "integration";

	@Test
	void everyTestClassBelongsToExactlyOneTask() {
		List<Class<?>> testClasses = testClasses();
		assertThat(testClasses).contains(TestTagSelectionTest.class);

		List<String> problems = new ArrayList<>();
		for (Class<?> testClass : testClasses) {
			MergedAnnotations annotations = MergedAnnotations.from(testClass, SearchStrategy.TYPE_HIERARCHY);
			Set<String> tags = annotations.stream(Tag.class)
				.map(tag -> tag.getString("value"))
				.collect(Collectors.toSet());
			boolean web = tags.contains(WEB);
			boolean integration = tags.contains(INTEGRATION);
			boolean springBootTest = annotations.isPresent(SpringBootTest.class);
			boolean webMvcTest = annotations.isPresent(WebMvcTest.class);

			if (web && integration) {
				problems.add(testClass.getName() + ": web과 integration 태그를 함께 사용");
			}
			if (springBootTest && !integration) {
				problems.add(testClass.getName() + ": @SpringBootTest에 integration 태그 없음");
			}
			if (webMvcTest && !web) {
				problems.add(testClass.getName() + ": @WebMvcTest에 web 태그 없음");
			}
			if (web && !webMvcTest) {
				problems.add(testClass.getName() + ": web 태그에 @WebMvcTest 없음");
			}
			if (web && springBootTest) {
				problems.add(testClass.getName() + ": @WebSliceTest와 @IntegrationTest를 함께 사용");
			}
		}
		assertThat(problems).isEmpty();
	}

	@Test
	void knownTestsAreClassifiedAsExpected() {
		List<Class<?>> testClasses = testClasses();

		assertThat(testClasses).extracting(Class::getSimpleName)
			.contains("ApplicationIntegrationTest", "GlobalExceptionHandlerWebTest", "MigrationNamingTest");
		assertThat(tagsOf(findBySimpleName(testClasses, "ApplicationIntegrationTest"))).containsExactly(INTEGRATION);
		assertThat(tagsOf(findBySimpleName(testClasses, "GlobalExceptionHandlerWebTest"))).containsExactly(WEB);
		assertThat(tagsOf(findBySimpleName(testClasses, "MigrationNamingTest"))).isEmpty();
	}

	private static Set<String> tagsOf(Class<?> testClass) {
		return MergedAnnotations.from(testClass, SearchStrategy.TYPE_HIERARCHY)
			.stream(Tag.class)
			.map(tag -> tag.getString("value"))
			.collect(Collectors.toSet());
	}

	private static Class<?> findBySimpleName(List<Class<?>> classes, String simpleName) {
		return classes.stream().filter(c -> c.getSimpleName().equals(simpleName)).findFirst().orElseThrow();
	}

	// com.beone 아래에서 JUnit 테스트 메서드를 가진 클래스를 찾는다.
	private static List<Class<?>> testClasses() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter((reader, factory) -> true);
		return scanner.findCandidateComponents("com.beone")
			.stream()
			.<Class<?>>map(definition -> load(definition.getBeanClassName()))
			.filter(TestTagSelectionTest::hasTestMethod)
			.toList();
	}

	private static boolean hasTestMethod(Class<?> type) {
		return Arrays.stream(type.getDeclaredMethods())
			.anyMatch(method -> MergedAnnotations.from(method).isPresent(Testable.class));
	}

	private static Class<?> load(String className) {
		try {
			return Class.forName(className, false, TestTagSelectionTest.class.getClassLoader());
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
