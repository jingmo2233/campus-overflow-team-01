package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.api.ReputationApi;
import com.campusoverflow.reputation.api.ReputationSummary;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReputationApiImpl implements ReputationApi {

    private final ReputationAccountRepository accounts;

    public ReputationApiImpl(ReputationAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public ReputationSummary summary(long userId) {
        int reputation = accounts.findByUserId(userId).map(ReputationAccount::reputation).orElse(0);
        return new ReputationSummary(userId, reputation);
    }

    /** 批量查询声誉值：未开户的用户按 0 返回，避免调用方到处判空。 */
    @Override
    public Map<Long, Integer> reputations(Collection<Long> userIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<ReputationAccount> found = accounts.findAllByUserId(userIds);
        Map<Long, Integer> existing = new HashMap<>();
        for (ReputationAccount account : found) {
            existing.put(account.userId(), account.reputation());
        }
        for (Long userId : userIds) {
            result.put(userId, existing.getOrDefault(userId, 0));
        }
        return result;
    }
}
