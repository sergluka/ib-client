package com.finplant.ib

import com.finplant.ib.params.AccountsSummaryParams
import com.finplant.ib.params.IbClientOptions
import com.finplant.ib.types.*
import com.ib.client.*
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.function.Function
import java.util.function.Predicate

import static com.finplant.ib.IbClient.BarSize.*
import static org.assertj.core.api.Assertions.assertThat

class IbClientTest extends Specification {

    public static final String ACCOUNT = "DU22993"
    public static final String TERMINAL_HOSTNAME = "127.0.0.1"
    public static final int TERMINAL_PORT = 7497
    public static final int CONNECTION_ID = 0

    IbClient client = new IbClient()

    void setup() {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(10))

        def options = new IbClientOptions().connectionDelay(Duration.ofSeconds(1))
        client = new IbClient(options)
        client.connect(TERMINAL_HOSTNAME, TERMINAL_PORT, CONNECTION_ID).block(Duration.ofSeconds(3))
        client.cancelAll().timeout(Duration.ofSeconds(10)).block()
//        closeAllPositions()
    }

    void closeAllPositions() {
        client.subscribeOnPositionChange()
                .takeUntil({ it == IbPosition.COMPLETE } as Predicate)
                .filter { it.pos != BigDecimal.ZERO }
                .toIterable()
                .forEach { position ->
                    try {
                        return closePosition(position)
                    } catch (Exception e) {
                        throw new Exception("Cannot close position: ${position}}", e)
                    }
                }
    }

    def closePosition(IbPosition position) {

        println("Closing position: $position")

        def contractDetails = client.reqContractDetails(position.contract).take(1).blockingLast()
        position.contract.exchange(contractDetails.validExchanges())

        def order = new Order()
        order.action(position.pos < 0.0 ? Types.Action.BUY : Types.Action.SELL)
        order.orderType(OrderType.MKT)
        order.totalQuantity(position.pos.abs())

        client.placeOrder(position.contract, order).block()

        println("Close position: $position")
    }

    void cleanup() {
        if (client) {
//            client.cancelAll().block()()
            client.close()
        }
    }

    def "smoke"() {
        expect:
        client.isConnected()
    }

    def "Call reqCurrentTime is OK"() {
        expect:
        StepVerifier.create(client.getCurrentTime())
                .assertNext { assertThat(it).isAfter(LocalDateTime.ofEpochSecond(1510320971L, 0, ZoneOffset.UTC)) }
                .verifyComplete()
    }

    def "Call reqContractDetails is OK"() {
        expect:
        StepVerifier.create(client.reqContractDetails(createContractEUR()))
                .assertNext { ContractDetails details ->
                    assert details.conid() == 12087792
                    assert details.marketRuleIds() == "239"
                    assert details.contract().symbol() == "EUR"
                    assert details.contract().currency() == "USD"
                }
                .thenCancel()
                .verify()
    }

    def "Requesting matching symbols for FB should return valid list"() {

        expect:
        StepVerifier.create(client.reqMatchingSymbols("FB"))
                .assertNext { IbContractDescription description ->
                    assert description.contract.symbol == "FB"
                    assert description.contract.secType == Types.SecType.STK
                    assert description.contract.primaryExchange == "NASDAQ.NMS"
                    assert description.derivativeSecTypes == [Types.SecType.CFD, Types.SecType.OPT, Types.SecType.IOPT,
                                                              Types.SecType.WAR, Types.SecType.FUT]
                }
                .assertNext { IbContractDescription description ->
                    assert description.contract.symbol == "FB"
                    assert description.contract.secType == Types.SecType.STK
                    assert description.contract.primaryExchange == "MEXI"
                    assert description.derivativeSecTypes.isEmpty()
                }
                .expectNextCount(15)
                .verifyComplete()
    }

    def "Call placeOrder is OK"() {
        given:
        def contract = createContractEUR()
        def order = createOrderStp()

        expect:
        StepVerifier.create(client.placeOrder(contract, order))
                .assertNext { IbOrder result ->
                    assert result.orderId > 0
                }
                .verifyComplete()
    }

    def "Call placeOrder with wrong data should raise error"() {
        given:
        def contract = new Contract()
        def order = new Order()

        expect:
        StepVerifier.create(client.placeOrder(contract, order))
                .expectError(IbExceptions.TerminalError)
                .verify()
    }

    def "Cancel request should be completed for limit order"() {
        given:
        def order = createOrderStp()
        order.auxPrice(10000)

        def placeAndWaitStatus = client.placeOrder(createContractEUR(), order)
                .flatMap { newOrder ->
                    client.subscribeOnOrderNewStatus()
                            .filter { status -> status.orderId == newOrder.orderId }
                            .filter { status -> status.isActive() }
                            .next()
                }

        expect:
        StepVerifier.create(placeAndWaitStatus.flatMap { client.cancelOrder(it.orderId) })
                .verifyComplete()
    }

    def "Cancel request should return error if order already has been filled"() {
        given:
        def order = createOrderStp()

        def placeAndWaitFill = client.placeOrder(createContractEUR(), order)
                .flatMap { newOrder ->
                    client.subscribeOnOrderNewStatus()
                            .filter { status -> status.orderId == newOrder.orderId }
                            .filter { status -> status.isFilled() }
                            .next()
                }

        expect:
        StepVerifier.create(placeAndWaitFill.flatMap { client.cancelOrder(it.orderId) })
                .verifyError(IbExceptions.OrderAlreadyFilledError)
    }

    def "Await execution report after fill"() {
        given:
        def placeAndWaitFill = client.placeOrder(createContractEUR(), createOrderStp())
                .flatMap { newOrder ->
                    client.subscribeOnOrderNewStatus()
                            .filter { status -> status.orderId == newOrder.orderId }
                            .filter { status -> status.isFilled() }
                            .next()
                }

        expect:
        StepVerifier.create(client.subscribeOnExecutionReport())
                .then { placeAndWaitFill.block(Duration.ofSeconds(10)) }
                .assertNext { IbExecutionReport report ->
                    assert report.execution.orderId > 0
                    assert report.execution.execId == report.commission.execId
                    assert report.commission.commission > 0.0
                }
                .thenCancel()
                .verify()
    }

    def "Cancel request should raise error if order doesn't exists"() {
        expect:
        StepVerifier.create(client.cancelOrder(1111111111))
                .expectError(IbExceptions.TerminalError)
                .verify()
    }

    def "Cancel all should be completed for all limit order"() {
        given:
        def contract = createContractEUR()

        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(Duration.ofSeconds(10)).block()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(Duration.ofSeconds(10)).block()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(Duration.ofSeconds(10)).block()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(Duration.ofSeconds(10)).block()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(Duration.ofSeconds(10)).block()

        when:
        StepVerifier.create(client.cancelAll())
                .expectComplete()
                .verify(Duration.ofSeconds(10))

        then:
        assertThat(client.getCache().orders.values())
                .hasSize(5)
                .extracting({ it.getLastStatus() } as Function)
                .allMatch { IbOrderStatus status -> status.isCanceled() }
    }

    @Ignore("'Duplicate order id' error occurs. TODO: Make mechanism to assign IDs right before sending")
    def "Few placeOrder shouldn't interfere"() {
        given:
        def contract = createContractEUR()

        def placeInParallel = Flux.range(0, 10)
                .parallel(10)
                .runOn(Schedulers.elastic())
                .flatMap {
                    client.placeOrder(contract, createOrderStp(client.nextOrderId()))
                }

        expect:
        StepVerifier.create(placeInParallel)
                .expectNextCount(10)
                .verifyComplete()
    }

    def "Expect for states after order placing"() {
        given:
        def contract = createContractEUR()
        def order = createOrderStp()

        expect:
        StepVerifier.create(client.subscribeOnOrderNewStatus().log())
                .then { client.placeOrder(contract, order).block(Duration.ofSeconds(10)) }
                .assertNext { assert it.status == OrderStatus.PreSubmitted }
                .assertNext { assert it.status in [OrderStatus.Submitted, OrderStatus.Filled] }
                .thenCancel()
                .verify()
    }


    def "Request cannot be duplicated"() {

        expect:
        StepVerifier.create(Flux.merge(client.reqOpenOrders(), client.reqOpenOrders()))
                .expectError(IbExceptions.DuplicatedRequestError)
                .verify()
    }

    def "Request orders"() {
        given:
        def contract = createContractEUR()
        def order = createOrderStp()

        when:
        client.placeOrder(contract, order).block(Duration.ofSeconds(10))

        then:
        StepVerifier.create(client.reqOpenOrders())
                .expectNextCount(1)
                .verifyComplete()
    }

    def "Request orders when no orders exists"() {
        expect:
        StepVerifier.create(client.reqOpenOrders()
                            // Ignoring orders from previous tests if there were launched
                                    .filter { !it.lastStatus.isFilled() }
                                    .filter { !it.lastStatus.isCanceled() })
                .expectComplete()
                .verify(Duration.ofSeconds(10))
    }

    def "Request positions"() {
        expect:
        StepVerifier.create(client.subscribeOnPositionChange().skipWhile { it == IbPosition.COMPLETE }.skip(1), 1)
                .expectNextCount(1)
                .thenCancel()
                .verify()
    }

    def "Request positions for account"() {

        given:
        def contract = createContractEUR()

        expect:
        StepVerifier.create(client.subscribeOnPositionChange().skipUntil { it == IbPosition.COMPLETE }.skip(1), 1)
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(3))
                .then { client.placeOrder(contract, createOrderMkt(0, 1)).block(Duration.ofSeconds(10)) }
                .assertNext { IbPosition pos ->
                    assert pos.contract.conid() == contract.conid()
                }
                .thenCancel()
                .verify(Duration.ofSeconds(60))
    }

    def "Request PnL per contract"() {
        given:
        def contract = createContractEUR()

        expect:
        StepVerifier.create(client.subscribeOnContractPnl(contract.conid(), ACCOUNT))
                .then { client.placeOrder(contract, createOrderMkt(0, 1.0)).block(Duration.ofSeconds(10)) }
                .expectNextCount(1)
                .thenCancel()
                .verify()
    }

    def "Request PnL per account"() {

        given:
        def contract = createContractFB()
        def order = createOrderStp(0, 1.0, 1.0)

        expect:
        StepVerifier.create(client.subscribeOnAccountPnl(ACCOUNT))
                .then { client.placeOrder(contract, order).block(Duration.ofSeconds(10)) }
                .assertNext {
                    assert it.dailyPnL != 0
                }
                .thenCancel()
                .verify(Duration.ofSeconds(30))
    }

    def "Request for Portfolio change for account"() {

        given:
        def portfolioUpdates = client.subscribeOnAccountPortfolio(ACCOUNT)
                .skipUntil { it == IbPortfolio.COMPLETE }
                .skip(1)

        expect:
        StepVerifier.create(portfolioUpdates, 1)
                .expectNextCount(1)
                .thenCancel()
                .verify(Duration.ofMinutes(3)) // Account updates once per 3 min
    }

    def "Get market data"() {

        given:
        def contract = createContractEUR()

        expect:
        StepVerifier.create(client.subscribeOnMarketData(contract))
                .expectNextCount(10)
                .thenCancel()
                .verify()
    }

    def "Get market depth"() {
        given:
        def contract = createContractEUR()
        def rowNumber = 2

        expect:
        StepVerifier.create(client.subscribeOnMarketDepth(contract, rowNumber))
                .assertNext { IbMarketDepth level ->
                    level.position == 0
                    level.side == IbMarketDepth.Side.BUY
                    level.price > 0.0
                    level.size > 0
                }
                .assertNext { IbMarketDepth level ->
                    level.position == 1
                    level.side == IbMarketDepth.Side.BUY
                    level.price > 0.0
                    level.size > 0
                }
                .assertNext { IbMarketDepth level ->
                    level.position == 0
                    level.side == IbMarketDepth.Side.SELL
                    level.price > 0.0
                    level.size > 0
                }
                .assertNext { IbMarketDepth level ->
                    level.position == 1
                    level.side == IbMarketDepth.Side.SELL
                    level.price > 0.0
                    level.size > 0
                }
                .thenCancel()
                .verify()
    }

    @Ignore("To check contracts with L2 paid subscription is need and opened trading session. Use this test for respective account and run manually")
    def "Get market depth L2"() {

        given:
        def contract = createContractARRY_L2()
        def rowNumber = 2

        expect:
        StepVerifier.create(client.subscribeOnMarketDepth(contract, rowNumber))
                .assertNext { IbMarketDepth level ->
                    level.position == 0
                    level.side == IbMarketDepth.Side.BUY
                    level.price > 0.0
                    level.size > 0
                }
                .assertNext { IbMarketDepth level ->
                    level.position == 1
                    level.side == IbMarketDepth.Side.BUY
                    level.price > 0.0
                    level.size > 0
                }
                .assertNext { IbMarketDepth level ->
                    level.position == 0
                    level.side == IbMarketDepth.Side.SELL
                    level.price > 0.0
                    level.size > 0
                }
                .assertNext { IbMarketDepth level ->
                    level.position == 1
                    level.side == IbMarketDepth.Side.SELL
                    level.price > 0.0
                    level.size > 0
                }
                .thenCancel()
                .verify()
    }

    def "Request market depth information about exchanges"() {
        expect:
        StepVerifier.create(client.reqMktDepthExchanges())
                .expectNextCount(1)
                .thenCancel()
                .verify()
    }

    def "Few reconnects shouldn't impact to functionality"() {
        expect:

        (0..10).each {
            client.connect(TERMINAL_HOSTNAME, TERMINAL_PORT, 1).block(Duration.ofSeconds(20))
            assert client.isConnected()
            client.getCurrentTime().timeout(Duration.ofSeconds(10)).block()
            client.disconnect()
        }
    }

    def "Connection status should changes"() {

        given:
        client.disconnect()

        expect:
        StepVerifier.create(client.connectionStatus())
                .then {
                    client.connect(TERMINAL_HOSTNAME, TERMINAL_PORT, 1).block(Duration.ofSeconds(20))
                    client.disconnect()
                    client.connect(TERMINAL_HOSTNAME, TERMINAL_PORT, 1).block(Duration.ofSeconds(20))
                    client.disconnect()
                }
                .expectNext(true)
                .expectNext(false)
                .expectNext(true)
                .expectNext(false)
                .thenCancel()
                .verify()
    }

    def "Set market data type"() {
        expect:
        client.setMarketDataType(IbClient.MarketDataType.DELAYED_FROZEN)
    }

    def "Get contract market data snapshot"() {

        given:
        def contract = createContractEUR()

        expect:
        StepVerifier.create(client.reqMarketData(contract))
                .assertNext {
                    assert it.getBid() != null
                    assert it.getAsk() != null
                    assert it.getClosePrice() != null
                }
                .verifyComplete()
    }

    def "Get historical midpoints from last week"() {

        given:
        def contract = createContractEUR()
        def from = LocalDateTime.now().minusWeeks(1)

        expect:
        StepVerifier.create(client.reqHistoricalMidpoints(contract, from, null, null))
                .expectNextCount(999)
                .assertNext { tick ->
                    assert tick.price() > 0.0
                }
                .thenCancel()
                .verify()
    }

    def "Get historical bid and asks from last week"() {

        given:
        def contract = createContractEUR()
        def from = LocalDateTime.now().minusWeeks(1)

        expect:
        StepVerifier.create(client.reqHistoricalBidAsks(contract, from, null, null))
                .expectNextCount(1000)
                .assertNext { tick ->
                    assert tick.priceBid() > 0.0
                    assert tick.priceAsk() > 0.0
                    assert tick.sizeBid() > 0.0
                    assert tick.sizeAsk() > 0.0
                }
                .thenCancel()
        verifyAll {}
    }

    @Ignore("TWS returns error 'Failed to request historical ticks:No market data permissions for ISLAND STK' ")
    def "Get historical trades from last week"() {

        given:
        def contract = createContractFB()
        def from = LocalDateTime.now().minusWeeks(1)

        expect:
        StepVerifier.create(client.reqHistoricalTrades(contract, from, null, null))
                .expectNextCount(1000)
                .assertNext { tick ->
                    def tz = OffsetDateTime.now().getOffset()
                    assert Math.abs(from.toEpochSecond(tz) - tick.time()) < 100000
                    assert tick.price() > 0.0
                    assert tick.size() > 0.0
                }
                .thenCancel()
                .verify()
    }

    @Ignore("TWS returns error 'Requested market data is not subscribed' ")
    def "Request bars"() {

        given:
        def contract = createContractFB()
        def endDateTime = LocalDateTime.of(2019, 04, 16, 12, 0, 0)

        def reqHistoricalData = client.reqHistoricalData(contract, endDateTime,
                                                         120, IbClient.DurationUnit.Second, MIN_1,
                                                         IbClient.Type.TRADES,
                                                         IbClient.TradingHours.Within)

        expect:
        StepVerifier.create(reqHistoricalData)
                .assertNext { IbBar bar ->
                    assert bar.time == LocalDateTime.of(2019, 4, 15, 22, 58, 0)
                    assert bar.open == 179.59
                    assert bar.high == 179.6
                    assert bar.low == 179.54
                    assert bar.close == 179.57
                    assert bar.volume == 604
                    assert bar.count == 417
                    assert bar.wap == 179.566
                }
                .assertNext { IbBar bar ->
                    assert bar.time == LocalDateTime.of(2019, 4, 15, 22, 59, 0)
                    assert bar.open == 179.58
                    assert bar.high == 179.77
                    assert bar.low == 179.56
                    assert bar.close == 179.66
                    assert bar.volume == 1046
                    assert bar.count == 720
                    assert bar.wap == 179.685
                }
                .verifyComplete()
    }

    @Unroll
    def "Subscribe to unfinished bars for period #period"() {

        given:
        def subscribeOnHistoricalData = client.subscribeOnHistoricalData(createContractEUR(),
                                                                         30, IbClient.DurationUnit.Second,
                                                                         period,
                                                                         IbClient.Type.BID,
                                                                         IbClient.TradingHours.Within)
                .skipWhile { it == IbBar.COMPLETE }
                .skip(1)

        expect:
        StepVerifier.create(subscribeOnHistoricalData)
                .assertNext { IbBar bar ->
                    assert bar.open
                    assert bar.high
                    assert bar.low
                    assert bar.close
                }
                .thenCancel()
                .verify()

        where:
        period   | _
        SEC_5 | _
        SEC_10 | _
        SEC_15 | _
        SEC_30 | _
        MIN_1 | _
        MIN_2 | _
        MIN_3 | _
        MIN_5 | _
        MIN_10 | _
        MIN_15 | _
        MIN_20 | _
        MIN_30 | _
        HOUR_1 | _
        HOUR_2 | _
        HOUR_3 | _
        HOUR_4 | _
        HOUR_8 | _
        DAY_1 | _
        WEEK_1 | _
        MONTH_1 | _
    }

    def "Get all accounts summary after few calls"() {

        when:
        client.reqAccountSummary("All", "All").block()
        client.reqAccountSummary("All", "All").block()
        client.reqAccountSummary("All", "All").block()
        client.reqAccountSummary("All", "All").block()
        client.reqAccountSummary("All", "All").block()
        client.reqAccountSummary("All", "All").block()
        client.reqAccountSummary("All", "All").block()
        client.reqAccountSummary("All", "All").block()
        client.reqAccountSummary("All", "All").block()

        then:
        StepVerifier.create(client.reqAccountSummary("All", "All"))
                .assertNext { IbAccountsSummary summary ->

                    def value = summary.accounts[ACCOUNT]
                    assert value.accountType == "INDIVIDUAL"
                    assert value.netLiquidation > 0.0g
                    assert value.cushion > 0.0g
                    assert value.dayTradesRemaining == -1
                    assert value.lookAheadNextChange > new Date()
                    assert value.availableFunds != null
                    assert value.buyingPower != null
                    assert value.equityWithLoanValue != null
                    assert value.excessLiquidity != null
                    assert value.fullAvailableFunds != null
                    assert value.fullExcessLiquidity != null
                    assert value.fullInitMarginReq != null
                    assert value.fullMaintMarginReq != null
                    assert value.grossPositionValue != null
                    assert value.initMarginReq != null
                    assert value.lookAheadAvailableFunds != null
                    assert value.lookAheadExcessLiquidity != null
                    assert value.lookAheadInitMarginReq != null
                    assert value.lookAheadMaintMarginReq != null
                    assert value.maintMarginReq != null
                    assert value.netLiquidation != null
                    assert value.regTEquity != null
                    assert value.regTMargin != null
                    assert value.sma != null
                    assert value.totalCashValue != null

                    def summaryAUD = summary.accounts["All"].details["AUD"]
                    assert summaryAUD.cashBalance
                    assert summaryAUD.totalCashBalance != null
                    assert summaryAUD.accruedCash != null
                    assert summaryAUD.stockMarketValue != null
                    assert summaryAUD.optionMarketValue != null
                    assert summaryAUD.futureOptionValue != null
                    assert summaryAUD.futuresPNL != null
                    assert summaryAUD.netLiquidationByCurrency != null
                    assert summaryAUD.unrealizedPnL != null
                    assert summaryAUD.realizedPnL != null
                    assert summaryAUD.exchangeRate != null
                    assert summaryAUD.fundValue != null
                    assert summaryAUD.netDividend != null
                    assert summaryAUD.mutualFundValue != null
                    assert summaryAUD.moneyMarketFundValue != null
                    assert summaryAUD.corporateBondValue != null
                    assert summaryAUD.tBondValue != null
                    assert summaryAUD.tBillValue != null
                    assert summaryAUD.warrantValue != null
                    assert summaryAUD.fxCashBalance != null
                    assert summaryAUD.accountOrGroup == "All"
                    assert summaryAUD.issuerOptionValue != null

                    def summaryEUR = summary.accounts["All"].details["EUR"]
                    assert summaryEUR.cashBalance
                    assert summaryEUR.totalCashBalance != null
                    assert summaryEUR.accruedCash != null
                    assert summaryEUR.stockMarketValue != null
                    assert summaryEUR.optionMarketValue != null
                    assert summaryEUR.futureOptionValue != null
                    assert summaryEUR.futuresPNL != null
                    assert summaryEUR.netLiquidationByCurrency != null
                    assert summaryEUR.unrealizedPnL != null
                    assert summaryEUR.realizedPnL != null
                    assert summaryEUR.exchangeRate != null
                    assert summaryEUR.fundValue != null
                    assert summaryEUR.netDividend != null
                    assert summaryEUR.mutualFundValue != null
                    assert summaryEUR.moneyMarketFundValue != null
                    assert summaryEUR.corporateBondValue != null
                    assert summaryEUR.tBondValue != null
                    assert summaryEUR.tBillValue != null
                    assert summaryEUR.warrantValue != null
                    assert summaryEUR.fxCashBalance != null
                    assert summaryEUR.accountOrGroup == "All"
                    assert summaryEUR.issuerOptionValue != null
                }
                .verifyComplete()
    }

    def "Get all accounts summary after few calls 2"() {

        given:
        def params = { AccountsSummaryParams builder -> builder.withAllAccountGroups().inAllCurrencies().withAllTag() }

        when:
        client.reqAccountSummary(params)
        client.reqAccountSummary(params).block()
        client.reqAccountSummary(params).block()
        client.reqAccountSummary(params).block()
        client.reqAccountSummary(params).block()
        client.reqAccountSummary(params).block()
        client.reqAccountSummary(params).block()
        client.reqAccountSummary(params).block()
        client.reqAccountSummary(params).block()

        then:
        StepVerifier.create(client.reqAccountSummary(params))
                .assertNext { IbAccountsSummary summary ->

                    def value = summary.accounts[ACCOUNT]
                    assert value.accountType == "INDIVIDUAL"
                    assert value.netLiquidation > 0.0g
                    assert value.cushion > 0.0g
                    assert value.dayTradesRemaining == -1
                    assert value.lookAheadNextChange > new Date()
                    assert value.availableFunds != null
                    assert value.buyingPower != null
                    assert value.equityWithLoanValue != null
                    assert value.excessLiquidity != null
                    assert value.fullAvailableFunds != null
                    assert value.fullExcessLiquidity != null
                    assert value.fullInitMarginReq != null
                    assert value.fullMaintMarginReq != null
                    assert value.grossPositionValue != null
                    assert value.initMarginReq != null
                    assert value.lookAheadAvailableFunds != null
                    assert value.lookAheadExcessLiquidity != null
                    assert value.lookAheadInitMarginReq != null
                    assert value.lookAheadMaintMarginReq != null
                    assert value.maintMarginReq != null
                    assert value.netLiquidation != null
                    assert value.regTEquity != null
                    assert value.regTMargin != null
                    assert value.sma != null
                    assert value.totalCashValue != null

                    def summaryAUD = summary.accounts["All"].details["AUD"]
                    assert summaryAUD.cashBalance
                    assert summaryAUD.totalCashBalance != null
                    assert summaryAUD.accruedCash != null
                    assert summaryAUD.stockMarketValue != null
                    assert summaryAUD.optionMarketValue != null
                    assert summaryAUD.futureOptionValue != null
                    assert summaryAUD.futuresPNL != null
                    assert summaryAUD.netLiquidationByCurrency != null
                    assert summaryAUD.unrealizedPnL != null
                    assert summaryAUD.realizedPnL != null
                    assert summaryAUD.exchangeRate != null
                    assert summaryAUD.fundValue != null
                    assert summaryAUD.netDividend != null
                    assert summaryAUD.mutualFundValue != null
                    assert summaryAUD.moneyMarketFundValue != null
                    assert summaryAUD.corporateBondValue != null
                    assert summaryAUD.tBondValue != null
                    assert summaryAUD.tBillValue != null
                    assert summaryAUD.warrantValue != null
                    assert summaryAUD.fxCashBalance != null
                    assert summaryAUD.accountOrGroup == "All"
                    assert summaryAUD.issuerOptionValue != null

                    def summaryEUR = summary.accounts["All"].details["EUR"]
                    assert summaryEUR.cashBalance
                    assert summaryEUR.totalCashBalance != null
                    assert summaryEUR.accruedCash != null
                    assert summaryEUR.stockMarketValue != null
                    assert summaryEUR.optionMarketValue != null
                    assert summaryEUR.futureOptionValue != null
                    assert summaryEUR.futuresPNL != null
                    assert summaryEUR.netLiquidationByCurrency != null
                    assert summaryEUR.unrealizedPnL != null
                    assert summaryEUR.realizedPnL != null
                    assert summaryEUR.exchangeRate != null
                    assert summaryEUR.fundValue != null
                    assert summaryEUR.netDividend != null
                    assert summaryEUR.mutualFundValue != null
                    assert summaryEUR.moneyMarketFundValue != null
                    assert summaryEUR.corporateBondValue != null
                    assert summaryEUR.tBondValue != null
                    assert summaryEUR.tBillValue != null
                    assert summaryEUR.warrantValue != null
                    assert summaryEUR.fxCashBalance != null
                    assert summaryEUR.accountOrGroup == "All"
                    assert summaryEUR.issuerOptionValue != null
                }
                .verifyComplete()
    }

    def "Get only base currency accounts summary details"() {
        when:
        def result = client.reqAccountSummary { builder ->
            builder.withAllAccountGroups().inBaseCurrency().withAllTag()
        }.block()

        then:
        result.accounts.size() == 1
        def account = result.accounts[ACCOUNT]
        account

        def baseDetails = account.details.get("BASE")
        baseDetails
        baseDetails.realCurrency == "USD"
    }

    def "reqMarketRule should return list of price increments"() {

        expect:
        StepVerifier.create(client.reqMarketRule(239))
                .assertNext { value ->
                    assert value.lowEdge() == 0.0
                    assert value.increment() == 0.00005
                }
                .verifyComplete()
    }

    private static def createContractEUR() {
        def contract = new Contract()
        contract.conid(12087792)
        contract.symbol("EUR")
        contract.currency("USD")
        contract.exchange("IDEALPRO")
        contract.secType(Types.SecType.CASH)
        return contract
    }

    private static def createContractFB() {
        def contract = new Contract()
        contract.conid(107113386)
        contract.symbol("FB")
        contract.currency("USD")
        contract.exchange("SMART")
        contract.secType(Types.SecType.STK)
        return contract
    }

    private static def createOrderStp(int id = 0, double auxPrice = 1.0, double quantity = 20000.0f) {
        def order = new Order()
        order.orderId(id)
        order.action("BUY")
        order.orderType(OrderType.STP)
        order.auxPrice(auxPrice)
        order.tif("GTC")
        order.totalQuantity(quantity)
        return order
    }

    private static def createOrderMkt(int id = 0, double quantity = 20000.0f) {
        def order = new Order()
        order.orderId(id)
        order.action("BUY")
        order.orderType(OrderType.MKT)
        order.totalQuantity(quantity)
        return order
    }

    private static def createContractARRY_L2() {
        def contract = new Contract()
        contract.conid(10831568)
        contract.symbol("ARRY")
        contract.currency("USD")
        contract.exchange("ISLAND")
        contract.secType(Types.SecType.STK)
        return contract
    }
}
