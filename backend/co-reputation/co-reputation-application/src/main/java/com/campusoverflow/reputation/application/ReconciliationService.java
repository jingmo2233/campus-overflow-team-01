package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 适应度函数 FF-8：对账——每个账户的声誉必须等于其流水之和。 */
@Service
public class ReconciliationService {

    public record Mismatch(long userId, int accountReputation, long ledgerSum) {
    }

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;

    public ReconciliationService(ReputationAccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /**
     * 全量比对账户声誉与流水汇总。返回空列表即账实相符。
     * 只有账户没有流水时按 0 比对（正常的新开户就是这个状态）；
     * 只有流水没有账户的情况无法在这里发现，因为不知道该拿哪个账户去比 —— 由账户表反向缺失检查覆盖。
     */
    @Transactional(readOnly = true)
    public List<Mismatch> reconcile() {
        Map<Long, Long> sums = ledger.sumByUser();
        List<ReputationAccount> all = accounts.findAll();
        List<Mismatch> mismatches = new ArrayList<>();
        for (ReputationAccount account : all) {
            long sum = sums.getOrDefault(account.userId(), 0L);
            if (sum != account.reputation()) {
                mismatches.add(new Mismatch(account.userId(), account.reputation(), sum));
            }
        }
        return mismatches;
    }
}
