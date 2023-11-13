/*
Copyright 2023 Koga Kazuo (kkazuo@kkazuo.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.github.kkazuo;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;
import com.google.transit.realtime.GtfsRt;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decode GTFS-realtime Feed Message binary encoded data
 */
public class App {
    static final ZoneOffset utcZone = ZoneOffset.ofHours(9);
    static final Pattern numPattern = Pattern.compile(" ([12][0-9]{9})\\Z");
    static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            showHelp();
        } else if (args[0].equals("-d")) {
            dumpInput(System.in);
        } else if (args[0].equals("-w")) {
            if (args.length < 3) {
                showHelp();
                System.exit(65);
            }
            for (int i = 1; i + 1 < args.length; ++i) {
                final String a1 = args[i];
                final String a2 = args[i + 1];
                try (final InputStream i1 = new FileInputStream(a1);
                        final InputStream i2 = new FileInputStream(a2)) {
                    dumpDiff(i1, i2, () -> {
                        System.out.println(String.format("%60s @ %s", a1, a2));
                        return null;
                    });
                } catch (FileNotFoundException e) {
                    System.err.println(e);
                }
            }
        } else {
            for (final String arg : args) {
                try (final InputStream input = new FileInputStream(arg)) {
                    dumpInput(input);
                }
            }
        }
    }

    static final void showHelp() {
        System.out.println("Decode GTFS-realtime Feed Message:");
        System.out.println();
        System.out.println("    rtd [options] [files...]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("    -d    Read from standard input.");
        System.out.println("    -w    Diff each files. (at least 2 files required.)");
        System.out.println();
    }

    static final void dumpInput(InputStream input) {
        parseInputAsMessageToStrings(input).forEach(line -> System.out.println(line));
    }

    static final Stream<String> parseInputAsMessageToStrings(InputStream input) {
        try {
            return Arrays.stream(GtfsRt.FeedMessage.parseFrom(input).toString().split("\\r?\\n"))
                    .map(line -> {
                        final Matcher m = numPattern.matcher(line);
                        if (m.find()) {
                            final String humanTime = Instant.ofEpochSecond(Long.parseLong(m.group(1)))
                                    .atZone(utcZone)
                                    .format(dateTimeFormatter);
                            return String.format("%s  # %s", line, humanTime);
                        } else {
                            return line;
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final void dumpDiff(InputStream i1, InputStream i2, Supplier<Void> dumpDeltaHeader) {
        final List<String> s1 = parseInputAsMessageToStrings(i1).collect(Collectors.toList());
        final List<String> s2 = parseInputAsMessageToStrings(i2).collect(Collectors.toList());
        final Patch<String> diff = DiffUtils.diff(s1, s2);
        boolean nonFirst = true;
        for (final AbstractDelta<String> delta : diff.getDeltas()) {
            if (nonFirst && isChangeDelta(delta)) {
                nonFirst = false;
                dumpDeltaHeader.get();
            }
            dumpDelta(delta);
        }
    }

    static final boolean isChangeDelta(AbstractDelta<String> d) {
        switch (d.getType()) {
            case EQUAL:
                return false;
            default:
                return true;
        }
    }

    static final void dumpDelta(AbstractDelta<String> d) {
        final Chunk<String> src = d.getSource();
        final Chunk<String> tgt = d.getTarget();

        switch (d.getType()) {
            case CHANGE:
                System.out.println(String.format("%60d =>", src.getPosition()));
                break;
            case DELETE:
                System.out.println(String.format("%60d - %d", src.getPosition(), tgt.getPosition()));
                break;
            case INSERT:
                System.out.println(String.format("%60d + %d", src.getPosition(), tgt.getPosition()));
                break;
            case EQUAL:
            default:
                break;
        }

        final Iterator<String> si = src.getLines().iterator();
        final Iterator<String> ti = tgt.getLines().iterator();
        while (si.hasNext() || ti.hasNext()) {
            final String ss = si.hasNext() ? si.next() : "";
            final String ts = ti.hasNext() ? ti.next() : "";
            System.out.println(String.format("%-60s | %s", ss, ts));
        }
    }
}
