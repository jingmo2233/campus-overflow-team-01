package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReputationQueryService {

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;
    private final BadgeRepository badges;
    private final IdentityApi identity;

    public ReputationQueryService(ReputationAccountRepository accounts, LedgerRepository ledger,
                                  BadgeRepository badges, IdentityApi identity) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.badges = badges;
        this.identity = identity;
    }

    /**
     * 声誉档案。账户未开立时按「声誉 0、无徽章」返回而不是 404 —— 开户事件可能比用户资料晚到，
     * 这时报 404 会让刚注册的用户看到「不存在」，体验上说不通。真正不存在的是用户，那才 404。
     */
    public ReputationProfileView profile(long userId, Actor viewerOrNull) {
        UserSummary user = identity.findUser(userId)
                .orElseThrow(() -> new NotFoundException("ID-4001", "用户不存在"));
        Optional<ReputationAccount> maybeAccount = accounts.findByUserId(userId);
        int reputation = maybeAccount.map(ReputationAccount::reputation).orElse(0);
        // 积分是个人数据：只有本人和管理员能看到，其他人只能看到声誉值与徽章。
        boolean canSeePoints = viewerOrNull != null && (viewerOrNull.is(userId) || viewerOrNull.isAdmin());
        List<ReputationProfileView.BadgeView> badgeViews = badges.findByUser(userId).stream()
                .map(award -> new ReputationProfileView.BadgeView(award.type().name(), award.type().displayName(),
                        award.type().description(), award.awardedAt()))
                .toList();
        return new ReputationProfileView(userId, user.displayName(), reputation,
                canSeePoints ? maybeAccount.map(ReputationAccount::availablePoints).orElse(0) : null,
                canSeePoints ? maybeAccount.map(ReputationAccount::frozenPoints).orElse(0) : null,
                badgeViews);
    }

    /** 声誉流水属于个人数据：仅本人与管理员可查看。 */
    public PageResult<LedgerEntryView> ledger(long userId, Actor viewer, PageRequest page) {
        if (viewer == null || !(viewer.is(userId) || viewer.isAdmin())) {
            throw new ForbiddenException("REP-3002", "只能查看自己的声誉流水");
        }
        return ledger.findByUser(userId, page).map(entry -> new LedgerEntryView(entry.id(),
                entry.reason().name(), entry.reason().label(), entry.delta(), entry.refType(), entry.refId(),
                entry.createdAt()));
    }
}
