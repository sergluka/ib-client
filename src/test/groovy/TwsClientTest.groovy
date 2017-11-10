import com.ib.client.Contract
import com.ib.client.Types
import lv.sergluka.tws.TwsClient
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class TwsClientTest extends Specification {

    TwsClient client = new TwsClient()

    void setup() {
        client.connect("127.0.0.1", 7497, 1)
        assert client.isConnected()
    }

    void cleanup() {
        if (client) {
            client.disconnect()
        }
    }

    def "reqContractDetails"() {
        given:
        client.connect("127.0.0.1", 7497, 1)
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
