package lv.sergluka.ib_client.types;

import java.util.HashMap;
import java.util.Map;

public class IbAccountsSummary {

    private final Map<String, IbAccountSummary> accounts = new HashMap<>();

    public void update(String account, String tag, String value, String currency) throws Exception {
        IbAccountSummary summary = accounts.computeIfAbsent(account, key -> new IbAccountSummary());
        summary.update(tag, value, currency);
    }

    public Map<String, IbAccountSummary> getAccounts() {
        return accounts;
    }
}
