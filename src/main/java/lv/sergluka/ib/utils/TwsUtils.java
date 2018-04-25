package lv.sergluka.ib.utils;

import java.util.Objects;

@SuppressWarnings("unused")
public class TwsUtils {

    public static String oppositeSide(String side) {
        Objects.requireNonNull(side);

        if (side.equals("BUY")) {
            return "SELL";
        }

        if (side.equals("SELL")) {
            return "BUY";
        }

        throw new IllegalArgumentException(String.format("Unexpected side: %s", side));
    }
}
