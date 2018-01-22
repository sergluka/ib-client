package lv.sergluka.tws

import com.ib.client.Contract
import com.ib.client.Order
import com.ib.client.OrderStatus
import com.ib.client.Types
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.BlockingVariables

import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class TwsClientTest extends Specification {

    TwsClient client = new TwsClient()

    void setup() {
        client.connect("127.0.0.1", 7497, 2)
        client.waitForConnect(30, TimeUnit.SECONDS)
        client.reqGlobalCancel()
    }

    void cleanup() {
        if (client) {
            client.disconnect()
        }
    }

    def "smoke"() {
        expect:
        client.isConnected()
    }

    def "Call reqCurrentTime is OK"() {
        when:
        long time = client.reqCurrentTime().get(10, TimeUnit.SECONDS)

        then:
        time > 1510320971
    }

    def "Call reqContractDetails is OK"() {
        given:
        def contract = new Contract();
        contract.symbol("EUR")
        contract.currency("USD")
        contract.secType(Types.SecType.CASH)

        when:
        def list = client.reqContractDetails(contract).get(1, TimeUnit.MINUTES)

        then:
        println(list)
        list.size() > 0
    }

    def "Call reqContractDetails with callback is OK"() {
        given:
        def consumer = Mock(Consumer)

        def contract = new Contract();
        contract.symbol("EUR")
        contract.currency("USD")
        contract.secType(Types.SecType.CASH)

        def result = new BlockingVariable()
        consumer.accept(_) >> {
            result.set(it.get(0))
        }

        when:
        client.reqContractDetails(contract, consumer)

        then:
        def list = result.get()
        list.size() == 1
        list.get(0).longName() == "European Monetary Union Euro"
    }

    def "Call placeOrder is OK"() {
        given:
        def contract = createContractEUR()
        def order = createOrderEUR()

        when:
        def promise = client.placeOrder(contract, order)
        def state = promise.get(10, TimeUnit.SECONDS)

        then:
        state
    }

    def "Few placeOrder shouldn't interfere"() {
        given:
        def contract = createContractEUR()

        when:
        def promise1 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise2 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise3 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise4 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise5 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise6 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise7 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise8 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise9 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise10 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))

        then:
        promise1.get(10, TimeUnit.SECONDS)
        promise2.get(10, TimeUnit.SECONDS)
        promise3.get(10, TimeUnit.SECONDS)
        promise4.get(10, TimeUnit.SECONDS)
        promise5.get(10, TimeUnit.SECONDS)
        promise6.get(10, TimeUnit.SECONDS)
        promise7.get(10, TimeUnit.SECONDS)
        promise8.get(10, TimeUnit.SECONDS)
        promise9.get(10, TimeUnit.SECONDS)
        promise10.get(10, TimeUnit.SECONDS)
    }

    def "Expect for states after placeOrder"() {
        given:
        def contract = createContractEUR()
        def order = createOrderEUR()

        def vars = new BlockingVariables(10)
        def calls = 1

        client.subscribeOnOrderNewStatus { id, status ->
            assert id == order.orderId()
            vars.setProperty("call${calls++}", status)
        }

        when:
        client.placeOrder(contract, order).get(30, TimeUnit.SECONDS)

        then:
        vars.call1.status == OrderStatus.PreSubmitted
        vars.call2.status == OrderStatus.Filled
    }


    def "Request cannot be duplicated"() {
        when:
        client.reqAllOpenOrders()
        client.reqAllOpenOrders()
        client.reqAllOpenOrders()
        client.reqAllOpenOrders()

        then:
        TwsExceptions.DuplicatedRequest ex = thrown()
    }

    def "Request orders"() {
        given:
        def contract = createContractEUR()

        when:
        client.placeOrder(contract, createOrderEUR(client.nextOrderId())).get(3, TimeUnit.SECONDS)
        def list = client.reqAllOpenOrders().get(2, TimeUnit.SECONDS)

        then:
        println list
        list.size() > 0
    }

    def "Request positions"() {
        given:
        def vars = new BlockingVariables(5)
        vars.data = []

        when:
        client.subscribeOnPositionChange { position ->
            if (position != null) {
                vars.data.add(position)
            } else {
                vars.done = true
            }
        }

        then:
        vars.done
        vars.data.size() > 0
    }

    def "Request PnL per contract"() {
        given:
        def var = new BlockingVariable(5)

        when:
        def id = client.subscribeOnContractPnl(12087792, "DU22993") { pnl ->
            var.set(pnl)
        }

        then:
        def pnl = var.get()
        pnl.positionId > 0

        cleanup:
        if (id != null) {
            client.unsubscribeOnContractPnl(id)
        }
    }

    def "Request PnL per account"() {
        given:
        def var = new BlockingVariable(5)

        when:
        false
        def id = client.subscribeOnAccountPnl("DU22993") { pnl ->
            var.set(pnl)
        }

        then:
        def pnl = var.get()
        pnl.dailyPnL != 0
        pnl.unrealizedPnL > 0

        cleanup:
        if (id != null) {
            client.unsubscribeOnAccountPnl(id)
        }
    }

    def "Request for Portfolio change for account"() {
        given:
        def var = new BlockingVariable(4 * 60) // Account updates once per 3 min

        when:
        false
        def id = client.subscribeOnAccountPortfolio("DU22993") { portfolio ->
            var.set(portfolio)
        }

        then:
        def portfolio = var.get()
        portfolio.position != 0.0

        cleanup:
        if (id != null) {
            client.unsubscribeOnAccountPortfolio("DU22993")
        }
    }

    def "Get market data"() {
        given:
        def var = new BlockingVariable(5)

        when:
        def id = client.subscribeOnMarketData(createContractEUR()) { tick ->
            var.set(tick)
        }

        then:
        def tick = var.get()
        tick.bid > 0

        cleanup:
        if (id != null) {
            client.unsubscribeOnMarketData(id)
        }
    }

    def "Request orders and place orders shouldn't interfere each other"() {
        given:
        def contract = createContractEUR()

        when:
        def promise1 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        client.reqAllOpenOrders().get(10, TimeUnit.SECONDS)
        def promise2 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise3 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        client.reqAllOpenOrders().get(10, TimeUnit.SECONDS)
        def promise4 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise5 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def promise6 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))

        and:

        def order1 = promise1.get(10, TimeUnit.SECONDS)
        def order2 = promise2.get(10, TimeUnit.SECONDS)
        def order3 = promise3.get(10, TimeUnit.SECONDS)
        def order4 = promise4.get(10, TimeUnit.SECONDS)
        def order5 = promise5.get(10, TimeUnit.SECONDS)
        def list1 = client.reqAllOpenOrders().get(10, TimeUnit.SECONDS)
        def order6 = promise6.get(10, TimeUnit.SECONDS)
        def list2 = client.reqAllOpenOrders().get(10, TimeUnit.SECONDS)


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
            client.connect("127.0.0.1", 7497, 1)
            client.waitForConnect(10, TimeUnit.SECONDS)
            assert client.isConnected()
            client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971
            client.disconnect()
        }
    }

    def "Set market data type"() {
        expect:
        client.reqMarketDataType(TwsClient.MarketDataType.DELAYED_FROZEN)
    }

    def "Get contract snapshot"() {
        when:
        def promise = client.reqMktDataSnapshot(createContractEUR())
        def tick = promise.get(10, TimeUnit.SECONDS)

        then:
        tick.getBidSize() > 0
    }

    private def createContract() {
        def contract = new Contract();
        contract.symbol("GC");
        contract.currency("USD");
        contract.exchange("NYMEX");
        contract.secType(Types.SecType.FUT)
        contract.multiplier("100")
        contract.lastTradeDateOrContractMonth("201812");
        return contract
    }

    private def createContractEUR() {
        def contract = new Contract();
        contract.symbol("EUR");
        contract.currency("USD");
        contract.exchange("IDEALPRO");
        contract.secType(Types.SecType.CASH)
        return contract
    }

    private def createOrder() {
        return createOrder(client.nextOrderId())
    }

    private def createOrderEUR() {
        return createOrderEUR(client.nextOrderId())
    }

    private def createOrder(int id) {
        def order = new Order()
        order.orderId(id)
        order.action("BUY");
        order.orderType("STP");
        order.auxPrice(1.1f)
        order.triggerPrice(0.23f)
        order.tif("GTC");
        order.totalQuantity(1.0f)
        order.outsideRth(true)
        return order
    }

    private def createOrderEUR(int id) {
        def order = new Order()
        order.orderId(id)
        order.action("BUY")
        order.orderType("STP");
        order.auxPrice(1.0f)
        order.tif("GTC")
        order.totalQuantity(20000.0f)
        return order;
    }
}
