package lv.sergluka.ib.impl.types;

public class IbOrderBook {

    public static class Key {
        private final Side side;
        private final int position;

        public Key(Side side, int position) {
            this.side = side;
            this.position = position;
        }

        public Side getSide() {
            return side;
        }

        public int getPosition() {
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

            if (position != key.position) return false;
            return side == key.side;
        }

        @Override
        public int hashCode() {
            int result = side.hashCode();
            result = 31 * result + position;
            return result;
        }
    }

    public enum Side {
        BUY,
        SELL
    }

    public enum Operation {
        INSERT,
        UPDATE,
        REMOVE
    }

    private final int position;
    private final Side side;
    private final double price;
    private final int size;
    private final String marketMaker;

    public IbOrderBook(int position, int side, double price, int size, String marketMaker) {
        switch (side) {
            case 0:
                this.side = Side.BUY;
                break;
            case 1:
                this.side = Side.SELL;
                break;
            default:
                throw new IllegalArgumentException(String.format("Unexpected side: %d", side));
        }

        this.position = position;
        this.price = price;
        this.size = size;
        this.marketMaker = marketMaker;
    }

    public int getPosition() {
        return position;
    }

    public Side getSide() {
        return side;
    }

    public double getPrice() {
        return price;
    }

    public int getSize() {
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
