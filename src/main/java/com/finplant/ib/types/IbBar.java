package com.finplant.ib.types;

import com.google.common.base.Objects;
import com.ib.client.Bar;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public class IbBar {

    public static final IbBar COMPLETE = new IbBar();

    private static final DateTimeFormatter dateTimeFormatter =
            new DateTimeFormatterBuilder().appendPattern("yyyyMMdd[  HH:mm:ss]")
                                          .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                                          .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                                          .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                                          .toFormatter();

    private final LocalDateTime time;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final long volume;
    private final int count;
    private final BigDecimal wap;

    public IbBar(Bar bar) {
        time = LocalDateTime.from(dateTimeFormatter.parse(bar.time()));
        open = BigDecimal.valueOf(bar.open());
        high = BigDecimal.valueOf(bar.high());
        low = BigDecimal.valueOf(bar.low());
        close = BigDecimal.valueOf(bar.close());
        volume = bar.volume();
        count = bar.count();
        wap = BigDecimal.valueOf(bar.wap());
    }

    private IbBar() {
        time = null;
        open = null;
        high = null;
        low = null;
        close = null;
        volume = 0;
        count = 0;
        wap = null;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public long getVolume() {
        return volume;
    }

    public int getCount() {
        return count;
    }

    public BigDecimal getWap() {
        return wap;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder("{");
        buffer.append("time='").append(time).append('\'');
        buffer.append(", open=").append(open);
        buffer.append(", high=").append(high);
        buffer.append(", low=").append(low);
        buffer.append(", close=").append(close);
        buffer.append(", volume=").append(volume);
        buffer.append(", count=").append(count);
        buffer.append(", wap=").append(wap);
        buffer.append('}');
        return buffer.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IbBar)) {
            return false;
        }
        IbBar ibBar = (IbBar) o;
        return volume == ibBar.volume &&
               count == ibBar.count &&
               Objects.equal(time, ibBar.time) &&
               Objects.equal(open, ibBar.open) &&
               Objects.equal(high, ibBar.high) &&
               Objects.equal(low, ibBar.low) &&
               Objects.equal(close, ibBar.close) &&
               Objects.equal(wap, ibBar.wap);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(time, open, high, low, close, volume, count, wap);
    }
}
