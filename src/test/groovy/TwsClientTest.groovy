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

    def "Request positions"() {
        given:
        def vars = new BlockingVariables(60)
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
        def order = new Order()
        order.orderId(client.nextOrderId())
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
