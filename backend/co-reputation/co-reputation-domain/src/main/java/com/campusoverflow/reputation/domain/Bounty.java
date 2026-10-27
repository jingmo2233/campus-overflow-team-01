package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.time.Duration;
import java.time.Instant;

/**
 * 悬赏：OPEN →（问题采纳）→ AWARDED；OPEN →（到期）→ EXPIRED。终态不可再变。
 */
public class Bounty {

    public static final int MIN_POINTS = 10;
    public static final int MAX_POINTS = 500;
    public static final int MAX_DAYS = 14;
    public static final int MIN_DAYS = 1;

    private Long id;
    private final long questionId;
    private final long sponsorId;
    private final int points;
    private BountyStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Long winnerId;
    private Long answerId;
    private Instant closedAt;

    private Bounty(Long id, long questionId, long sponsorId, int points, BountyStatus status, Instant createdAt,
                   Instant expiresAt, Long winnerId, Long answerId, Instant closedAt) {
        this.id = id;
        this.questionId = questionId;
        this.sponsorId = sponsorId;
        this.points = points;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.winnerId = winnerId;
        this.answerId = answerId;
        this.closedAt = closedAt;
    }

    /**
     * 发起悬赏。发起人须为课程管理者（教师/助教/管理员）；问题不能已删除或已采纳。
     * 校验顺序固定为「权限 → 参数 → 问题状态」，保证同样的非法输入总是抛同一类异常。
     */
    public static Bounty open(long questionId, boolean questionOpen, Actor sponsor, boolean sponsorManagesCourse,
                              int points, int days, Instant now) {
        if (!sponsorManagesCourse) {
            throw new ForbiddenException("REP-3001", "只有课程管理者可以发起悬赏");
        }
        if (points < MIN_POINTS || points > MAX_POINTS) {
            throw new BusinessRuleException("REP-1003",
                    "悬赏积分须在 " + MIN_POINTS + "–" + MAX_POINTS + " 之间");
        }
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BusinessRuleException("REP-1004",
                    "悬赏期限须在 " + MIN_DAYS + "–" + MAX_DAYS + " 天之间");
        }
        if (!questionOpen) {
            throw new BusinessRuleException("REP-1001", "问题已删除或已采纳，不能发起悬赏");
        }
        return new Bounty(null, questionId, sponsor.userId(), points, BountyStatus.OPEN, now,
                now.plus(Duration.ofDays(days)), null, null, null);
    }

    public static Bounty reconstitute(Long id, long questionId, long sponsorId, int points, BountyStatus status,
                                      Instant createdAt, Instant expiresAt, Long winnerId, Long answerId,
                                      Instant closedAt) {
        return new Bounty(id, questionId, sponsorId, points, status, createdAt, expiresAt, winnerId, answerId,
                closedAt);
    }

    /** 结算：只有进行中的悬赏可结算，且发起人不能赢自己的悬赏（否则积分原地转一圈）。 */
    public void award(long answerId, long winnerId, Instant now) {
        ensureOpen();
        if (winnerId == sponsorId) {
            throw new BusinessRuleException("REP-1005", "不能赢取自己发起的悬赏");
        }
        this.status = BountyStatus.AWARDED;
        this.answerId = answerId;
        this.winnerId = winnerId;
        this.closedAt = now;
    }

    /** 关闭并退款：到期未采纳，或被采纳的恰好是发起人自己的回答。 */
    public void close(Instant now) {
        ensureOpen();
        this.status = BountyStatus.EXPIRED;
        this.closedAt = now;
    }

    /** 是否已到期：进行中且已过截止时刻（等于截止时刻即视为到期）。 */
    public boolean isDue(Instant now) {
        return status == BountyStatus.OPEN && !now.isBefore(expiresAt);
    }

    /** 终态不可再变（INV-B5），重复结算或关闭一律按状态冲突处理。 */
    private void ensureOpen() {
        if (status != BountyStatus.OPEN) {
            throw new ConflictException("REP-2001", "悬赏已结束，不能再次结算或关闭");
        }
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long questionId() { return questionId; }
    public long sponsorId() { return sponsorId; }
    public int points() { return points; }
    public BountyStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Long winnerId() { return winnerId; }
    public Long answerId() { return answerId; }
    public Instant closedAt() { return closedAt; }
}
