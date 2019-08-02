package com.finplant.ib

import com.finplant.ib.types.IbBar
import com.finplant.ib.types.IbContractDescription
import com.finplant.ib.types.IbExecutionReport
import com.finplant.ib.types.IbMarketDepth
import com.finplant.ib.types.IbPortfolio
import com.finplant.ib.types.IbPosition
import com.ib.client.*
import io.reactivex.functions.Predicate
import io.reactivex.observers.BaseTestConsumer
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import static com.finplant.ib.IbClient.BarSize.*
import static org.assertj.core.api.Assertions.assertThat

class IbClientTest extends Specification {

    IbClient client = new IbClient()

    void setup() {
        def options = new IbClientOptions().connectionDelay(Duration.ofSeconds(1))
        client = new IbClient(options)
        assert client.connect("127.0.0.1", 7496, 0).blockingAwait(3000, TimeUnit.SECONDS)
//        client.cancelAll().timeout(10, TimeUnit.SECONDS).blockingGet()
//        closeAllPositions()
    }

    void closeAllPositions() {
        client.subscribeOnPositionChange()
                .takeUntil({ it == IbPosition.COMPLETE } as Predicate)
                .filter { it.pos != BigDecimal.ZERO }
                .toList()
                .blockingGet()
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

        client.placeOrder(position.contract, order).blockingGet()

        println("Close position: $position")
    }

    void cleanup() {
        if (client) {
//            client.cancelAll().blockingGet()
            client.close()
        }
    }

    def "smoke"() {
        expect:
        client.isConnected()
    }

    def "Call reqCurrentTime is OK"() {
        when:
        long time = client.getCurrentTime().blockingGet()

        then:
        time > 1510320971
    }

    def "Call reqContractDetails is OK"() {
        given:
        def contract = new Contract()
        contract.symbol("EUR")
        contract.currency("USD")
        contract.secType(Types.SecType.CASH)

        when:
        def list = client.reqContractDetails(contract).toList().blockingGet()

        then:
        println(list)
        list.size() > 0
    }

    def "Requesting matching symbols for FB should return valid list"() {

        when:
        def observer = client.reqMatchingSymbols("FB").test()

        then:
        observer.awaitTerminalEvent(10, TimeUnit.SECONDS)
        observer.assertNoErrors()
        observer.valueCount() >= 2
        observer.values()[0].with { IbContractDescription description ->
            assert description.contract.symbol == "FB"
            assert description.contract.secType == Types.SecType.STK
            assert description.contract.primaryExchange == "MEXI"
            assert description.derivativeSecTypes.isEmpty()
        }
        observer.values()[1].with { IbContractDescription description ->
            assert description.contract.symbol == "FB"
            assert description.contract.secType == Types.SecType.STK
            assert description.contract.primaryExchange == "NASDAQ.NMS"
            assert description.derivativeSecTypes == [Types.SecType.CFD, Types.SecType.OPT, Types.SecType.IOPT,
                                                      Types.SecType.WAR, Types.SecType.FUT]
        }
    }

    def "Call placeOrder is OK"() {
        given:
        def contract = createContractEUR()
        def order = createOrderStp()

        when:
        def single = client.placeOrder(contract, order).test()

        then:
        single.awaitTerminalEvent(10, TimeUnit.SECONDS)
        single.assertNoErrors()
        single.assertComplete()
        single.assertValueAt 0, { it.orderId > 0 } as Predicate
    }

    def "Call placeOrder with wrong data should raise error"() {
        given:
        def contract = new Contract()
        def order = new Order()

        when:
        def single = client.placeOrder(contract, order).test()

        then:
        single.awaitTerminalEvent(10, TimeUnit.SECONDS)
        single.assertError(IbExceptions.TerminalError.class)
    }

    def "Cancel request should be completed for limit order"() {
        given:
        def order = createOrderStp()
        order.auxPrice(10000)

        def ibOrder = client.placeOrder(createContractEUR(), order).blockingGet()

        when:
        def observer = client.cancelOrder(ibOrder.orderId).test()

        then:
        observer.awaitTerminalEvent(10, TimeUnit.SECONDS)
        observer.assertNoErrors()
    }

    def "Cancel request should return error if order already has been filled"() {
        given:
        def order = createOrderStp()
        client.placeOrder(createContractEUR(), order).flatMap({ ibOrder ->
            client.subscribeOnOrderNewStatus()
                    .filter({ status -> status.orderId == order.orderId() })
                    .filter({ status -> status.isFilled() })
                    .timeout(20, TimeUnit.SECONDS)
                    .firstOrError()
        }).blockingGet()

        when:
        def observer = client.cancelOrder(order.orderId()).test()

        then:
        observer.awaitTerminalEvent(10, TimeUnit.SECONDS)
        observer.assertError(IbExceptions.OrderAlreadyFilledError.class)
    }

    def "Await execution report after fill"() {
        given:
        def observer = client.subscribeOnExecutionReport().take(1).test()

        def order = createOrderStp()
        client.placeOrder(createContractEUR(), order).flatMap({ ibOrder ->
            client.subscribeOnOrderNewStatus()
                    .filter({ status -> status.orderId == order.orderId() })
                    .filter({ status -> status.isFilled() })
                    .timeout(20, TimeUnit.SECONDS)
                    .firstOrError()
        }).blockingGet()

        expect:
        observer.awaitTerminalEvent(30, TimeUnit.SECONDS)
        observer.values()[0].with { IbExecutionReport report ->
            assert report.execution.orderId > 0
            assert report.execution.execId == report.commission.execId
            assert report.commission.commission > 0.0
        }
    }

    def "Cancel request should raise error if order doesn't exists"() {
        when:
        def observer = client.cancelOrder(1111111111).test()

        then:
        observer.awaitTerminalEvent(10, TimeUnit.SECONDS)
        observer.assertError(IbExceptions.TerminalError.class)
    }

    def "Cancel all should be completed for all limit order"() {
        given:
        def contract = createContractEUR()

        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(1, TimeUnit.SECONDS).blockingGet()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(1, TimeUnit.SECONDS).blockingGet()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(1, TimeUnit.SECONDS).blockingGet()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(1, TimeUnit.SECONDS).blockingGet()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(1, TimeUnit.SECONDS).blockingGet()
        client.placeOrder(contract, createOrderStp(0, 1000.0)).timeout(1, TimeUnit.SECONDS).blockingGet()

        when:
        def observer = client.cancelAll().test()

        then:
        observer.awaitTerminalEvent(10, TimeUnit.SECONDS)
        observer.assertNoErrors()

        client.getCache().orders.values().forEach { assert it.lastStatus.isCanceled() }
    }


    def "Few placeOrder shouldn't interfere"() {
        given:
        def contract = createContractEUR()

        when:
        def single1 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single2 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single3 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single4 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single5 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single6 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single7 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single8 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single9 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def single10 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))

        then:
        single1.blockingGet()
        single2.blockingGet()
        single3.blockingGet()
        single4.blockingGet()
        single5.blockingGet()
        single6.blockingGet()
        single7.blockingGet()
        single8.blockingGet()
        single9.blockingGet()
        single10.blockingGet()
    }

    def "Expect for states after placeOrder"() {
        given:
        def contract = createContractEUR()
        def order = createOrderStp()
        def observer = client.subscribeOnOrderNewStatus().test()

        when:
        client.placeOrder(contract, order).blockingGet()

        then:
        observer.awaitCount(2)
        observer.assertNotComplete()
        observer.assertNoErrors()
        observer.assertValueAt 0, { it.status == OrderStatus.PreSubmitted } as Predicate
        observer.assertValueAt 1, { it.status in [OrderStatus.Submitted, OrderStatus.Filled] } as Predicate
    }


    def "Request cannot be duplicated"() {
        when:
        def observable = client.reqOpenOrders()
                .mergeWith(client.reqOpenOrders())
                .mergeWith(client.reqOpenOrders())
                .mergeWith(client.reqOpenOrders())
                .mergeWith(client.reqOpenOrders())
        observable.blockingLast()

        then:
        IbExceptions.DuplicatedRequestError ex = thrown()
    }

    def "Request orders"() {
        given:
        def contract = createContractEUR()
        def order = createOrderStp()

        when:
        client.placeOrder(contract, order).blockingGet()
        def list = client.reqOpenOrders().timeout(3, TimeUnit.SECONDS).toList().blockingGet()

        then:
        println list
        list.size() > 0
    }

    def "Request orders when no orders exists"() {
        when:
        def list = client.reqOpenOrders().timeout(3, TimeUnit.SECONDS).toList().blockingGet()

        then:
        list.isEmpty()
    }

    def "Request positions"() {
        when:
        def positions = client.subscribeOnPositionChange("DU22993")
                .takeUntil({ it == IbPosition.COMPLETE } as Predicate)
                .toList()
                .timeout(3, TimeUnit.SECONDS)
                .blockingGet()

        then:
        positions
        positions.size() > 0
    }

    def "Request positions for account"() {
        given:
        def contract = createContractGC()

        when:
        def observer = client.subscribeOnPositionChange("DU22993")//.observeOn(Schedulers.newThread())
                .skipWhile { it.valid }
                .filter { it.pos != 0.0 }
                .take(1)
                .test()
        client.placeOrder(contract, createOrderMkt(0, 1.0)).blockingGet()

        then:
        observer.assertNoErrors()
        assertThat(observer.values()).hasSize(1).anyMatch { it.pos == 1.0 && it.contract.conid() == contract.conid() }
    }

    def "Request PnL per contract"() {
        given:
        def contract = createContractGC()

        when:
        client.placeOrder(contract, createOrderMkt(0, 1.0)).blockingGet()
        def observer = client.subscribeOnContractPnl(contract.conid(), "DU22993").test()

        then:
        observer.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1000MS)
        observer.assertNoErrors()
        observer.assertNotComplete()
        observer.assertValueCount(1)
        observer.assertValueAt 0, { it.positionId > 0 } as Predicate
    }

    def "Request PnL per account"() {
        when:
        def observer = client.subscribeOnAccountPnl("DU22993").test()

        client.placeOrder(createContractGC(), createOrderStp(0, 1.0, 1.0)).blockingGet()

        then:
        observer.awaitCount(1, { sleep(30_000) })
        observer.assertNoErrors()
        observer.assertNotComplete()
        observer.assertValueCount(1)
        observer.assertValueAt 0, { it.dailyPnL != 0 } as Predicate
    }

    def "Request for Portfolio change for account"() {
        when:
        def portfolio = client.subscribeOnAccountPortfolio("DU22993")
                .takeUntil({ it == IbPortfolio.COMPLETE } as Predicate)
                .toList()
                .timeout(3, TimeUnit.MINUTES) // Account updates once per 3 min
                .blockingGet()

        then:
        portfolio.size() > 0
    }

    def "Get market data"() {
        when:
        def testObserver = client.subscribeOnMarketData(createContractEUR()).test()

        then:
        testObserver.awaitCount(10)
        testObserver.assertNoErrors()
        testObserver.assertNotComplete()
        testObserver.assertValueAt 0, { it.closePrice > 0 } as Predicate
    }

    def "Get market depth"() {
        given:
        def contract = createContractEUR()

        when:
        def observer = client.subscribeOnMarketDepth(contract, 2).test()

        then:
        observer.awaitCount(4, BaseTestConsumer.TestWaitStrategy.SLEEP_100MS, 5000)
        observer.assertNoErrors()
        observer.assertValueAt 0, { IbMarketDepth level ->
            level.position == 0
            level.side == IbMarketDepth.Side.BUY
            level.price > 0.0
            level.size > 0
        } as Predicate

        observer.assertValueAt 1, { IbMarketDepth level ->
            level.position == 1
            level.side == IbMarketDepth.Side.BUY
            level.price > 0.0
            level.size > 0
        } as Predicate

        observer.assertValueAt 0, { IbMarketDepth level ->
            level.position == 0
            level.side == IbMarketDepth.Side.SELL
            level.price > 0.0
            level.size > 0
        } as Predicate

        observer.assertValueAt 1, { IbMarketDepth level ->
            level.position == 1
            level.side == IbMarketDepth.Side.SELL
            level.price > 0.0
            level.size > 0
        } as Predicate
    }

    @Ignore("To check contracts with L2 paid subscription is need and opened trading session. Use this test for respective account and run manually")
    def "Get market depth L2"() {
        given:
        def contract = createContractARRY_L2()

        when:
        def observer = client.subscribeOnMarketDepth(contract, 2).test()

        then:
        observer.awaitCount(4, BaseTestConsumer.TestWaitStrategy.SLEEP_100MS, 5000)
        observer.assertNoErrors()
        observer.assertValueAt 0, { IbMarketDepth level ->
            level.position == 0
            level.side == IbMarketDepth.Side.BUY
            level.price > 0.0
            level.size > 0
        } as Predicate

        observer.assertValueAt 1, { IbMarketDepth level ->
            level.position == 1
            level.side == IbMarketDepth.Side.BUY
            level.price > 0.0
            level.size > 0
        } as Predicate

        observer.assertValueAt 0, { IbMarketDepth level ->
            level.position == 0
            level.side == IbMarketDepth.Side.SELL
            level.price > 0.0
            level.size > 0
        } as Predicate

        observer.assertValueAt 1, { IbMarketDepth level ->
            level.position == 1
            level.side == IbMarketDepth.Side.SELL
            level.price > 0.0
            level.size > 0
        } as Predicate
    }

    def "Request market depth information about exchanges"() {
        when:
        def observer = client.reqMktDepthExchanges().test()

        then:
        observer.awaitTerminalEvent(10, TimeUnit.SECONDS)
        observer.assertNoErrors()
        observer.awaitCount(1)
    }

    def "Request orders and place orders shouldn't interfere each other"() {
        given:
        def contract = createContractEUR()

        when:
        def future1 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        client.reqOpenOrders().timeout(10, TimeUnit.SECONDS).blockingSubscribe()
        def future2 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def future3 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        client.reqOpenOrders().timeout(10, TimeUnit.SECONDS).blockingSubscribe()
        def future4 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def future5 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def future6 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))

        and:

        def order1 = future1.timeout(10, TimeUnit.SECONDS).blockingGet()
        def order2 = future2.timeout(10, TimeUnit.SECONDS).blockingGet()
        def order3 = future3.timeout(10, TimeUnit.SECONDS).blockingGet()
        def order4 = future4.timeout(10, TimeUnit.SECONDS).blockingGet()
        def order5 = future5.timeout(10, TimeUnit.SECONDS).blockingGet()
        def list1 = client.reqOpenOrders().timeout(10, TimeUnit.SECONDS).toList().blockingGet()
        def order6 = future6.timeout(10, TimeUnit.SECONDS).blockingGet()
        def list2 = client.reqOpenOrders().timeout(10, TimeUnit.SECONDS).toList().blockingGet()


        then:
        list1.size() <= 6
        list1.collect { it.orderId }.toList().containsAll(
                [order1.getOrderId(),
                 order2.getOrderId(),
                 order3.getOrderId(),
                 order4.getOrderId(),
                 order5.getOrderId()])

        list2.size() == 6
        list2.collect { it.orderId }.toList().containsAll(
                [order1.getOrderId(),
                 order2.getOrderId(),
                 order3.getOrderId(),
                 order4.getOrderId(),
                 order5.getOrderId(),
                 order6.getOrderId()])

        list2.collect { it.orderId }.toList().containsAll(
                [order1.getOrderId(),
                 order2.getOrderId(),
                 order3.getOrderId(),
                 order4.getOrderId(),
                 order5.getOrderId(),
                 order6.getOrderId()])
    }

    def "Few reconnects shouldn't impact to functionality"() {
        expect:

        (0..10).each {
            client.connect("127.0.0.1", 7497, 1).blockingGet(20, TimeUnit.SECONDS)
            assert client.isConnected()
            client.getCurrentTime().timeout(10, TimeUnit.SECONDS).blockingGet() > 1510320971
            client.disconnect()
        }
    }

    def "Connection status should changes"() {
        given:
        client.disconnect()
        def observable = client.connectionStatus().test()

        when:
        client.connect("127.0.0.1", 7497, 1).blockingGet(20, TimeUnit.SECONDS)
        client.disconnect()
        client.connect("127.0.0.1", 7497, 1).blockingGet(20, TimeUnit.SECONDS)
        client.disconnect()

        then:
        observable.awaitCount(4)
        observable.assertValueAt(0, true)
        observable.assertValueAt(1, false)
        observable.assertValueAt(2, true)
        observable.assertValueAt(3, false)
    }

    def "Set market data type"() {
        expect:
        client.setMarketDataType(IbClient.MarketDataType.DELAYED_FROZEN)
    }

    def "Get contract snapshot"() {
        when:
        def observer = client.reqMarketData(createContractEUR()).test()

        then:
        observer.awaitDone(30, TimeUnit.SECONDS)
        observer.assertValueCount(1)
        observer.assertNoErrors()
        observer.assertValueAt 0, { tick ->
            assert tick.getBid() != null
            assert tick.getAsk() != null
            assert tick.getClosePrice() != null
            true
        } as Predicate
    }

    def "Get historical midpoints from last week"() {

        given:
        def from = LocalDateTime.now().minusWeeks(1)

        when:
        def observer = client.reqHistoricalMidpoints(createContractEUR(), from, null, null).test()

        then:
        observer.awaitDone(10, TimeUnit.SECONDS)
        observer.assertNoErrors()
        observer.assertComplete()
        observer.valueCount() >= 1000
        observer.assertValueAt 0, { tick ->
            assert tick.price() > 0.0
            true
        } as Predicate
    }

    def "Get historical bid and asks from last week"() {

        given:
        def from = LocalDateTime.now().minusWeeks(1)

        when:
        def observer = client.reqHistoricalBidAsks(createContractEUR(), from, null, null).test()

        then:
        observer.awaitDone(10, TimeUnit.SECONDS)
        observer.assertNoErrors()
        observer.assertComplete()
        observer.valueCount() >= 1000
        observer.assertValueAt 0, { tick ->
            assert tick.priceBid() > 0.0
            assert tick.priceAsk() > 0.0
            assert tick.sizeBid() > 0.0
            assert tick.sizeAsk() > 0.0
            true
        } as Predicate
    }

    def "Get historical trades from last week"() {

        given:
        def from = LocalDateTime.now().minusWeeks(1)

        when:
        def observer = client.reqHistoricalTrades(createContractFB(), from, null, null).test()

        then:
        observer.awaitDone(10, TimeUnit.SECONDS)
        observer.assertNoErrors()
        observer.assertComplete()
        observer.valueCount() >= 1000
        observer.assertValueAt 0, { tick ->
            def tz = OffsetDateTime.now().getOffset()
            assert Math.abs(from.toEpochSecond(tz) - tick.time()) < 100000
            assert tick.price() > 0.0
            assert tick.size() > 0.0
            true
        } as Predicate
    }

    def "Request bars"() {

        given:
        def endDateTime = LocalDateTime.of(2019, 04, 16, 12, 0, 0);

        when:
        def observer = client.reqHistoricalData(createContractFB(), endDateTime,
                                                120, IbClient.DurationUnit.Second, _1_min,
                                                IbClient.Type.TRADES,
                                                IbClient.TradingHours.Within).test()
        then:
        observer.awaitDone(10, TimeUnit.SECONDS)
        observer.assertNoErrors()
        observer.assertComplete()
        observer.valueCount() == 2
        observer.assertValueAt 0, { IbBar bar ->
            assert bar.time == LocalDateTime.of(2019, 4, 15, 22, 58, 0)
            assert bar.open == 179.59
            assert bar.high == 179.6
            assert bar.low == 179.54
            assert bar.close == 179.57
            assert bar.volume == 604
            assert bar.count == 417
            assert bar.wap == 179.566
            true
        } as Predicate
        observer.assertValueAt 1, { IbBar bar ->
            assert bar.time == LocalDateTime.of(2019, 4, 15, 22, 59, 0)
            assert bar.open == 179.58
            assert bar.high == 179.77
            assert bar.low == 179.56
            assert bar.close == 179.66
            assert bar.volume == 1046
            assert bar.count == 720
            assert bar.wap == 179.685
            true
        } as Predicate
    }

    @Unroll
    def "Subscribe to unfinished bars for period #period"() {

        when:
        def observer = client.subscribeOnHistoricalData(createContractEUR(),
                                                        30, IbClient.DurationUnit.Second,
                                                        period,
                                                        IbClient.Type.BID,
                                                        IbClient.TradingHours.Within)
                .skipWhile { it == IbBar.COMPLETE }
                .filter { it != IbBar.COMPLETE }
                .take(1).test()

        then:
        observer.awaitCount(1)
        observer.assertNoErrors()
        observer.assertComplete()
        observer.valueCount() == 1
        observer.assertValueAt 0, { IbBar bar ->
            assert bar.open > 0.0
            assert bar.high > 0.0
            assert bar.low > 0.0
            assert bar.close > 0.0
            true
        } as Predicate

        where:
        period   | _
        _5_sec   | _
        _10_sec  | _
        _15_sec  | _
        _30_sec  | _
        _1_min   | _
        _2_min   | _
        _3_min   | _
        _5_min   | _
        _10_min  | _
        _15_min  | _
        _20_min  | _
        _30_min  | _
        _1_hour  | _
        _2_hour  | _
        _3_hour  | _
        _4_hour  | _
        _8_hour  | _
        _1_day   | _
        _1_week  | _
        _1_month | _
    }

    def "Get all accounts summary after few calls"() {
        when:
        client.reqAccountSummary("All", "All").blockingGet()
        client.reqAccountSummary("All", "All").blockingGet()
        client.reqAccountSummary("All", "All").blockingGet()
        client.reqAccountSummary("All", "All").blockingGet()
        client.reqAccountSummary("All", "All").blockingGet()
        client.reqAccountSummary("All", "All").blockingGet()
        client.reqAccountSummary("All", "All").blockingGet()
        client.reqAccountSummary("All", "All").blockingGet()
        client.reqAccountSummary("All", "All").blockingGet()

        def observer = client.reqAccountSummary("All", "All").test()

        then:
        observer.awaitTerminalEvent()
        observer.assertNoErrors()
        observer.assertValueCount(1)

        def value = observer.values()[0]
        value.accountType == "INDIVIDUAL"
        value.netLiquidation > 0.0g
        value.cushion > 0.0g
        value.dayTradesRemaining == -1
        value.lookAheadNextChange > new Date()
        value.accruedCash != null
        value.availableFunds != null
        value.buyingPower != null
        value.equityWithLoanValue != null
        value.excessLiquidity != null
        value.fullAvailableFunds != null
        value.fullExcessLiquidity != null
        value.fullInitMarginReq != null
        value.fullMaintMarginReq != null
        value.grossPositionValue != null
        value.initMarginReq != null
        value.lookAheadAvailableFunds != null
        value.lookAheadExcessLiquidity != null
        value.lookAheadInitMarginReq != null
        value.lookAheadMaintMarginReq != null
        value.maintMarginReq != null
        value.netLiquidation != null
        value.regTEquity != null
        value.regTMargin != null
        value.SMA != null
        value.totalCashValue != null

        def summaryAUD = value.details["AUD"]
        summaryAUD.cashBalance
        summaryAUD.totalCashBalance != null
        summaryAUD.accruedCash != null
        summaryAUD.stockMarketValue != null
        summaryAUD.optionMarketValue != null
        summaryAUD.futureOptionValue != null
        summaryAUD.futuresPNL != null
        summaryAUD.netLiquidationByCurrency != null
        summaryAUD.unrealizedPnL != null
        summaryAUD.realizedPnL != null
        summaryAUD.exchangeRate != null
        summaryAUD.fundValue != null
        summaryAUD.netDividend != null
        summaryAUD.mutualFundValue != null
        summaryAUD.moneyMarketFundValue != null
        summaryAUD.corporateBondValue != null
        summaryAUD.tBondValue != null
        summaryAUD.tBillValue != null
        summaryAUD.warrantValue != null
        summaryAUD.fxCashBalance != null
        summaryAUD.accountOrGroup == "All"
        summaryAUD.issuerOptionValue != null

        def summaryEUR = value.details["EUR"]
        summaryEUR.cashBalance
        summaryEUR.totalCashBalance != null
        summaryEUR.accruedCash != null
        summaryEUR.stockMarketValue != null
        summaryEUR.optionMarketValue != null
        summaryEUR.futureOptionValue != null
        summaryEUR.futuresPNL != null
        summaryEUR.netLiquidationByCurrency != null
        summaryEUR.unrealizedPnL != null
        summaryEUR.realizedPnL != null
        summaryEUR.exchangeRate != null
        summaryEUR.fundValue != null
        summaryEUR.netDividend != null
        summaryEUR.mutualFundValue != null
        summaryEUR.moneyMarketFundValue != null
        summaryEUR.corporateBondValue != null
        summaryEUR.tBondValue != null
        summaryEUR.tBillValue != null
        summaryEUR.warrantValue != null
        summaryEUR.fxCashBalance != null
        summaryEUR.accountOrGroup == "All"
        summaryEUR.issuerOptionValue != null
    }

    def "Get only base currency accounts summary details"() {
        given:
        def accountName = client.getManagedAccounts()[0]

        when:
        def result = client.reqAccountSummary("All", null).blockingGet()

        then:
        result.accounts.size() == 1
        def account = result.accounts[accountName]
        account

        def baseDetails = account.details.get("BASE")
        baseDetails
        baseDetails.realCurrency == "USD"
    }

    def "reqMarketRule should return list of price increments"() {
        when:
        def observer = client.reqMarketRule(791).test()

        then:
        observer.awaitTerminalEvent()
        observer.assertNoErrors()
        observer.assertValueCount(11)

        observer.assertValueAt 0, { value ->
            assert value.lowEdge() == 0.0
            assert value.increment() == 1.0
            return true
        } as Predicate
        observer.assertValueAt 1, { value ->
            assert value.lowEdge() == 3000.0
            assert value.increment() == 5.0
            return true
        } as Predicate
        observer.assertValueAt 2, { value ->
            assert value.lowEdge() == 5000.0
            assert value.increment() == 10.0
            return true
        } as Predicate
        observer.assertValueAt 3, { value ->
            assert value.lowEdge() == 30000.0
            assert value.increment() == 50.0
            return true
        } as Predicate
        observer.assertValueAt 4, { value ->
            assert value.lowEdge() == 50000.0
            assert value.increment() == 100.0
            return true
        } as Predicate
        observer.assertValueAt 5, { value ->
            assert value.lowEdge() == 300000.0
            assert value.increment() == 500.0
            return true
        } as Predicate
        observer.assertValueAt 6, { value ->
            assert value.lowEdge() == 500000.0
            assert value.increment() == 1000.0
            return true
        } as Predicate
        observer.assertValueAt 7, { value ->
            assert value.lowEdge() == 3000000.0
            assert value.increment() == 5000.0
            return true
        } as Predicate
        observer.assertValueAt 8, { value ->
            assert value.lowEdge() == 5000000.0
            assert value.increment() == 10000.0
            return true
        } as Predicate
        observer.assertValueAt 9, { value ->
            assert value.lowEdge() == 3.0E7
            assert value.increment() == 50000.0
            return true
        } as Predicate
        observer.assertValueAt 10, { value ->
            assert value.lowEdge() == 5.0E7
            assert value.increment() == 100000.0
            return true
        } as Predicate
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

    private static def createContractGC() {
        def contract = new Contract()
        contract.conid(130450913)
        contract.symbol("GC")
        contract.currency("USD")
        contract.exchange("NYMEX")
        contract.secType(Types.SecType.FUT)
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
