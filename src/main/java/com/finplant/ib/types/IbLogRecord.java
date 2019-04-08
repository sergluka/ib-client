package com.finplant.ib.types;

@SuppressWarnings("unused")
public class IbLogRecord {

    public enum Severity {
        INFO,
        WARN,
        ERROR,
    }

    /**
     * Severity of IB event
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
     * @return Severity of IB event
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * @return IB request ID. Can be 0.
     */
    public int getId() {
        return id;
    }

    /**
     * @return IB message code.
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/message_codes.html">Message Codes</a>
     */
    public int getCode() {
        return code;
    }

    /**
     * @return IB message description
     */
    public String getMessage() {
        return message;
    }
}
