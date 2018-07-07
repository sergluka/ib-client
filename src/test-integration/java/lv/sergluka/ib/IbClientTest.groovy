package lv.sergluka.ib

import com.ib.client.*
import io.reactivex.functions.Predicate
import io.reactivex.observers.BaseTestConsumer
import io.reactivex.observers.TestObserver
import lv.sergluka.ib.impl.types.IbPnl
import lv.sergluka.ib.impl.types.IbPortfolio
import lv.sergluka.ib.impl.types.IbPosition
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.BlockingVariables
import spock.util.concurrent.PollingConditions

import java.util.concurrent.TimeUnit

class IbClientTest extends Specification {

    IbClient client = new IbClient()

    void setup() {
        client = new IbClient()
        assert client.connect("127.0.0.1", 7497, 2).blockingAwait(30, TimeUnit.SECONDS)
        client.reqGlobalCancel()
        // Wait until all respective callbacks will be called
        sleep(2_000)
    }

    void cleanup() {
        if (client) {
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
        def order = createOrderEUR()

        when:
        def state = client.placeOrder(contract, order).blockingGet()

        then:
        state.order.action() == order.action()
        state.orderId > 0
    }

    def "Few placeOrder shouldn't interfere"() {
        given:
        def contract = createContractEUR()

        when:
        def single1 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single2 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single3 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single4 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single5 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single6 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single7 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single8 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single9 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def single10 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))

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
        def order = createOrderEUR()

        def vars = new BlockingVariables(10)
        def calls = 1

        client.subscribeOnOrderNewStatus().subscribe({
            vars.setProperty("status${calls++}", it.status)
        })

        when:
        client.placeOrder(contract, order).blockingGet()

        then:
        vars.status1 == OrderStatus.PreSubmitted
        vars.status2 in [OrderStatus.Submitted, OrderStatus.Filled]
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
        client.placeOrder(contract, createOrderEUR(client.nextOrderId())).blockingGet()
        def list = client.reqAllOpenOrders().toList().blockingGet()

        then:
        println list
        list.size() > 0
    }

    def "Request positions"() {

//        given:
//        client.placeOrder(createContractXAUUSD(), createOrderXAUUSD(client.nextOrderId())).blockingGet()

        when:
        def positions = client.subscribeOnPositionChange("DU22993")
                .takeWhile({ it -> it != null })
                .toList()
                .timeout(10, TimeUnit.SECONDS)
                .blockingGet()

        then:
        positions
        positions.size() > 0
    }

    // TODO: Close all positions at startup, and make one for test
    def "Request positions for account"() {
        given:
        TestObserver<IbPosition> observer = new TestObserver<>()
        def condition = new PollingConditions(timeout: 5)

        when:
        client.subscribeOnPositionChange("DU22993").subscribe(observer)

        then:
        condition.eventually {
            observer.values().size() > 0
        }
        observer.assertNoErrors()
        observer.assertNotComplete()
        observer.assertValueAt(observer.values().size() - 1, IbPosition.EMPTY)
    }

    def "Request PnL per contract"() {
        given:
        TestObserver<IbPnl> observer = new TestObserver<>()

        when:
        client.subscribeOnContractPnl(12087792 /* EUR.USD */, "DU22993").subscribe(observer)

        then:
        observer.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1000MS)
        observer.assertNoErrors()
        observer.assertNotComplete()
        observer.assertValueCount(1)
        observer.assertValueAt 0, { it.positionId > 0 } as Predicate
    }

    def "Request PnL per account"() {
        given:
        TestObserver<IbPnl> observer = new TestObserver<>()

        when:
        client.subscribeOnAccountPnl("DU22993").subscribe(observer)

        then:
        observer.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1000MS)
        observer.assertNoErrors()
        observer.assertNotComplete()
        observer.assertValueCount(1)
        observer.assertValueAt 0, { it.unrealizedPnL != 0 } as Predicate
    }

    def "Request for Portfolio change for account"() {
        given:
        TestObserver<IbPortfolio> observer = new TestObserver<>()

        when:
        client.subscribeOnAccountPortfolio("DU22993").subscribe(observer)

        then:
        observer.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1000MS, 4 * 60 * 1000) // Account updates once per 3 min
        observer.assertNoErrors()
        observer.assertNotComplete()
        observer.assertValueCount(1)
        observer.assertValueAt 0, { it.position != 0.0 } as Predicate
    }

    def "Get market data"() {
        given:
        def var = new BlockingVariable(5)

        when:
        def subscription = client.subscribeOnMarketData(createContractEUR()) { tick ->
            var.set(tick)
        }

        then:
        def tick = var.get()
        tick.closePrice > 0

        cleanup:
        subscription?.unsubscribe()
    }

    def "Request orders and place orders shouldn't interfere each other"() {
        given:
        def contract = createContractEUR()

        when:
        def future1 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        client.reqAllOpenOrders().blockingGet(10, TimeUnit.SECONDS)
        def future2 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future3 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        client.reqAllOpenOrders().blockingGet(10, TimeUnit.SECONDS)
        def future4 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future5 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future6 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))

        and:

        def order1 = future1.blockingGet(10, TimeUnit.SECONDS)
        def order2 = future2.blockingGet(10, TimeUnit.SECONDS)
        def order3 = future3.blockingGet(10, TimeUnit.SECONDS)
        def order4 = future4.blockingGet(10, TimeUnit.SECONDS)
        def order5 = future5.blockingGet(10, TimeUnit.SECONDS)
        def list1 = client.reqAllOpenOrders().blockingGet(10, TimeUnit.SECONDS)
        def order6 = future6.blockingGet(10, TimeUnit.SECONDS)
        def list2 = client.reqAllOpenOrders().blockingGet(10, TimeUnit.SECONDS)


        then:
        list1.size() <= 6
        list1.get(0).getOrderId() == order1.getOrderId()
        list1.get(1).getOrderId() == order2.getOrderId()
        list1.get(2).getOrderId() == order3.getOrderId()
        list1.get(3).getOrderId() == order4.getOrderId()
        list1.get(4).getOrderId() == order5.getOrderId()

        list2.size() == 6
        list2.get(0).getOrderId() == order1.getOrderId()
        list2.get(1).getOrderId() == order2.getOrderId()
        list2.get(2).getOrderId() == order3.getOrderId()
        list2.get(3).getOrderId() == order4.getOrderId()
        list2.get(4).getOrderId() == order5.getOrderId()
        list2.get(5).getOrderId() == order6.getOrderId()
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
        def future = client.reqMktData(createContractEUR())
        def tick = future.timeout(10, TimeUnit.SECONDS).blockingGet()

        then:
        tick.getBid() != 0.0
        tick.getAsk() != 0.0
        tick.getClosePrice() != 0.0
    }

    private static def createContractEUR() {
        def contract = new Contract()
        contract.symbol("EUR")
        contract.currency("USD")
        contract.exchange("IDEALPRO")
        contract.secType(Types.SecType.CASH)
        return contract
    }

    private static def createContractXAUUSD() {
        def contract = new Contract()
        contract.symbol("XAUUSD")
        contract.currency("USD")
        contract.exchange("SMART")
        contract.secType(Types.SecType.CMDTY)
        return contract
    }

    private def createOrderEUR() {
        return createOrderEUR(client.nextOrderId())
    }

    private static def createOrderEUR(int id) {
        def order = new Order()
        order.orderId(id)
        order.action("BUY")
        order.orderType(OrderType.STP)
        order.auxPrice(1.0f)
        order.tif("GTC")
        order.totalQuantity(20000.0f)
        return order
    }

    private static def createOrderXAUUSD(int id) {
        def order = new Order()
        order.orderId(id)
        order.action("BUY")
        order.orderType("MKT")
        order.auxPrice(1.0f)
        order.totalQuantity(1.0f)
        return order
    }
}
