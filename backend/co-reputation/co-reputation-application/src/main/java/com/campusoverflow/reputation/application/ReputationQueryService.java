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

    public ReputationProfileView profile(long userId, Actor viewerOrNull) {
        UserSummary user = identity.findUser(userId)
                .orElseThrow(() -> new NotFoundException("REP-3001", "用户不存在"));
        ReputationAccount account = accounts.findByUserId(userId).orElse(null);
        boolean canSeePoints = viewerOrNull != null && (viewerOrNull.userId() == userId || viewerOrNull.isAdmin());
        Integer availablePoints = canSeePoints ? (account == null ? 0 : account.availablePoints()) : null;
        Integer frozenPoints = canSeePoints ? (account == null ? 0 : account.frozenPoints()) : null;
        java.util.List<ReputationProfileView.BadgeView> badgeViews = badges.findByUser(userId).stream()
                .map(b -> new ReputationProfileView.BadgeView(b.type().name(), b.type().displayName(),
                        b.type().description(), b.awardedAt()))
                .toList();
        return new ReputationProfileView(userId, user.displayName(),
                account == null ? 0 : account.reputation(), availablePoints, frozenPoints, badgeViews);
    }

    /** 声誉流水属于个人数据：仅本人与管理员可查看。 */
    public PageResult<LedgerEntryView> ledger(long userId, Actor viewer, PageRequest page) {
        if (viewer == null || (viewer.userId() != userId && !viewer.isAdmin())) {
            throw new ForbiddenException("REP-3002", "只能查看自己的声誉流水");
        }
        return ledger.findByUser(userId, page).map(e -> new LedgerEntryView(e.id(), e.reason().name(),
                e.reason().label(), e.delta(), e.refType(), e.refId(), e.createdAt()));
    }
}
