package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.reputation.domain.ReputationRulebook;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 用例：悬赏的发起、结算、到期退款。积分冻结与悬赏状态在同一事务中变更。 */
@Service
public class BountyService {

    /** 单次到期扫描的批上限，避免一次事务里锁定过多悬赏行。 */
    private static final int EXPIRE_BATCH = 200;

    private final BountyRepository bounties;
    private final ReputationAccountRepository accounts;
    private final ReputationLedgerService ledgerService;
    private final QaQueryApi qa;
    private final IdentityApi identity;
    private final IntegrationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    public BountyService(BountyRepository bounties, ReputationAccountRepository accounts,
                         ReputationLedgerService ledgerService, QaQueryApi qa, IdentityApi identity,
                         IntegrationEventPublisher events, AuditTrail audit, Clock clock) {
        this.bounties = bounties;
        this.accounts = accounts;
        this.ledgerService = ledgerService;
        this.qa = qa;
        this.identity = identity;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public long open(Actor actor, long questionId, int points, int days) {
        QuestionRef question = qa.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
        // INV-B4：同一问题同时只能有一个进行中的悬赏。此处先查一次给出明确的 409，
        // 并发下的兜底靠 question_id + status='OPEN' 的唯一索引（SYS-409-DUPLICATE）。
        if (bounties.findOpenByQuestion(questionId).isPresent()) {
            throw new ConflictException("REP-2002", "该问题已有进行中的悬赏");
        }
        Instant now = clock.instant();
        boolean questionOpen = !question.deleted() && question.acceptedAnswerId() == null;
        boolean managesCourse = identity.canManageCourse(actor.userId(), question.courseId());
        Bounty bounty = Bounty.open(questionId, questionOpen, actor, managesCourse, points, days, now);
        // INV-B3：发布即冻结。积分不足时 freezePoints 抛 REP-1002，整个用例回滚，不会留下悬赏记录。
        ReputationAccount sponsor = loadAccount(actor);
        sponsor.freezePoints(points);
        accounts.save(sponsor);
        Bounty saved = bounties.save(bounty);
        events.publish(new BountyOpenedEvent(EventIds.next(), now, saved.id(), questionId, actor.userId(), points,
                saved.expiresAt()));
        audit.record("BOUNTY_OPENED", actor.userId(), "BOUNTY", String.valueOf(saved.id()),
                "question=" + questionId + ", points=" + points);
        return saved.id();
    }

    /** 由 AnswerAccepted 事件触发（在处理器事务内）。幂等：悬赏已结束则直接返回。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleOnAccept(long questionId, long answerId, long winnerId, String sourceEventId) {
        Optional<Bounty> found = bounties.findOpenByQuestion(questionId);
        if (found.isEmpty()) {
            // 从未发起，或已经结算/到期关闭 —— 重复投递时走到这里，直接返回即幂等。
            return;
        }
        Bounty bounty = found.get();
        Optional<ReputationAccount> sponsorAccount = accounts.findByUserId(bounty.sponsorId());
        if (sponsorAccount.isEmpty()) {
            // 发起人账户缺失属于数据异常，不在这里凭空造账户；留给对账任务暴露。
            return;
        }
        ReputationAccount sponsor = sponsorAccount.get();
        Instant now = clock.instant();
        if (winnerId == bounty.sponsorId()) {
            // 发起人自己的回答被采纳：积分原地转一圈没有意义，与「到期未采纳」同样按关闭退款处理。
            closeAndRefund(bounty, sponsor, now);
            return;
        }
        bounty.award(answerId, winnerId, now);
        bounties.save(bounty);
        // INV-B6：直接从冻结池转出，绝不能写成「先 refundFrozen 再扣 available」——并发下会出现负余额。
        sponsor.spendFrozen(bounty.points());
        accounts.save(sponsor);
        ledgerService.record(winnerId, sourceEventId, ReputationReason.BOUNTY_AWARDED, bounty.points(),
                "QUESTION", questionId);
        events.publish(new BountyAwardedEvent(EventIds.next(), now, bounty.id(), questionId, bounty.sponsorId(),
                winnerId, bounty.points()));
        audit.record("BOUNTY_AWARDED", winnerId, "BOUNTY", String.valueOf(bounty.id()),
                "question=" + questionId + ", points=" + bounty.points());
    }

    /** 到期未采纳的悬赏：关闭并退回冻结积分。由定时任务调用。 */
    @Transactional
    public int expireDue() {
        Instant now = clock.instant();
        List<Bounty> due = bounties.findDue(now, EXPIRE_BATCH);
        int processed = 0;
        for (Bounty bounty : due) {
            Optional<ReputationAccount> sponsor = accounts.findByUserId(bounty.sponsorId());
            if (sponsor.isEmpty()) {
                continue;
            }
            closeAndRefund(bounty, sponsor.get(), now);
            processed++;
        }
        return processed;
    }

    @Transactional(readOnly = true)
    public Optional<BountyView> latest(long questionId) {
        return bounties.findLatestByQuestion(questionId).map(bounty -> new BountyView(bounty.id(),
                bounty.questionId(), bounty.sponsorId(),
                identity.findUser(bounty.sponsorId()).map(UserSummary::displayName).orElse(null),
                bounty.points(), bounty.status().name(), bounty.createdAt(), bounty.expiresAt(),
                bounty.winnerId(), bounty.answerId()));
    }

    private void closeAndRefund(Bounty bounty, ReputationAccount sponsor, Instant now) {
        bounty.close(now);
        bounties.save(bounty);
        // INV-B6：退回走冻结池 → 可用池，发起人的积分总量守恒。
        sponsor.refundFrozen(bounty.points());
        accounts.save(sponsor);
        events.publish(new BountyClosedEvent(EventIds.next(), now, bounty.id(), bounty.questionId(),
                bounty.sponsorId(), bounty.points()));
        audit.record("BOUNTY_CLOSED", sponsor.userId(), "BOUNTY", String.valueOf(bounty.id()),
                "question=" + bounty.questionId() + ", refunded=" + bounty.points());
    }

    /**
     * 取发起人账户。账户本应由 UserRegistered 事件开立，这里兜底开户是为了在事件投递延迟时
     * 不让用户看到「账户不存在」；因为只在缺失时创建，INV-R5 的「初始积分只写一次」仍成立。
     */
    private ReputationAccount loadAccount(Actor actor) {
        return accounts.findByUserId(actor.userId())
                .orElseGet(() -> ReputationAccount.open(actor.userId(),
                        ReputationRulebook.initialPoints(actor.role())));
    }
}
