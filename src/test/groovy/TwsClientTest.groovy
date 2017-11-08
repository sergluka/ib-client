import lv.sergluka.tws.TwsClient
import spock.lang.Specification

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
        setup:
        client.connect("127.0.0.1", 7497, 1)

        when:
        def id = client.reqIdsSync()
        Thread.sleep(100000);

        then:
        id > 0
    }
}
