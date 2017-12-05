/*
 * Copyright (C) 2017 Junpei Kawamoto
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.goobox.sync.common;

import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.commons.io.FileUtils;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JMockit.class)
public class UtilsTest {

    private Path tmpDir;

    @Before
    public void setUp() throws IOException {
        tmpDir = Files.createTempDirectory(null);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tmpDir.toFile());
    }

    @Test
    public void isExcluded() throws IOException {

        // System files.
        final List<String> systemFiles = Arrays.asList("desktop.ini", "thumbs.db", ".ds_store", ".DS_Store");

        // Temporary files.
        final String random = Long.toHexString(System.currentTimeMillis());
        final Stream<String> tempFiles = Stream.of("~$" + random, ".~" + random, "~" + random + ".tmp");

        // Illegal file names.
        final Stream<String> illegalFiles = Stream.of(random + "     ");

        Stream.concat(systemFiles.stream(), Stream.concat(tempFiles, illegalFiles)).forEach(filename -> {
            // relative path.
            assertTrue(Utils.isExcluded(Paths.get(filename)));
            // absolute path.
            assertTrue(Utils.isExcluded(Paths.get(filename).toAbsolutePath()));
        });

        // Similar to system files.
        final Stream<Path> similarFiles = Stream.concat(
                systemFiles.stream().map(name -> name + random), systemFiles.stream().map(name -> random + name)).map(Paths::get);

        // Normal files.
        final Stream<Path> normalFiles = Files.list(Paths.get("."));

        Stream.concat(similarFiles, normalFiles).forEach(path -> {
            // relative path.
            assertFalse(Utils.isExcluded(path));
            // absolute path.
            assertFalse(Utils.isExcluded(path.toAbsolutePath()));
        });

    }

    @Test
    public void conflictedCopyPath(@Mocked System system) throws IOException {

        final String userName = "example";
        final long currentTime = 10000000L;
        final String date = ISODateTimeFormat.date().print(currentTime);

        new Expectations() {{
            System.getProperty("user.name");
            result = userName;
            System.currentTimeMillis();
            result = currentTime;
        }};

        class Fixture {
            private String input;
            private String expected;

            private Fixture(String input, String expected) {
                this.input = input;
                this.expected = expected;
            }
        }
        final String template = "%s (%s's conflicted copy %s)";
        final List<Fixture> fixtures = Arrays.asList(
                new Fixture("sample", String.format(template, "sample", userName, date)),
                new Fixture("sample", String.format(template, "sample", userName, date) + " 1"),
                new Fixture("sample.ext", String.format(template, "sample", userName, date) + ".ext"),
                new Fixture("sample.ext", String.format(template, "sample", userName, date) + " 1.ext"),
                new Fixture("sample.ext.ext2", String.format(template, "sample", userName, date) + ".ext.ext2"),
                new Fixture("sample.ext.ext2", String.format(template, "sample", userName, date) + " 1.ext.ext2")
        );

        for (Fixture fixture : fixtures) {
            final Path res = Utils.conflictedCopyPath(tmpDir.resolve(fixture.input));
            assertEquals(tmpDir.resolve(fixture.expected), res);
            assertTrue(res.toFile().createNewFile());
        }

    }

}