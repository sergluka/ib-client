package com.finplant.ib.impl.cache;

import java.util.Objects;

class PositionKey {
    private final String account;
    private final int contractId;

    PositionKey(String account, int contractId) {
        this.account = account;
        this.contractId = contractId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PositionKey that = (PositionKey) o;
        return contractId == that.contractId &&
                Objects.equals(account, that.account);
    }

    @Override
    public int hashCode() {

        return Objects.hash(account, contractId);
    }
}
