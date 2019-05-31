package com.finplant.ib.impl

import com.finplant.ib.IbExceptions
import com.finplant.ib.impl.request.RequestRepository
import com.finplant.ib.types.IbLogRecord
import spock.lang.Specification

class TerminalErrorHandlerTest extends Specification {

    def requests = Mock(RequestRepository)

    def "Check historical data subscription error messages parsing"() {

        given:
        TerminalErrorHandler handler = new TerminalErrorHandler(requests) {
            @Override
            void onLog(IbLogRecord record) {
            }

            @Override
            void onError() {
            }

            @Override
            void onFatalError() {

            }
        }

        when:
        handler.handle(1, 162, "Historical Market Data Service error message:HMDS query returned no data: AGD@SMART Trades")

        then:
        1 * requests.onError(null, 1, _, true) >> { params ->
            assert params[2] instanceof IbExceptions.NoDataError
        }
    }
}
