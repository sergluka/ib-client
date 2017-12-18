package lv.sergluka.tws.impl.types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwsTick {

    private static final Logger log = LoggerFactory.getLogger(TwsTick.class);

    public static class IntValues {
        public static final int BID_SIZE = 0;
        public static final int ASK_SIZE = 3;
        public static final int LAST_SIZE = 5;
        public static final int VOLUME = 8;
        public static final int VOLUME_AVERAGE = 21;
        public static final int OPTION_CALL_OPEN_INTEREST = 27;
        public static final int OPTION_PUT_OPEN_INTEREST = 28;
        public static final int OPTION_CALL_VOLUME = 29;
        public static final int OPTION_PUT_VOLUME = 30;
        public static final int ACTION_VOLUME = 34;
        public static final int ACTION_IMBALANCE = 36;
        public static final int REGULATORY_IMBALANCE = 61;
        public static final int SHORT_TERM_VOLUME3_MIN = 63;
        public static final int SHORT_TERM_VOLUME5_MIN = 64;
        public static final int SHORT_TERM_VOLUME10_MIN = 65;
        public static final int DELAYED_BID_SIZE = 69;
        public static final int DELAYED_ASK_SIZE = 70;
        public static final int DELAYED_LAST_SIZE = 71;
        public static final int DELAYED_VOLUME = 74;
        public static final int FUTURES_OPEN_INTEREST = 86;
    }

    private int bidSize;
    private int askSize;
    private int lastSize;
    private int volume;
    private int volumeAverage;
    private int optionCallOpenInterest;
    private int optionPutOpenInterest;
    private int optionCallVolume;
    private int optionPutVolume;
    private int actionVolume;
    private int actionImbalance;
    private int regulatoryImbalance;
    private int shortTermVolume3Min;
    private int shortTermVolume5Min;
    private int shortTermVolume10Min;
    private int delayedBidSize;
    private int delayedAskSize;
    private int delayedLastSize;
    private int delayedVolume;
    private int futuresOpenInterest;

    public TwsTick() {
    }

    public void setIntValue(int type, int value) {
        switch (type) {
            case IntValues.BID_SIZE:
                bidSize = value;
                break;
            case IntValues.ASK_SIZE:
                askSize = value;
                break;
            case IntValues.LAST_SIZE:
                lastSize = value;
                break;
            case IntValues.VOLUME:
                volume = value;
                break;
            case IntValues.VOLUME_AVERAGE:
                volumeAverage = value;
                break;
            case IntValues.OPTION_CALL_OPEN_INTEREST:
                optionCallOpenInterest = value;
                break;
            case IntValues.OPTION_PUT_OPEN_INTEREST:
                optionPutOpenInterest = value;
                break;
            case IntValues.OPTION_CALL_VOLUME:
                optionCallVolume = value;
                break;
            case IntValues.OPTION_PUT_VOLUME:
                optionPutVolume = value;
                break;
            case IntValues.ACTION_VOLUME:
                actionVolume = value;
                break;
            case IntValues.ACTION_IMBALANCE:
                actionImbalance = value;
                break;
            case IntValues.REGULATORY_IMBALANCE:
                regulatoryImbalance = value;
                break;
            case IntValues.SHORT_TERM_VOLUME3_MIN:
                shortTermVolume3Min = value;
                break;
            case IntValues.SHORT_TERM_VOLUME5_MIN:
                shortTermVolume5Min = value;
                break;
            case IntValues.SHORT_TERM_VOLUME10_MIN:
                shortTermVolume10Min = value;
                break;
            case IntValues.DELAYED_BID_SIZE:
                delayedBidSize = value;
                break;
            case IntValues.DELAYED_ASK_SIZE:
                delayedAskSize = value;
                break;
            case IntValues.DELAYED_LAST_SIZE:
                delayedLastSize = value;
                break;
            case IntValues.DELAYED_VOLUME:
                delayedVolume = value;
                break;
            case IntValues.FUTURES_OPEN_INTEREST:
                futuresOpenInterest = value;
                break;
            default:
                log.warn("Unknown int type for tick, type={}, value={}", type, value);
        }
        log.debug("Set int value #{} = {}", type, value);
    }

    public int getBidSize() {
        return bidSize;
    }

    public int getAskSize() {
        return askSize;
    }

    public int getLastSize() {
        return lastSize;
    }

    public int getVolume() {
        return volume;
    }

    public int getVolumeAverage() {
        return volumeAverage;
    }

    public int getOptionCallOpenInterest() {
        return optionCallOpenInterest;
    }

    public int getOptionPutOpenInterest() {
        return optionPutOpenInterest;
    }

    public int getOptionCallVolume() {
        return optionCallVolume;
    }

    public int getOptionPutVolume() {
        return optionPutVolume;
    }

    public int getActionVolume() {
        return actionVolume;
    }

    public int getActionImbalance() {
        return actionImbalance;
    }

    public int getRegulatoryImbalance() {
        return regulatoryImbalance;
    }

    public int getShortTermVolume3Min() {
        return shortTermVolume3Min;
    }

    public int getShortTermVolume5Min() {
        return shortTermVolume5Min;
    }

    public int getShortTermVolume10Min() {
        return shortTermVolume10Min;
    }

    public int getDelayedBidSize() {
        return delayedBidSize;
    }

    public int getDelayedAskSize() {
        return delayedAskSize;
    }

    public int getDelayedLastSize() {
        return delayedLastSize;
    }

    public int getDelayedVolume() {
        return delayedVolume;
    }

    public int getFuturesOpenInterest() {
        return futuresOpenInterest;
    }
}
