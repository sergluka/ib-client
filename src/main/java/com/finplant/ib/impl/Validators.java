package com.finplant.ib.impl;

import com.ib.client.Contract;

import java.util.Collection;

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

    public static void intShouldBePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void intShouldBePositiveOrZero(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void intShouldBeInRange(int value, int min, int max) {
        if (value < min && value > max) {
            throw new IllegalArgumentException(String.format("Parameter is not in range: [%d - %d]", min, max));
        }
    }

    public static void contractWithIdShouldExist(Contract contract) {
        shouldNotBeNull(contract, "Contract should be defined");
        if (contract.conid() <= 0) {
            throw new IllegalArgumentException("Contract ID should be positive");
        }
    }

    public static void collectionShouldNotBeEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
