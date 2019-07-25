package com.finplant.ib.impl.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public class Converter {

    private static final Logger log = LoggerFactory.getLogger(Converter.class);

    /**
     * TWS API describes that double equals Double.MAX_VALUE should be threaded as unset
     */
    public static BigDecimal doubleToBigDecimal(String name, double value) {
        if (value == Double.MAX_VALUE) {
            log.trace("Missing value for '{}'", name);
            return null;
        }

        return BigDecimal.valueOf(value);
    }

}
