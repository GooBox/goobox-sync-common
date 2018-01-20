/*
 * Copyright (C) 2017 Kaloyan Raev, Junpei Kawamoto
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.joda.time.format.ISODateTimeFormat;

import net.harawata.appdirs.AppDirsFactory;

@SuppressWarnings({"WeakerAccess", "unused"})
public class Utils {

    public static final String APP_NAME = "Goobox";

    private static String OS = null;

    public static Path getHomeDir() {
        String path = System.getProperty("user.home");
        if (isWindows() && !isPureAscii(path)) {
            try {
                path = getMSDOSPath(path);
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException("Cannot determine user home dir", e);
            }
        }
        return Paths.get(path);
    }

    public static Path getDataDir() {
        return Paths.get(AppDirsFactory.getInstance().getUserDataDir(APP_NAME, null, ""));
    }

    public static Path getSyncDir() {
        return Utils.getHomeDir().resolve(APP_NAME);
    }

    private static String getOsName() {
        if (OS == null) {
            OS = System.getProperty("os.name");
        }
        return OS;
    }

    private static boolean isWindows() {
        return getOsName().startsWith("Windows");
    }

    private static boolean isPureAscii(String path) {
        return StandardCharsets.US_ASCII.newEncoder().canEncode(path);
    }

    private static String getMSDOSPath(String path) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(
                "cmd /c for %I in (\"" + path + "\") do @echo %~fsI");

        process.waitFor();

        byte[] data = new byte[65536];
        int size = process.getInputStream().read(data);

        if (size <= 0)
            return null;

        return new String(data, 0, size).replaceAll("\\r\\n", "");
    }

    private static final List<String> SYSTEM_FILES = Arrays.asList("desktop.ini", "thumbs.db", ".ds_store");

    /**
     * Returns true if the given path should be excluded from cloud synchronization.
     * <p>
     * The following files will be excluded:
     * - system files: desktop.ini, thumbs.db, .ds_store
     * - temporary files:
     * -- file its name starts with ~$ or .~,
     * -- file its name starts with ~ and ends with .tmp
     * - files and directories their names end with spaces.
     *
     * @param path to be evaluated.
     * @return true if the given path should be excluded.
     */
    public static boolean isExcluded(Path path) {

        final String filename = path.getFileName().toString().toLowerCase();
        for (final String name : SYSTEM_FILES) {
            if (filename.equals(name)) {
                return true;
            }
        }
        if (filename.startsWith("~$") || filename.startsWith(".~")) {
            return true;
        } else if (filename.startsWith("~") && filename.endsWith(".tmp")) {
            return true;
        }
        return filename.endsWith(" ");

    }

    /**
     * Returns a path for a conflicted copy of the given local file.
     * <p>
     * A file name pattern of a conflicted copy is as follows:
     * `original file name` (`user name`'s conflicted copy `date`) `counter`(.ext)
     * <p>
     * if counter is 0, it'll be omitted.
     *
     * @param localPath for which a conflicted copy path is going to be created.
     * @return a path of which the corresponding file doesn't exists.
     */
    public static Path conflictedCopyPath(Path localPath) {

        String name = localPath.getFileName().toString();
        String ext = "";
        final int idx = name.indexOf(".");
        if (idx != -1) {
            ext = name.substring(idx);
            name = name.substring(0, idx);
        }

        String fileName = String.format(
                "%s (%s's conflicted copy %s)%s",
                name,
                System.getProperty("user.name"),
                ISODateTimeFormat.date().print(System.currentTimeMillis()),
                ext);
        int c = 0;

        Path candidate = localPath.getParent().resolve(fileName);
        while (candidate.toFile().exists()) {
            c++;
            fileName = String.format(
                    "%s (%s's conflicted copy %s) %d%s",
                    name,
                    System.getProperty("user.name"),
                    ISODateTimeFormat.date().print(System.currentTimeMillis()),
                    c,
                    ext);
            candidate = localPath.getParent().resolve(fileName);
        }
        return candidate;

    }

}
