/*
 * 功能: 口令强度策略校验工具，提供可配置的长度与字符类别要求。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

import java.util.ArrayList;
import java.util.List;

/**
 * 口令强度策略。
 *
 * <p>纯抽象工具：对明文口令做长度与字符类别校验，返回违规项列表（不抛出携带口令的异常，
 * 也不记录口令明文）。默认策略要求长度 12-128，并至少包含大写、小写、数字、符号中的三类。
 */
public final class PasswordPolicy {

    private final int minLength;
    private final int maxLength;
    private final int minCharacterClasses;

    /**
     * @param minLength            最小长度
     * @param maxLength            最大长度
     * @param minCharacterClasses  至少满足的字符类别数（大写/小写/数字/符号）
     */
    public PasswordPolicy(int minLength, int maxLength, int minCharacterClasses) {
        if (minLength <= 0 || maxLength < minLength) {
            throw new IllegalArgumentException("invalid password length bounds");
        }
        if (minCharacterClasses < 1 || minCharacterClasses > 4) {
            throw new IllegalArgumentException("minCharacterClasses must be within [1,4]");
        }
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.minCharacterClasses = minCharacterClasses;
    }

    /**
     * @return 平台默认口令策略（12-128 长度，至少三类字符）
     */
    public static PasswordPolicy defaults() {
        return new PasswordPolicy(12, 128, 3);
    }

    /**
     * 校验明文口令并返回违规项国际化键列表（空列表表示通过）。
     *
     * @param rawPassword 明文口令
     * @return 违规项国际化键，永不为 {@code null}
     */
    public List<String> validate(CharSequence rawPassword) {
        List<String> violations = new ArrayList<>();
        if (rawPassword == null || rawPassword.length() < minLength) {
            violations.add("error.auth.password.tooShort");
        }
        if (rawPassword != null && rawPassword.length() > maxLength) {
            violations.add("error.auth.password.tooLong");
        }
        if (rawPassword != null && countCharacterClasses(rawPassword) < minCharacterClasses) {
            violations.add("error.auth.password.insufficientComplexity");
        }
        return violations;
    }

    /**
     * @param rawPassword 明文口令
     * @return 是否满足策略
     */
    public boolean isSatisfiedBy(CharSequence rawPassword) {
        return validate(rawPassword).isEmpty();
    }

    private int countCharacterClasses(CharSequence value) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isUpperCase(character)) {
                upper = true;
            } else if (Character.isLowerCase(character)) {
                lower = true;
            } else if (Character.isDigit(character)) {
                digit = true;
            } else {
                symbol = true;
            }
        }
        int classes = 0;
        if (upper) {
            classes++;
        }
        if (lower) {
            classes++;
        }
        if (digit) {
            classes++;
        }
        if (symbol) {
            classes++;
        }
        return classes;
    }
}
