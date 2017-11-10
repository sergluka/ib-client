import com.ib.client.Contract
import lv.sergluka.tws.TwsClient
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class TwsClientTest extends Specification {

    TwsClient client

    void setup() {
        client = new TwsClient()
    }

    void cleanup() {
        if (client) {
            client.disconnect()
        }
    }

    def "connect"() {
        when:
        client.connect("127.0.0.1", 7497, 1)

        then:
        client.isConnected()
    }

    def "reqId"() {
        given:
        client.connect("127.0.0.1", 7497, 1)

        when:
        def id = client.reqIdsSync()

        then:
        id > 0
    }

    def "reqContractDetails"() {
        given:
        client.connect("127.0.0.1", 7497, 1)
        def contract = new Contract();
        contract.symbol("EURUSD")
        contract.secType("NEWS")

        when:
        def f = client.reqContractDetails(contract)
        def list = f.get(1, TimeUnit.MINUTES)

        then:
        list.size() > 0

    }

}
