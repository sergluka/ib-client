package com.finplant.ib;

import com.ib.client.Contract;

public class Validators {

    public static void shouldNotBeNull(Object obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void stringShouldNotBeEmpty(String account, String message) {
        if (account == null || account.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void intShouldBePositive(int port, String message) {
        if (port <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void intShouldBePositiveOrZero(int connId, String message) {
        if (connId < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void contractWithIdShouldExist(Contract contract) {
        shouldNotBeNull(contract, "Contract should be defined");
        if (contract.conid() <= 0) {
            throw new IllegalArgumentException("Contract ID should be positive");
        }
    }
}
