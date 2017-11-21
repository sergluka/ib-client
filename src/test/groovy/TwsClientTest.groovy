import com.ib.client.Contract
import com.ib.client.Order
import com.ib.client.OrderStatus
import com.ib.client.Types
import lv.sergluka.tws.TwsClient
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.BlockingVariables

import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class TwsClientTest extends Specification {

    TwsClient client = new TwsClient()

    void setup() {
        client.connect("127.0.0.1", 7497, 1)
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
        def contract = createContract()
        def order = createOrder()

        when:
        def promise = client.placeOrder(contract, order)
        def state = promise.get(10, TimeUnit.SECONDS)

        then:
        state.status() == OrderStatus.PreSubmitted
    }

    def "Few placeOrder shouldn't interfere"() {
        given:
        def contract = createContract()

        when:
        def promise1 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise2 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise3 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise4 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise5 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise6 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise7 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise8 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise9 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise10 = client.placeOrder(contract, createOrder(client.nextOrderId()))

        then:
        promise1.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise2.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise3.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise4.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise5.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise6.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise7.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise8.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise9.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
        promise10.get(10, TimeUnit.SECONDS).status() == OrderStatus.PreSubmitted
    }

    def "Call placeOrder with callback is OK"() {
        given:
        def contract = createContract()
        def order = createOrder()
        def consumer = Mock(Consumer)

        def result = new BlockingVariable()
        consumer.accept(_) >> {
            result.set(it.get(0))
        }

        when:
        client.placeOrder(contract, order, consumer)

        then:
        result.get().with {
            status() == OrderStatus.PreSubmitted
        }
    }

    def "Expect for states after placeOrder"() {
        given:
        def contract = createContract()
        def order = createOrder()

        def vars = new BlockingVariables(60)
        def calls = 1

        client.subscribeOnOrderStatus { id, status ->
            assert id == order.orderId()
            vars.setProperty("call${calls++}", status)
        }

        when:
        client.placeOrder(contract, order).get(10, TimeUnit.SECONDS)

        then:
        vars.call1.status == OrderStatus.PreSubmitted
        vars.call2.status == OrderStatus.Filled
    }


    def "Request orders and plca orders shouldn't interfere each other"() {
        given:
        def contract = createContract()

        when:
        def promise1 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def list1 = client.reqAllOpenOrders()
        def promise2 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise3 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def list2 = client.reqAllOpenOrders()
        def promise4 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def promise5 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def list3 = client.reqAllOpenOrders()
        def promise6 = client.placeOrder(contract, createOrder(client.nextOrderId()))
        def list4 = client.reqAllOpenOrders().get(10, TimeUnit.SECONDS);

        then:
        list4
    }

    def "Request positions"() {
        given:
        def vars = new BlockingVariables(5)
        vars.data = []

        when:
        client.subscribeOnPosition { position ->
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

    def "Few reconnects doesn't impact to functionality"() {
        expect:

        (0..10).each {
            client.connect("127.0.0.1", 7497, 1)
            assert client.isConnected()
            client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971
            client.disconnect()
        }
    }

    private def createContract() {
        def contract = new Contract();
        contract.symbol("GC");
        contract.currency("USD");
        contract.exchange("NYMEX");
        contract.secType(Types.SecType.FUT)
        contract.multiplier("100")
        contract.lastTradeDateOrContractMonth("201712");
        return contract
    }

    private def createOrder() {
        return createOrder(client.nextOrderId())
    }

    private def createOrder(int id) {
        def order = new Order()
        order.orderId(id)
        order.action("BUY");
        order.orderType("STP");
        order.auxPrice(1.1);
        order.triggerPrice(0.23)
        order.tif("GTC");
        order.totalQuantity(1.0)
        order.outsideRth(true)
        return order;
    }
}
