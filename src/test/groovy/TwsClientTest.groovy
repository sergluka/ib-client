import com.ib.client.Contract
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

    def "reqCurrentTime"() {
        when:
        def time = client.reqCurrentTime().get(10, TimeUnit.SECONDS)

        then:
        time > 1510320971
    }

    def "test constant reconnects doesn't interfere a functionality"() {
        expect:

        client.connect("127.0.0.1", 7497, 1)
        assert client.isConnected()
        client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971

        client.disconnect()
        assert !client.isConnected()
        client.connect("127.0.0.1", 7497, 1)
        assert client.isConnected()
        client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971

        client.disconnect()
        assert !client.isConnected()
        client.connect("127.0.0.1", 7497, 1)
        assert client.isConnected()
        client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971

        client.disconnect()
        assert !client.isConnected()
        client.connect("127.0.0.1", 7497, 1)
        assert client.isConnected()
        client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971

        client.disconnect()
        assert !client.isConnected()
        client.connect("127.0.0.1", 7497, 1)
        assert client.isConnected()
        client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971

        client.disconnect()
        assert !client.isConnected()
        client.connect("127.0.0.1", 7497, 1)
        assert client.isConnected()
        client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971

        client.disconnect()
        assert !client.isConnected()
        client.connect("127.0.0.1", 7497, 1)
        assert client.isConnected()
        client.reqCurrentTime().get(10, TimeUnit.SECONDS) > 1510320971

        client.disconnect()
        assert !client.isConnected()
    }

    def "reqContractDetails"() {
        given:
        def contract = new Contract();
        contract.symbol("EUR");
        contract.currency("USD");
        contract.secType(Types.SecType.CASH)
//        contract.exchange("SMART")
//        contract.symbol("NVDA")
//        contract.secType(Types.SecType.STK)

        when:
        def list = client.reqContractDetails(contract).get(1, TimeUnit.MINUTES)

        then:
        println(list)
        list.size() > 0
    }

}
