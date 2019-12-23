package com.finplant.ib.impl.request

import com.finplant.ib.IbClient
import com.finplant.ib.IbExceptions
import com.finplant.ib.impl.IdGenerator
import reactor.test.StepVerifier
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.AsyncConditions

import java.time.Duration
import java.util.function.Consumer

class RequestRepositoryTest extends Specification {

    def client = Mock(IbClient)
    def idGenerator = Mock(IdGenerator)

    @Subject
    def repository = new RequestRepository(client, idGenerator)

    def "Build request without type should raise error"() {

        expect:
        StepVerifier.create(repository.builder().register({}).subscribe())
                .expectError(IllegalArgumentException)
                .verify()
    }

    def "Build request without registration function should raise error"() {

        expect:
        StepVerifier.create(repository.builder().type(RequestRepository.Type.EVENT_ACCOUNT_PNL).subscribe())
                .expectError(IllegalArgumentException)
                .verify()
    }

    def "Add request with id and complete it with data"() {
        given:
        def registerCalled = new AsyncConditions()
        def unregisterCalled = new AsyncConditions()

        1 * idGenerator.nextId() >> 111
        2 * client.isConnected() >> true
        0 * idGenerator._
        0 * client._

        when:

        StepVerifier.create(repository.builder()
                                    .type(RequestRepository.Type.EVENT_PORTFOLIO)
                                    .register(
                                            { id -> registerCalled.evaluate { assert id == 111 } }
                                                    as Consumer<Integer>)
                                    .unregister(
                                            { id -> unregisterCalled.evaluate { assert id == 111 } }
                                                    as Consumer<Integer>)
                                    .subscribe())
                .then { repository.onNextAndComplete(RequestRepository.Type.EVENT_PORTFOLIO, 111, "Data", true) }
                .expectNext("Data")
                .verifyComplete()

        then:
        registerCalled.await()
        unregisterCalled.await()
    }

    def "Add request without id and pass data without completition"() {
        given:
        def registerCalled = new AsyncConditions()

        2 * client.isConnected() >> true
        0 * idGenerator._
        0 * client._

        def observer = repository.builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register({ id -> registerCalled.evaluate { assert id == null } } as Runnable)
                .subscribe()

        expect:
        StepVerifier.create(observer)
                .then { repository.onNext(RequestRepository.Type.EVENT_PORTFOLIO, null, "Data", true) }
                .expectNext("Data")
                .expectNoEvent(Duration.ofSeconds(2))
                .thenCancel()
                .verify(Duration.ofSeconds(5))

        registerCalled.await()
    }

    def "Duplicated request with same type should raise error"() {
        given:
        2 * client.isConnected() >> true
        0 * idGenerator._
        0 * client._

        expect:
        repository.builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register({})
                .subscribe().subscribe()

        StepVerifier.create(repository.builder()
                                    .type(RequestRepository.Type.EVENT_PORTFOLIO)
                                    .register({})
                                    .subscribe())
                .expectError(IbExceptions.DuplicatedRequestError)
                .verify()
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

        StepVerifier.create(observer)
                .then { repository.onComplete(RequestRepository.Type.EVENT_PORTFOLIO, null, true) }
                .expectComplete()
                .verify()

        then:
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

        StepVerifier.create(observer)
                .then { repository.onError(RequestRepository.Type.EVENT_PORTFOLIO, null, new IllegalArgumentException()) }
                .expectError(IllegalArgumentException)
                .verify()

        then:
        registerCalled.await()
        unregisterCalled.await()
    }
}
