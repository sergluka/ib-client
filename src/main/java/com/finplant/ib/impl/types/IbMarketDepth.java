package com.finplant.ib.impl.types;

import java.math.BigDecimal;
import java.util.Objects;

import com.ib.client.Contract;

public class IbMarketDepth {

    private final Contract contract;

    public static class Key {
        private final Side side;
        private final Integer position;

        public Key(Side side, Integer position) {
            this.side = side;
            this.position = position;
        }

        public Side getSide() {
            return side;
        }

        public Integer getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return String.format("%s/%d", side, position);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;

            Key key = (Key) o;

            if (!Objects.equals(position, key.position)) return false;
            return side == key.side;
        }

        @Override
        public int hashCode() {
            Integer result = side.hashCode();
            result = 31 * result + position;
            return result;
        }
    }

    public enum Side {
        SELL,
        BUY
    }

    public enum Operation {
        INSERT,
        UPDATE,
        REMOVE
    }

    private final Integer position;
    private final Side side;
    private final BigDecimal price;
    private final Integer size;
    private final String marketMaker;

    public IbMarketDepth(Contract contract, Integer position, Integer side, BigDecimal price, Integer size,
                         String marketMaker) {
        switch (side) {
            case 0:
                this.side = Side.SELL;
                break;
            case 1:
                this.side = Side.BUY;
                break;
            default:
                throw new IllegalArgumentException(String.format("Unexpected side: %d", side));
        }

        this.contract = contract;
        this.position = position;
        this.price = price;
        this.size = size;
        this.marketMaker = marketMaker;
    }

    public Key key() {
        return new Key(side, position);
    }

    public Contract getContract() {
        return contract;
    }

    public Integer getPosition() {
        return position;
    }

    public Side getSide() {
        return side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getSize() {
        return size;
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        buffer.append("position=").append(position);
        buffer.append(", side=").append(side);
        buffer.append(", price=").append(price);
        buffer.append(", size=").append(size);
        buffer.append(", marketMaker=").append(marketMaker);
        buffer.append('}');
        return buffer.toString();
    }
}
