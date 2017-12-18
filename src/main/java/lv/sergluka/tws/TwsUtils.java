package lv.sergluka.tws;

import java.util.Objects;

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
