package com.beone.api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.annotation.AliasFor;

/**
 * Spring MVC 웹 슬라이스 테스트. MockMvc로 디스패치·직렬화·예외 처리를 확인하며 DB·Docker를 쓰지 않는다.
 * {@code webTest} Gradle 태스크에서만 실행하고 {@code @IntegrationTest}와 함께 쓰지 않는다.
 *
 * <p>테스트 전용 컨트롤러는 테스트 클래스 안의 정적 클래스로 두고 {@code @Import}로 등록한다.
 * 테스트 클래스 안의 클래스는 컴포넌트 스캔에서 빠지므로 다른 테스트 컨텍스트에 섞이지 않는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("web")
@WebMvcTest
public @interface WebSliceTest {

	/** 슬라이스에 포함할 컨트롤러. 비우면 스캔된 모든 컨트롤러를 포함한다. */
	@AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
	Class<?>[] controllers() default {};

	/** 테스트에 추가할 프로퍼티. */
	@AliasFor(annotation = WebMvcTest.class, attribute = "properties")
	String[] properties() default {};

}
