package com.finplant.ib.impl.sender;

import java.util.Objects;

// TODO: test
public class EventKey {
    private final RequestRepository.Event event;
    private final Integer id;

    EventKey(RequestRepository.Event event, Integer id) {
        this.event = event;
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EventKey eventKey = (EventKey) obj;
        return Objects.equals(id, eventKey.id) || event == eventKey.event;
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(event, id);

        }

        return Objects.hash(event);
    }

    @Override
    public String toString() {
        if (id == null && event == null) {
            return "Invalid message";
        }

        if (id != null && event == null) {
            return id.toString();
        }

        if (id == null) {
            return event.name();
        }

        return String.format("%s,%s", event.name(), id);
    }
}
