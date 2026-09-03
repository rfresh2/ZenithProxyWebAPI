package dev.zenith.web.api;

import com.zenith.event.console.ConsoleLogEvent;
import com.zenith.util.struct.CircularFifoQueue;

import java.util.ArrayList;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.EVENT_BUS;

public class CircularLogQueue {
    private final CircularFifoQueue<ConsoleLogEvent> events;
    private final Runnable appendListener;
    private long totalAppended = 0;

    public CircularLogQueue(final int maxEntries, final Runnable appendListener) {
        this.events = new CircularFifoQueue<>(maxEntries);
        this.appendListener = appendListener;
        EVENT_BUS.subscribe(
            this,
            of(ConsoleLogEvent.class, this::onConsoleLogEvent)
        );
    }

    private void onConsoleLogEvent(final ConsoleLogEvent event) {
        synchronized (this) {
            events.add(event);
            totalAppended++;
        }
        appendListener.run();
    }

    public synchronized LogSnapshot snapshot(final long fromIndex, final int limit) {
        long baseIndex = totalAppended - events.size();
        long safeFromIndex = Math.max(fromIndex, baseIndex);
        var items = new ArrayList<ConsoleLogEvent>(Math.min(limit, events.size()));
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
        List<ConsoleLogEvent> events
    ) { }
}
