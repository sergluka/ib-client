package lv.sergluka.ib_client.params;

import lv.sergluka.ib_client.IbClient;
import lv.sergluka.ib_client.impl.Validators;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds parameters for {@link IbClient#reqAccountSummary(java.util.function.Consumer)}.
 *
 * <p>Helps to avoid original TWS reqAccountSummary parameters usage, that is not convenient, and involve text constant
 */
public class AccountsSummaryParams {

    private String accountGroup;
    private String ledger;
    private EnumSet<IbClient.AccountSummaryTags> tags = EnumSet.noneOf(IbClient.AccountSummaryTags.class);

    /**
     * Use all the managed accounts.
     *
     * <p>Match group value - "All"
     *
     * @return this
     */
    public AccountsSummaryParams withAllAccountGroups() {
        accountGroupShouldBeUnassigned();

        accountGroup = "All";
        return this;
    }

    /**
     * Use specific accounts.
     *
     * @param group Account group
     * @return this
     */
    public AccountsSummaryParams withAccountGroup(String group) {
        accountGroupShouldBeUnassigned();

        accountGroup = group;
        return this;
    }

    /**
     * Makes sum up values for ALL accounts and currencies.
     *
     * <p>Match ledger value - "$LEDGER:ALL"
     *
     * @return this
     */
    public AccountsSummaryParams inAllCurrencies() {
        ledgerShouldBeUnassigned();

        ledger = "$LEDGER:ALL";
        return this;
    }

    /**
     * Makes return summary data only in base currency.
     *
     * <p>Match ledger value - "$LEDGER"
     *
     * @return this
     */
    public AccountsSummaryParams inBaseCurrency() {
        ledgerShouldBeUnassigned();

        ledger = "$LEDGER";
        return this;
    }

    /**
     * Makes return summary data in the specific currency.
     *
     * @param currency currency
     * @return this
     */
    public AccountsSummaryParams inCurrency(String currency) {
        ledgerShouldBeUnassigned();

        ledger = "$LEDGER:" + currency;
        return this;
    }

    /**
     * Makes return summary data with the desired tags.
     *
     * @param newTags tags
     * @return this
     */
    public AccountsSummaryParams withTags(Set<IbClient.AccountSummaryTags> newTags) {
        tags.addAll(newTags);
        return this;
    }

    /**
     * Makes return summary data with the desired tags.
     *
     * @param newTags tags
     * @return this
     */
    public AccountsSummaryParams withTags(IbClient.AccountSummaryTags... newTags) {
        tags.addAll(Arrays.asList(newTags));
        return this;
    }

    /**
     * Makes return summary data with the all tags.
     *
     * @return this
     */
    public AccountsSummaryParams withAllTag() {
        tags = EnumSet.allOf(IbClient.AccountSummaryTags.class);
        return this;
    }

    public void validate() {
        Validators.stringShouldNotBeEmpty(accountGroup, "Account group should be defined");
        Validators.collectionShouldNotBeEmpty(tags, "Tags should be defined");
        Validators.stringShouldNotBeEmpty(ledger, "Currency should be defined");
    }

    public String getAccountGroup() {
        return accountGroup;
    }

    public String buildTags() {
        return Stream.concat(tags.stream().map(Enum::name), Stream.of(ledger)).collect(Collectors.joining(","));
    }

    private void accountGroupShouldBeUnassigned() {
        if (accountGroup != null) {
            throw new IllegalArgumentException("Account group already assigned");
        }
    }

    private void ledgerShouldBeUnassigned() {
        if (ledger != null) {
            throw new IllegalArgumentException("Ledger already assigned");
        }
    }
}
