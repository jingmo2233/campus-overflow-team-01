package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;

/**
 * 声誉账户。不变量：reputation 恒等于该用户所有账本流水 delta 之和（由对账任务 FF-8 验证）。
 * 悬赏积分 = 可用 + 冻结，冻结部分在悬赏结算或到期时释放。
 */
public class ReputationAccount {

    private final long userId;
    private int reputation;
    private int availablePoints;
    private int frozenPoints;
    private final long version;

    private ReputationAccount(long userId, int reputation, int availablePoints, int frozenPoints, long version) {
        this.userId = userId;
        this.reputation = reputation;
        this.availablePoints = availablePoints;
        this.frozenPoints = frozenPoints;
        this.version = version;
    }

    /**
     * 开户：声誉从 0 起算，按角色发放初始悬赏积分（见 INV-R5，只在此处写入一次）。
     * 初始积分为 0 是合法的（学生），负数不是。
     */
    public static ReputationAccount open(long userId, int initialPoints) {
        if (initialPoints < 0) {
            throw new BusinessRuleException("REP-1006", "初始积分不能为负");
        }
        return new ReputationAccount(userId, 0, initialPoints, 0, 0L);
    }

    public static ReputationAccount reconstitute(long userId, int reputation, int availablePoints, int frozenPoints,
                                                 long version) {
        return new ReputationAccount(userId, reputation, availablePoints, frozenPoints, version);
    }

    /** 只允许通过账本流水改变声誉（由应用服务保证先写流水）。 */
    public void applyReputation(int delta) {
        this.reputation += delta;
    }

    /** 发布悬赏时冻结积分：可用减少、冻结增加，总量不变（INV-R3 / INV-B3）。 */
    public void freezePoints(int points) {
        requirePositive(points);
        if (availablePoints < points) {
            throw new BusinessRuleException("REP-1002", "可用悬赏积分不足");
        }
        this.availablePoints -= points;
        this.frozenPoints += points;
    }

    /**
     * 悬赏成功发放：冻结积分直接从冻结池转出，发起人账户中消失（INV-B6）。
     * 注意不可写成「先解冻再扣减」——那样在并发下会出现负余额。
     */
    public void spendFrozen(int points) {
        requirePositive(points);
        ensureFrozen(points);
        this.frozenPoints -= points;
    }

    /** 悬赏到期：冻结积分退回可用。 */
    public void refundFrozen(int points) {
        requirePositive(points);
        ensureFrozen(points);
        this.frozenPoints -= points;
        this.availablePoints += points;
    }

    /** 冻结池余额校验：动用的积分不能超过已冻结的数量。 */
    private void ensureFrozen(int points) {
        if (frozenPoints < points) {
            throw new BusinessRuleException("REP-1002", "冻结积分不足，无法完成该操作");
        }
    }

    /** 冻结、支出、退回的数量必须为正数，避免用负数绕过校验反向改余额。 */
    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new BusinessRuleException("REP-1006", "积分数量必须为正数");
        }
    }

    public long userId() { return userId; }
    public int reputation() { return reputation; }
    public int availablePoints() { return availablePoints; }
    public int frozenPoints() { return frozenPoints; }
    public long version() { return version; }
}
