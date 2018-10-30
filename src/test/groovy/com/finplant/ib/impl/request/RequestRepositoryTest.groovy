package com.finplant.ib.impl.request

import com.finplant.ib.IbClient
import com.finplant.ib.IbExceptions
import com.finplant.ib.IdGenerator
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.AsyncConditions

import java.util.function.Consumer

class RequestRepositoryTest extends Specification {

    def client = Mock(IbClient)
    def idGenerator = Mock(IdGenerator)

    @Subject
    def repository = new RequestRepository(client, idGenerator)

    def "Build request without type should raise error"() {
        when:
        repository.builder().register({}).subscribe()

        then:
        thrown(IllegalArgumentException)
    }

    def "Build request without registration function should raise error"() {
        when:
        repository.builder().type(RequestRepository.Type.EVENT_ACCOUNT_PNL).subscribe()

        then:
        thrown(IllegalArgumentException)
    }

    def "Add request with id and complete it with data"() {
        given:
        def registerCalled = new AsyncConditions()
        def unregisterCalled = new AsyncConditions()

        1 * idGenerator.nextRequestId() >> 111
        2 * client.isConnected() >> true
        0 * idGenerator._
        0 * client._

        when:
        def observer = repository.builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register({ id -> registerCalled.evaluate { assert id == 111 } } as Consumer<Integer>)
                .unregister({ id -> unregisterCalled.evaluate { assert id == 111 } } as Consumer<Integer>)
                .subscribe()
                .test()
        repository.onNextAndComplete(RequestRepository.Type.EVENT_PORTFOLIO, 111, "Data", true)

        then:
        observer.assertNoErrors()
        observer.assertComplete()
        observer.assertValueCount(1)
        observer.assertValue("Data")
        registerCalled.await()
        unregisterCalled.await()
    }

    def "Add request without id and pass data without completition"() {
        given:
        def registerCalled = new AsyncConditions()

        1 * client.isConnected() >> true
        0 * idGenerator._
        0 * client._

        when:
        def observer = repository.builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register({ id -> registerCalled.evaluate { assert id == null } } as Runnable)
                .subscribe()
                .test()
        repository.onNext(RequestRepository.Type.EVENT_PORTFOLIO, null, "Data", true)

        then:
        observer.assertNoErrors()
        observer.assertNotComplete()
        observer.assertValueCount(1)
        observer.assertValue("Data")
        registerCalled.await()
    }

    def "Duplicated request with same type should raise error"() {
        given:
        2 * client.isConnected() >> true
        0 * idGenerator._
        0 * client._

        when:
        repository.builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register({})
                .subscribe()
                .test()
        def observer = repository.builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register({})
                .subscribe()
                .test()
        then:
        observer.assertError(IbExceptions.DuplicatedRequest.class)
    }

    def "Add request without id and complete without data"() {
        given:
        def registerCalled = new AsyncConditions()
        def unregisterCalled = new AsyncConditions()

        2 * client.isConnected() >> true
        0 * idGenerator._
        0 * client._

        when:
        def observer = repository.builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register({ id -> registerCalled.evaluate { assert id == null } } as Runnable)
                .unregister({ id -> unregisterCalled.evaluate { assert id == null } } as Runnable)
                .subscribe()
                .test()
        repository.onComplete(RequestRepository.Type.EVENT_PORTFOLIO, null, true)

        then:
        observer.assertNoErrors()
        observer.assertComplete()
        observer.assertValueCount(0)
        registerCalled.await()
        unregisterCalled.await()
    }

    def "Add request without id and complete with error"() {
        given:
        def registerCalled = new AsyncConditions()
        def unregisterCalled = new AsyncConditions()

        2 * client.isConnected() >> true
        0 * idGenerator._
        0 * client._

        when:
        def observer = repository.builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register({ id -> registerCalled.evaluate { assert id == null } } as Runnable)
                .unregister({ id -> unregisterCalled.evaluate { assert id == null } } as Runnable)
                .subscribe()
                .test()
        repository.onError(RequestRepository.Type.EVENT_PORTFOLIO, null, new IllegalArgumentException())

        then:
        observer.assertError(IllegalArgumentException.class)
        observer.assertValueCount(0)
        registerCalled.await()
        unregisterCalled.await()
    }
}
