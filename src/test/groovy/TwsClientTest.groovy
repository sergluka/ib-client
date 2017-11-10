import com.ib.client.Contract
import com.ib.client.Types
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
        contract.exchange("SMART")
        contract.symbol("NVDA")
        contract.secType(Types.SecType.STK)

        when:
        def list = client.reqContractDetails(contract).get(1, TimeUnit.MINUTES)

        Thread.sleep(1000)

        def list2 = client.reqContractDetails(contract).get(1, TimeUnit.MINUTES)

        then:
        println(list)
        list.size() > 0
    }

}
