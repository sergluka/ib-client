package lv.sergluka.ib

import com.ib.client.*
import lv.sergluka.ib.impl.types.IbOrderBook
import spock.lang.Ignore
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.BlockingVariables

import java.util.concurrent.TimeUnit

class IbClientTest extends Specification {

    IbClient client = new IbClient()

    void setup() {
        client = new IbClient()
        client.connect("127.0.0.1", 7497, 2).get(30, TimeUnit.SECONDS)
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
        long time = client.reqCurrentTime().get(10, TimeUnit.SECONDS)

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
        def list = client.reqContractDetails(contract).get(1, TimeUnit.MINUTES)

        then:
        println(list)
        list.size() > 0
    }

    def "Call placeOrder is OK"() {
        given:
        def contract = createContractEUR()
        def order = createOrderEUR()

        when:
        def future = client.placeOrder(contract, order)
        def state = future.get(10, TimeUnit.SECONDS)

        then:
        state
    }

    def "Few placeOrder shouldn't interfere"() {
        given:
        def contract = createContractEUR()

        when:
        def future1 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future2 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future3 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future4 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future5 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future6 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future7 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future8 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future9 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future10 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))

        then:
        future1.get(10, TimeUnit.SECONDS)
        future2.get(10, TimeUnit.SECONDS)
        future3.get(10, TimeUnit.SECONDS)
        future4.get(10, TimeUnit.SECONDS)
        future5.get(10, TimeUnit.SECONDS)
        future6.get(10, TimeUnit.SECONDS)
        future7.get(10, TimeUnit.SECONDS)
        future8.get(10, TimeUnit.SECONDS)
        future9.get(10, TimeUnit.SECONDS)
        future10.get(10, TimeUnit.SECONDS)
    }

    def "Expect for states after placeOrder"() {
        given:
        def contract = createContractEUR()
        def order = createOrderEUR()

        def vars = new BlockingVariables(10)
        def calls = 1

        client.subscribeOnOrderNewStatus {
            assert it.orderId == it.orderId
            vars.setProperty("status${calls++}", it.status)
        }

        when:
        client.placeOrder(contract, order).get(30, TimeUnit.SECONDS)

        then:
        vars.status1 == OrderStatus.PreSubmitted
        vars.status2 in [OrderStatus.Submitted, OrderStatus.Filled]
    }


    def "Request cannot be duplicated"() {
        when:
        client.reqAllOpenOrders()
        client.reqAllOpenOrders()
        client.reqAllOpenOrders()
        client.reqAllOpenOrders()

        then:
        IbExceptions.DuplicatedRequest ex = thrown()
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

    def "Request positions for account"() {
        given:
        def vars = new BlockingVariables(5)
        vars.data = []

        when:
        client.subscribeOnPositionChange("DU22993") { position ->
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
        def subscription = client.subscribeOnContractPnl(12087792, "DU22993") { pnl ->
            var.set(pnl)
        }

        then:
        def pnl = var.get()
        pnl.positionId > 0

        cleanup:
        subscription?.unsubscribe()
    }

    def "Request PnL per account"() {
        given:
        def var = new BlockingVariable(10)

        when:
        false
        def subscription = client.subscribeOnAccountPnl("DU22993") { pnl ->
            var.set(pnl)
        }

        then:
        def pnl = var.get()
        pnl.unrealizedPnL != null

        cleanup:
        subscription?.unsubscribe()
    }

    def "Request for Portfolio change for account"() {
        given:
        def var = new BlockingVariable(4 * 60) // Account updates once per 3 min

        when:
        client.subscribeOnAccountPortfolio("DU22993") { portfolio ->
            var.set(portfolio)
        }

        then:
        def portfolio = var.get()
        portfolio.position != null
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

    def "Get market depth"() {
        when:
        def orderBook = client.getMarketDepth(createContractEUR(), 20).get(10, TimeUnit.SECONDS)
        print(orderBook)

        then:
        orderBook.size() > 0
        def level1 = orderBook.get(new IbOrderBook.Key(IbOrderBook.Side.BUY, 0))
        level1.position == 0
        level1.side == IbOrderBook.Side.BUY
        level1.price > 0.0
        level1.size > 0

        def level2 = orderBook.get(new IbOrderBook.Key(IbOrderBook.Side.BUY, 1))
        level2.position == 1
        level2.side == IbOrderBook.Side.BUY
        level2.price > 0.0
        level2.size > 0

        def level3 = orderBook.get(new IbOrderBook.Key(IbOrderBook.Side.SELL, 0))
        level3.position == 0
        level3.side == IbOrderBook.Side.SELL
        level3.price > 0.0
        level3.size > 0

        def level4 = orderBook.get(new IbOrderBook.Key(IbOrderBook.Side.SELL, 1))
        level4.position == 1
        level4.side == IbOrderBook.Side.SELL
        level4.price > 0.0
        level4.size > 0

        sleep(3000)
    }

    @Ignore("To check contracts with L2 paid subscription is need. Use this test for respective account and run manually")
    def "Get market depth L2"() {
        when:
        def orderBook = client.getMarketDepth(createContractARRY_L2(), 5).get(10, TimeUnit.SECONDS)
        print(orderBook)

        then:
        orderBook.size() > 0
        def level1 = orderBook.get(new IbOrderBook.Key(IbOrderBook.Side.BUY, 0))
        level1.position == 0
        level1.side == IbOrderBook.Side.BUY
        level1.price > 0.0
        level1.size > 0

        def level2 = orderBook.get(new IbOrderBook.Key(IbOrderBook.Side.BUY, 1))
        level2.position == 1
        level2.side == IbOrderBook.Side.BUY
        level2.price > 0.0
        level2.size > 0

        def level3 = orderBook.get(new IbOrderBook.Key(IbOrderBook.Side.SELL, 0))
        level3.position == 0
        level3.side == IbOrderBook.Side.SELL
        level3.price > 0.0
        level3.size > 0

        def level4 = orderBook.get(new IbOrderBook.Key(IbOrderBook.Side.SELL, 1))
        level4.position == 1
        level4.side == IbOrderBook.Side.SELL
        level4.price > 0.0
        level4.size > 0

        sleep(3000)
    }
    def "Request market depth information about exchanges"() {
        when:
        def exchanges = client.reqMktDepthExchanges().get(10, TimeUnit.SECONDS)

        then:
        exchanges.size() > 0
    }

    def "Request orders and place orders shouldn't interfere each other"() {
        given:
        def contract = createContractEUR()

        when:
        def future1 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        client.reqAllOpenOrders().get(10, TimeUnit.SECONDS)
        def future2 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future3 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        client.reqAllOpenOrders().get(10, TimeUnit.SECONDS)
        def future4 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future5 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))
        def future6 = client.placeOrder(contract, createOrderEUR(client.nextOrderId()))

        and:

        def order1 = future1.get(10, TimeUnit.SECONDS)
        def order2 = future2.get(10, TimeUnit.SECONDS)
        def order3 = future3.get(10, TimeUnit.SECONDS)
        def order4 = future4.get(10, TimeUnit.SECONDS)
        def order5 = future5.get(10, TimeUnit.SECONDS)
        def list1 = client.reqAllOpenOrders().get(10, TimeUnit.SECONDS)
        def order6 = future6.get(10, TimeUnit.SECONDS)
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
            client.connect("127.0.0.1", 7497, 1).get(10, TimeUnit.SECONDS)
            assert client.isConnected()
            client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971
            client.disconnect()
        }
    }

    def "Set market data type"() {
        expect:
        client.reqMarketDataType(IbClient.MarketDataType.DELAYED_FROZEN)
    }

    def "Get contract snapshot"() {
        when:
        def future = client.reqMktData(createContractEUR())
        def tick = future.get(10, TimeUnit.SECONDS)

        then:
        tick.getBidSize() > 0
    }

    private static def createContractEUR() {
        def contract = new Contract()
        contract.symbol("EUR")
        contract.currency("USD")
        contract.exchange("IDEALPRO")
        contract.secType(Types.SecType.CASH)
        return contract
    }

    private static def createContractARRY_L2() {
        def contract = new Contract()
        contract.symbol("ARRY")
        contract.currency("USD")
        contract.exchange("ISLAND")
        contract.secType(Types.SecType.STK)
        return contract
    }

    private def createOrderEUR() {
        return createOrderEUR(client.nextOrderId())
    }

    private static def createOrderEUR(int id) {
        def order = new Order()
        order.orderId(id)
        order.action("BUY")
        order.orderType("STP")
        order.auxPrice(1.0f)
        order.tif("GTC")
        order.totalQuantity(20000.0f)
        return order
    }
}
