package com.phonghub.adapter.out.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class InMemoryFileStorageAdapterTest {

    @Test
    void signedUrlStopsWorkingAtExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-08T00:00:00Z"));
        InMemoryFileStorageAdapter adapter = new InMemoryFileStorageAdapter(clock);
        byte[] content = "private".getBytes();
        adapter.put("properties/p/documents/a.pdf", "application/pdf", content);

        URI signedUrl = adapter.createSignedUrl(
            "properties/p/documents/a.pdf", Duration.ofMinutes(10)
        );

        assertArrayEquals(content, adapter.openSignedUrl(signedUrl).orElseThrow());
        clock.advance(Duration.ofMinutes(10));
        assertTrue(adapter.openSignedUrl(signedUrl).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
