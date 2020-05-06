package lv.sergluka.ib_client.params


import spock.lang.Specification
import spock.lang.Subject

import static lv.sergluka.ib_client.IbClient.AccountSummaryTags.AccountType
import static lv.sergluka.ib_client.IbClient.AccountSummaryTags.NetLiquidation

class AccountsSummaryParamsTest extends Specification {

    @Subject
    def params = new AccountsSummaryParams()

    def "Build params large as large as possible"() {

        when:
        params.inAllCurrencies().withAllAccountGroups().withAllTag()

        then:
        params.getAccountGroup() == "All"
        params.buildTags() == 'AccountType,NetLiquidation,TotalCashValue,SettledCash,AccruedCash,BuyingPower,' +
                'EquityWithLoanValue,PreviousEquityWithLoanValue,GrossPositionValue,RegTEquity,RegTMargin,SMA,' +
                'InitMarginReq,MaintMarginReq,AvailableFunds,ExcessLiquidity,Cushion,FullInitMarginReq,' +
                'FullMaintMarginReq,FullAvailableFunds,FullExcessLiquidity,LookAheadNextChange,LookAheadInitMarginReq,' +
                'LookAheadMaintMarginReq,LookAheadAvailableFunds,LookAheadExcessLiquidity,HighestSeverity,' +
                'DayTradesRemaining,Leverage,$LEDGER:ALL'
    }

    def "Build params for particular currency, account and tag"() {

        when:
        params.inCurrency("EUR").withAccountGroup("AA0000").withTags(AccountType, NetLiquidation)

        then:
        params.getAccountGroup() == "AA0000"
        params.buildTags() == 'AccountType,NetLiquidation,$LEDGER:EUR'
    }

    def "Build params for base currency, account and tag"() {

        when:
        params.inBaseCurrency().withAccountGroup("AA0000").withTags(AccountType, NetLiquidation)

        then:
        params.getAccountGroup() == "AA0000"
        params.buildTags() == 'AccountType,NetLiquidation,$LEDGER'
    }
}
