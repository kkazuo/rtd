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

import com.google.transit.realtime.GtfsRt;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

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
        } else {
            for (var arg : args) {
                try (final var input = new FileInputStream(arg)) {
                    dumpInput(input);
                }
            }
        }
    }

    static final void showHelp() {
        System.out.println("Decode GTFS-realtime Feed Message:");
        System.out.println();
        System.out.println("    rtd [-d] [files...]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("    -d    Read from standard input.");
        System.out.println();
    }

    static final void dumpInput(InputStream input) throws IOException {
        GtfsRt.FeedMessage.parseFrom(input).toString().lines().forEach(line -> {
            final var m = numPattern.matcher(line);
            if (m.find()) {
                System.out.print(line);
                System.out.print("  # ");
                System.out.println(Instant.ofEpochSecond(Long.parseLong(m.group(1)))
                        .atZone(utcZone)
                        .format(dateTimeFormatter));
            } else {
                System.out.println(line);
            }
        });
    }
}
