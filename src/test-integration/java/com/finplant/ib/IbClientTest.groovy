package com.finplant.ib

import com.finplant.ib.impl.types.IbMarketDepth
import com.finplant.ib.impl.types.IbPortfolio
import com.finplant.ib.impl.types.IbPosition
import com.ib.client.*
import io.reactivex.functions.Predicate
import io.reactivex.observers.BaseTestConsumer
import spock.lang.Ignore
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import static org.assertj.core.api.Assertions.assertThat

class IbClientTest extends Specification {

    IbClient client = new IbClient()

    void setup() {
        client = new IbClient()
        assert client.connect("127.0.0.1", 7497, 2).blockingAwait(30, TimeUnit.SECONDS)
        client.cancelAll().timeout(10, TimeUnit.SECONDS).blockingGet()
        closeAllPositions()
    }

    void closeAllPositions() {
        client.subscribeOnPositionChange()
                .takeUntil({ it == IbPosition.COMPLETE } as Predicate)
                .filter { it.pos != BigDecimal.ZERO }
                .blockingSubscribe { position -> closePosition(position) }
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
            client.cancelAll().blockingGet()
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

    def "Cancel request should be completed even order was filled"() {
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
        observer.assertNoErrors()
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

        client.getCache().orders.values().forEach { assert it.lastStatuses.isCanceled() }
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
        def observable = client.reqAllOpenOrders()
                .mergeWith(client.reqAllOpenOrders())
                .mergeWith(client.reqAllOpenOrders())
                .mergeWith(client.reqAllOpenOrders())
                .mergeWith(client.reqAllOpenOrders())
        observable.blockingLast()

        then:
        IbExceptions.DuplicatedRequest ex = thrown()
    }

    def "Request orders"() {
        given:
        def contract = createContractEUR()

        when:
        client.placeOrder(contract, createOrderStp(client.nextOrderId())).blockingGet()
        def list = client.reqAllOpenOrders().timeout(3, TimeUnit.SECONDS).blockingGet()

        then:
        println list
        list.size() > 0
    }

    def "Request orders when no orders exists"() {
        when:
        def list = client.reqAllOpenOrders().timeout(3, TimeUnit.SECONDS).blockingGet()

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
        observer.awaitCount(1, { sleep(5000) })
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
        def observer = client.getMarketDepth(contract, 2).test()

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
        def observer = client.getMarketDepth(contract, 2).test()

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
        client.reqAllOpenOrders().timeout(10, TimeUnit.SECONDS).blockingGet()
        def future2 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def future3 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        client.reqAllOpenOrders().timeout(10, TimeUnit.SECONDS).blockingGet()
        def future4 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def future5 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))
        def future6 = client.placeOrder(contract, createOrderStp(client.nextOrderId()))

        and:

        def order1 = future1.timeout(10, TimeUnit.SECONDS).blockingGet()
        def order2 = future2.timeout(10, TimeUnit.SECONDS).blockingGet()
        def order3 = future3.timeout(10, TimeUnit.SECONDS).blockingGet()
        def order4 = future4.timeout(10, TimeUnit.SECONDS).blockingGet()
        def order5 = future5.timeout(10, TimeUnit.SECONDS).blockingGet()
        def list1 = client.reqAllOpenOrders().timeout(10, TimeUnit.SECONDS).blockingGet()
        def order6 = future6.timeout(10, TimeUnit.SECONDS).blockingGet()
        def list2 = client.reqAllOpenOrders().timeout(10, TimeUnit.SECONDS).blockingGet()


        then:
        list1.size() <= 6
        list1.containsKey(order1.getOrderId())
        list1.containsKey(order2.getOrderId())
        list1.containsKey(order3.getOrderId())
        list1.containsKey(order4.getOrderId())
        list1.containsKey(order5.getOrderId())

        list2.size() == 6
        list2.containsKey(order1.getOrderId())
        list2.containsKey(order2.getOrderId())
        list2.containsKey(order3.getOrderId())
        list2.containsKey(order4.getOrderId())
        list2.containsKey(order5.getOrderId())
        list2.containsKey(order6.getOrderId())
    }

    def "Few reconnects shouldn't impact to functionality"() {
        expect:

        (0..10).each {
            client.connect("127.0.0.1", 7497, 1).blockingGet(10, TimeUnit.SECONDS)
            assert client.isConnected()
            client.getCurrentTime().timeout(10, TimeUnit.SECONDS).blockingGet() > 1510320971
            client.disconnect()
        }
    }

    def "Set market data type"() {
        expect:
        client.setMarketDataType(IbClient.MarketDataType.DELAYED_FROZEN)
    }

    def "Get contract snapshot"() {
        when:
        def observer = client.reqMktData(createContractEUR()).test()

        then:
        observer.awaitDone(10, TimeUnit.SECONDS)
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
            def tz = OffsetDateTime.now().getOffset();
            assert Math.abs(from.toEpochSecond(tz) - tick.time()) < 100
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
            def tz = OffsetDateTime.now().getOffset();
            assert Math.abs(from.toEpochSecond(tz) - tick.time()) < 100
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
            def tz = OffsetDateTime.now().getOffset();
            assert Math.abs(from.toEpochSecond(tz) - tick.time()) < 100000
            assert tick.price() > 0.0
            assert tick.size() > 0.0
            true
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
        contract.conid(347896247)
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
