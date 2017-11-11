import com.ib.client.Contract
import com.ib.client.Order
import com.ib.client.Types
import lv.sergluka.tws.TwsClient
import spock.lang.Specification

import java.util.concurrent.TimeUnit

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
        def time = client.reqCurrentTime().get(10, TimeUnit.SECONDS)

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

    def "Call placeOrder is OK"() {
        given:
        def contract = new Contract();
        contract.symbol("GC");
        contract.currency("USD");
        contract.exchange("NYMEX");
        contract.secType(Types.SecType.FUT)
        contract.multiplier("100")
        contract.lastTradeDateOrContractMonth("201712");

        def order = new Order()
        order.orderId(client.nextOrderId())
        order.action("BUY");
        order.orderType("STP");
        order.auxPrice(1.1);
        order.triggerPrice(1.23)
        order.tif("GTC");
        order.totalQuantity(1.0)
        order.outsideRth(true)

        when:
        def future = client.placeOrder(contract, order)
        def state = future.get(10, TimeUnit.SECONDS)

        then:
        state.status()

        and:

        Thread.sleep(100000)
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
}
