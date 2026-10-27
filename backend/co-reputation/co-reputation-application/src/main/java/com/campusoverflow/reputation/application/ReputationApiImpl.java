package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.api.ReputationApi;
import com.campusoverflow.reputation.api.ReputationSummary;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import java.util.Collection;
import java.util.HashMap;
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

    /** 未开户按 0 声誉返回。其他上下文拿这个值只用于展示，不该因为「还没开户」而报错。 */
    @Override
    public ReputationSummary summary(long userId) {
        return new ReputationSummary(userId,
                accounts.findByUserId(userId).map(ReputationAccount::reputation).orElse(0));
    }

    @Override
    public Map<Long, Integer> reputations(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (ReputationAccount account : accounts.findAllByUserId(userIds)) {
            result.put(account.userId(), account.reputation());
        }
        // 补齐未开户的 ID：调用方拿到的 Map 对每个入参 ID 都有值，不必再判空。
        userIds.forEach(id -> result.putIfAbsent(id, 0));
        return result;
    }
}
