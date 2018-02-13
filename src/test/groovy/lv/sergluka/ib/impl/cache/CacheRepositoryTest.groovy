package lv.sergluka.ib.impl.cache

import com.ib.client.Contract
import spock.lang.Specification

class CacheRepositoryTest extends Specification {

    def cache = new CacheRepository()

    def "Getting absent position should return null"() {
        given:
        def contract = new Contract()
        contract.conid(1234)

        when:
        def position = cache.getPosition("AAAA", contract)


        then:
        position == null
    }
}
