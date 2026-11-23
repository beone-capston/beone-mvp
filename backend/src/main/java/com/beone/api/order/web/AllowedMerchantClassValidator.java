package com.beone.api.order.web;

import com.beone.api.order.application.MerchantClassCatalog;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link AllowedMerchantClass} 판정. Spring이 만들 때 {@link MerchantClassCatalog}를 주입한다.
 */
public class AllowedMerchantClassValidator implements ConstraintValidator<AllowedMerchantClass, String> {

	private final MerchantClassCatalog catalog;

	public AllowedMerchantClassValidator(MerchantClassCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || this.catalog.isAllowed(value);
	}

}
