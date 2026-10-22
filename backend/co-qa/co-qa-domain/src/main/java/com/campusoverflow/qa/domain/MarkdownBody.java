package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.Guard;

/** Markdown 正文。只保存原文；渲染与 XSS 净化在基础设施层完成。 */
public record MarkdownBody(String value) {

    private static final int MAX = 30000;
    private static final int MIN_QUESTION = 20;
    private static final int MIN_ANSWER = 10;

    public static MarkdownBody ofQuestion(String raw) {
        return new MarkdownBody(Guard.requireText(raw, MIN_QUESTION, MAX, "QA-1002", "问题正文"));
    }

    public static MarkdownBody ofAnswer(String raw) {
        return new MarkdownBody(Guard.requireText(raw, MIN_ANSWER, MAX, "QA-1002", "回答正文"));
    }
}
