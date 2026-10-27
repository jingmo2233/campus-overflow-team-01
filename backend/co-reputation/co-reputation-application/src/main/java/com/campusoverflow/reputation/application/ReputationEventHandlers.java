package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.domain.BadgePolicy;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.reputation.domain.ReputationRulebook;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订阅上游上下文的集成事件（由 OutboxRelay 在事务提交后投递，至少一次语义）。
 * 每个处理器运行在自己的事务中，并通过账本唯一键 / 徽章存在性检查实现幂等。
 */
@Component
public class ReputationEventHandlers {

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;
    private final ReputationLedgerService ledgerService;
    private final BadgeService badges;
    private final BountyService bounties;
    private final Clock clock;

    public ReputationEventHandlers(ReputationAccountRepository accounts, LedgerRepository ledger,
                                   ReputationLedgerService ledgerService, BadgeService badges,
                                   BountyService bounties, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.ledgerService = ledgerService;
        this.badges = badges;
        this.bounties = bounties;
        this.clock = clock;
    }

    /** 开立声誉账户。INV-R5：初始积分只写一次，重复投递直接跳过。 */
    @EventListener
    @Transactional
    public void on(UserRegisteredEvent e) {
        if (accounts.findByUserId(e.userId()).isPresent()) {
            return;
        }
        accounts.save(ReputationAccount.open(e.userId(), ReputationRulebook.initialPoints(e.role())));
    }

    /** 场景 R-2 的异步后半段：采纳 → 回答者 +15 → 徽章 → 悬赏结算。 */
    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        // 自问自答被采纳不加分：acceptedDelta 返回 0，ledgerService.record 对 0 增量直接短路，
        // 因此既不写流水也不计入「被采纳数」，徽章也就不会因此提前授予。
        int delta = ReputationRulebook.acceptedDelta(e.answerAuthorId(), e.questionAuthorId());
        ledgerService.record(e.answerAuthorId(), e.eventId(), ReputationReason.ANSWER_ACCEPTED, delta,
                "ANSWER", e.answerId());
        long acceptedCount = ledger.countByUserAndReason(e.answerAuthorId(), ReputationReason.ANSWER_ACCEPTED);
        BadgePolicy.forAcceptedCount(acceptedCount).forEach(type -> badges.grant(e.answerAuthorId(), type));
        // 悬赏结算在采纳的同一事务内完成；该问题没有进行中的悬赏时内部直接返回。
        bounties.settleOnAccept(e.questionId(), e.answerId(), e.answerAuthorId(), e.eventId());
    }

    @EventListener
    @Transactional
    public void on(VoteCastEvent e) {
        int delta = ReputationRulebook.voteDelta(e.oldValue(), e.newValue());
        if (delta == 0) {
            return;
        }
        ReputationReason reason = "QUESTION".equals(e.targetType())
                ? ReputationReason.QUESTION_VOTE
                : ReputationReason.ANSWER_VOTE;
        if (delta > 0) {
            // INV-R4：每人每个自然日（UTC）因被投票获得的正向声誉上限 200，超出部分截断不入账。
            // 只夹正向增量——撤销踩（+2）这类小额正向收益同样受上限约束，但负向增量必须原样入账。
            int gainedToday = ledger.sumPositiveVoteDeltaSince(e.targetAuthorId(), startOfUtcDay());
            delta = ReputationRulebook.applyDailyCap(delta, gainedToday);
        }
        ledgerService.record(e.targetAuthorId(), e.eventId(), reason, delta, e.targetType(), e.targetId());
    }

    /** 提交回答：只授「初来乍到」徽章，不给声誉（规则手册里回答本身不计分）。 */
    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        badges.grant(e.answerAuthorId(), BadgeType.FIRST_ANSWER);
    }

    /** 教师认证：只授「名师认可」徽章。规则手册没有 ENDORSED 入账原因，故不加声誉。 */
    @EventListener
    @Transactional
    public void on(AnswerEndorsedEvent e) {
        badges.grant(e.answerAuthorId(), BadgeType.TEACHER_ENDORSED);
    }

    /**
     * 每日上限的统计起点：UTC 自然日零点。与 {@code ReputationRulebook} 注释里的「自然日（UTC）」
     * 保持一致；排行榜的周/月/学期键才用 Asia/Shanghai（见 {@code AcademicTerm}）。
     */
    private Instant startOfUtcDay() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
