package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.Guard;

/** Markdown 正文。只保存原文；渲染与 XSS 净化在基础设施层完成。 */
public record MarkdownBody(String value) {

    private static final int MAX = 30000;

    /** 问题正文下限：与 AskPage「至少 20 字」提示保持一致。 */
    public static final int MIN_QUESTION = 20;

    /** 回答正文下限。 */
    public static final int MIN_ANSWER = 10;

    /**
     * record 的规范构造器是 public 的，无法阻止调用方绕过工厂直接 new，
     * 因此这里做保底校验：空、纯空白、超长一律拒绝，保证不存在「未校验的正文对象」。
     * 问题正文更严格的 20 字下限由工厂与聚合（Question.post/edit）双重保证。
     */
    public MarkdownBody {
        value = Guard.requireText(value, MIN_ANSWER, MAX, "QA-1002", "正文");
    }

    public static MarkdownBody ofQuestion(String raw) {
        return new MarkdownBody(raw).requireAtLeast(MIN_QUESTION, "问题正文");
    }

    public static MarkdownBody ofAnswer(String raw) {
        return new MarkdownBody(raw);
    }

    /**
     * 在通用下限之上追加更严格的下限。供聚合在接收外部构造的 MarkdownBody 时做防御性校验，
     * 避免 {@code new MarkdownBody("x".repeat(15))} 这类绕过工厂的对象流入问题正文。
     */
    public MarkdownBody requireAtLeast(int min, String fieldName) {
        int length = value.codePointCount(0, value.length());
        if (length < min) {
            throw new BusinessRuleException("QA-1002",
                    fieldName + "长度应在 " + min + "–" + MAX + " 个字符之间（当前 " + length + "）");
        }
        return this;
    }
}
