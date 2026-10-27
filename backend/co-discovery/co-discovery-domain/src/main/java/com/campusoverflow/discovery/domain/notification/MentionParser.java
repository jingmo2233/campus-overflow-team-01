package com.campusoverflow.discovery.domain.notification;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析评论中的 @昵称（昵称不含空格与 @，长度 2–20）。每条评论最多提醒 5 人，防止滥用。 */
public final class MentionParser {

    private static final Pattern MENTION = Pattern.compile("@([^\\s@，。,.!?！？:：;；、]{2,20})");
    public static final int MAX_MENTIONS = 5;

    private MentionParser() {
    }

    public static Set<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> mentions = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(text);
        while (mentions.size() < MAX_MENTIONS && matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }
}
