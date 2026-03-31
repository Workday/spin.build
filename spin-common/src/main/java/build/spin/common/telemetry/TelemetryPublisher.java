package build.spin.common.telemetry;

/*-
 * #%L
 * Spin Common Library
 * %%
 * Copyright (C) 2026 Workday, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import build.base.telemetry.Activity;
import build.base.telemetry.Advice;
import build.base.telemetry.Commenced;
import build.base.telemetry.Completed;
import build.base.telemetry.Diagnostic;
import build.base.telemetry.Error;
import build.base.telemetry.Fatal;
import build.base.telemetry.Information;
import build.base.telemetry.Location;
import build.base.telemetry.Meter;
import build.base.telemetry.NamedUnit;
import build.base.telemetry.Notification;
import build.base.telemetry.Progress;
import build.base.telemetry.Telemetry;
import build.base.telemetry.TelemetryRecorder;
import build.base.telemetry.TelemetryRecorderFactory;
import build.base.telemetry.Warning;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A {@link TelemetryRecorder} and publisher of {@link Telemetry} for a specific source URI.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class TelemetryPublisher
        implements TelemetryRecorder {

    /**
     * The {@link URI} of this {@link TelemetryPublisher}.
     */
    private final URI uri;

    /**
     * The {@link Consumer} to publish {@link Telemetry}.
     */
    private final Consumer<Telemetry> publisher;

    /**
     * Constructs a {@link TelemetryPublisher}.
     *
     * @param uri       the source {@link URI}
     * @param publisher the {@link Consumer} to use for publishing
     */
    public TelemetryPublisher(final URI uri,
            final Consumer<Telemetry> publisher) {

        this.uri = Objects.requireNonNull(uri, "The source must not be null");
        this.publisher = Objects.requireNonNull(publisher, "The publisher must not be null");
    }

    @Override
    public URI uri() {
        return this.uri;
    }

    @Override
    public TelemetryRecorderFactory factory() {
        return uri -> new TelemetryPublisher(uri, this.publisher);
    }

    @Override
    public Information info(final Stream<? extends Location> locations, final String format, final Object... args) {
        final Information telemetry = Information.create(this.uri, locations, format, args);
        this.publisher.accept(telemetry);
        return telemetry;
    }

    @Override
    public Advice advice(final Stream<? extends Location> locations, final String format, final Object... args) {
        final Advice telemetry = Advice.create(this.uri, locations, format, args);
        this.publisher.accept(telemetry);
        return telemetry;
    }

    @Override
    public Notification notify(final Stream<? extends Location> locations, final String format, final Object... args) {
        final Notification telemetry = Notification.create(this.uri, locations, format, args);
        this.publisher.accept(telemetry);
        return telemetry;
    }

    @Override
    public Warning warn(final Stream<? extends Location> locations, final Throwable throwable, final String format, final Object... args) {
        final Warning telemetry = Warning.create(this.uri, locations, throwable, format, args);
        this.publisher.accept(telemetry);
        return telemetry;
    }

    @Override
    public Error error(final Stream<? extends Location> locations, final Throwable throwable, final String format, final Object... args) {
        final Error telemetry = Error.create(this.uri, locations, throwable, format, args);
        this.publisher.accept(telemetry);
        return telemetry;
    }

    @Override
    public Fatal fatal(final Stream<? extends Location> locations, final Throwable throwable, final String format, final Object... args) {
        final Fatal telemetry = Fatal.create(this.uri, locations, throwable, format, args);
        this.publisher.accept(telemetry);
        return telemetry;
    }

    @Override
    public Diagnostic diagnostic(final Stream<? extends Location> locations, final String format, final Object... args) {
        final Diagnostic telemetry = Diagnostic.create(this.uri, locations, format, args);
        this.publisher.accept(telemetry);
        return telemetry;
    }

    @Override
    public Activity commence(final String format, final Object... args) {
        final String description = (format == null || format.isEmpty())
                ? UUID.randomUUID().toString()
                : String.format(format, args);

        final Commenced commenced = Commenced.create(this.uri, description);
        this.publisher.accept(commenced);

        final AtomicBoolean completed = new AtomicBoolean(false);

        return new Activity() {
            @Override
            public <T> boolean complete(final T value) {
                if (completed.compareAndSet(false, true)) {
                    TelemetryPublisher.this.publisher.accept(
                            Completed.create(
                                    TelemetryPublisher.this.uri, value, commenced.instant(), description));
                    return true;
                }
                return false;
            }

            @Override
            public boolean complete() {
                return complete(null);
            }

            @Override
            public boolean completeExceptionally(final Throwable throwable) {
                if (completed.compareAndSet(false, true)) {
                    TelemetryPublisher.this.publisher.accept(
                            Error.create(TelemetryPublisher.this.uri, throwable, description));
                    return true;
                }
                return false;
            }

            @Override
            public void close() {
                complete();
            }
        };
    }

    @Override
    public Meter commence(final int total, final NamedUnit unit, final String format, final Object... arguments) {
        final String description = (format == null || format.isEmpty())
                ? UUID.randomUUID().toString()
                : String.format(format, arguments);

        final int maximum = Math.max(0, total);

        final Commenced commenced = Commenced.create(this.uri, description);
        this.publisher.accept(commenced);

        this.publisher.accept(Progress.create(TelemetryPublisher.this.uri, 0, maximum, description));

        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicInteger progress = new AtomicInteger(0);

        return new Meter() {
            @Override
            public boolean progress(final int delta, final String format, final Object... arguments) {
                if (completed.compareAndSet(false, false)) {
                    final int previous = progress.getAndUpdate(current -> Math.min((current + delta), maximum));
                    final int current = progress.get();

                    final String message = (format == null || format.isEmpty())
                            ? description
                            : String.format(format, arguments);

                    if (current != previous) {
                        TelemetryPublisher.this.publisher.accept(
                                Progress.create(TelemetryPublisher.this.uri, current, maximum, message));
                    }

                    return true;
                }
                return false;
            }

            @Override
            public <T> boolean complete(final T value) {
                if (completed.compareAndSet(false, true)) {
                    if (progress.get() != maximum) {
                        progress.set(maximum);
                        TelemetryPublisher.this.publisher.accept(
                                Progress.create(TelemetryPublisher.this.uri, maximum, maximum, description));
                    }

                    TelemetryPublisher.this.publisher.accept(
                            Completed.create(
                                    TelemetryPublisher.this.uri, value, commenced.instant(), description));
                    return true;
                }
                return false;
            }

            @Override
            public boolean complete() {
                return complete(null);
            }

            @Override
            public boolean completeExceptionally(final Throwable throwable) {
                if (completed.compareAndSet(false, true)) {
                    TelemetryPublisher.this.publisher.accept(
                            Error.create(TelemetryPublisher.this.uri, throwable, description));
                    return true;
                }
                return false;
            }

            @Override
            public void close() {
                complete();
            }
        };
    }
}
