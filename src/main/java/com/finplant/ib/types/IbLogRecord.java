package com.finplant.ib.types;

@SuppressWarnings("unused")
public class IbLogRecord {

    public enum Severity {
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    /**
     * Severity of IB event.
     */
    private final Severity severity;
    private final int id;
    private final int code;
    private final String message;

    public IbLogRecord(Severity severity, int id, int code, String message) {
        this.severity = severity;
        this.id = id;
        this.code = code;
        this.message = message;
    }

    /**
     * Severity of IB event.
     *
     * @return Severity
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * IB request ID. Can be 0.
     *
     * @return request ID
     */
    public int getId() {
        return id;
    }

    /**
     * IB message code.
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/message_codes.html">Message Codes</a>
     *
     * @return message code
     */
    public int getCode() {
        return code;
    }

    /**
     * IB message description.
     *
     * @return Description
     */
    public String getMessage() {
        return message;
    }
}
