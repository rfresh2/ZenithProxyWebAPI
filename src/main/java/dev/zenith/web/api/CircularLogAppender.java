package dev.zenith.web.api;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.zenith.util.struct.CircularFifoQueue;

import java.util.ArrayList;
import java.util.List;

public class CircularLogAppender extends AppenderBase<ILoggingEvent> {
    private final CircularFifoQueue<ILoggingEvent> events;
    private long totalAppended = 0;

    public CircularLogAppender(final int maxEntries) {
        this.events = new CircularFifoQueue<>(maxEntries);
    }

    @Override
    protected synchronized void append(final ILoggingEvent eventObject) {
        events.add(eventObject);
        totalAppended++;
    }

    public synchronized LogSnapshot snapshot(final long fromIndex, final int limit) {
        long baseIndex = totalAppended - events.size();
        long safeFromIndex = Math.max(fromIndex, baseIndex);
        var items = new ArrayList<ILoggingEvent>(Math.min(limit, events.size()));
        long index = baseIndex;
        for (var event : events) {
            if (index >= safeFromIndex && items.size() < limit) {
                items.add(event);
            }
            index++;
        }
        return new LogSnapshot(baseIndex, safeFromIndex, safeFromIndex + items.size(), events.size(), items);
    }

    public record LogSnapshot(
        long baseIndex,
        long fromIndex,
        long nextIndex,
        int retained,
        List<ILoggingEvent> events
    ) { }
}
