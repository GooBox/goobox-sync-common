/*
 * Copyright (C) 2017 Kaloyan Raev
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
package io.goobox.sync.common.systemtray;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import dorkbox.util.Desktop;
import dorkbox.util.OS;
import dorkbox.util.SwingUtil;
import io.goobox.sync.common.Utils;

/**
 * Helper for managing the Goobox system tray icon.
 */
public class SystemTrayHelper {

    private final static URL ICON_IDLE = SystemTrayHelper.class.getResource("tray-idle.png");
    private final static URL ICON_SYNC = SystemTrayHelper.class.getResource("tray-sync.png");

    private final static String TOOLTIP_IDLE = "Idle";
    private final static String TOOLTIP_SYNC = "Synchronizing";

    private static SystemTray systemTray;
    private static ShutdownListener shutdownListener;

    /**
     * Sets the system tray icon to idle mode.
     */
    public static void setIdle() {
        if (initialized()) {
            systemTray.setImage(ICON_IDLE);
            systemTray.setStatus(TOOLTIP_IDLE);
            systemTray.setTooltip(TOOLTIP_IDLE);
        }
    }

    /**
     * Sets the system tray icon to synchronizing mode.
     */
    public static void setSynchronizing() {
        if (initialized()) {
            systemTray.setImage(ICON_SYNC);
            systemTray.setStatus(TOOLTIP_SYNC);
            systemTray.setTooltip(TOOLTIP_SYNC);
        }
    }

    /**
     * Sets a {@link ShutdownListener}, which allows the app to shutdown gracefully
     * when the "Quit" action is invoked.
     * 
     * <p>
     * If no shutdown listener is set then the "Quit" action will call
     * <code>System.exit(0)</code>.
     * </p>
     * 
     * @param listener
     *            the shutdown listener
     * @see ShutdownListener
     */
    public static void setShutdownListener(ShutdownListener listener) {
        shutdownListener = listener;
    }

    private static boolean init() {
        // Set Native look and feel on Windows instead of Swing
        if (OS.isWindows()) {
            SwingUtil.setLookAndFeel(null);
        }

        systemTray = SystemTray.get();
        if (systemTray == null) {
            // throw new RuntimeException("Unable to load SystemTray!");
            return false;
        }

        Menu mainMenu = systemTray.getMenu();
        mainMenu.add(new Separator());
        mainMenu.add(new MenuItem("Open Goobox Folder", new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                try {
                    String path = Utils.getSyncDir().toString();
                    if (OS.isWindows()) {
                        Desktop.browseDirectory(path);
                    } else {
                        Desktop.browseURL(path);
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        })).setShortcut('o');
        mainMenu.add(new Separator());
        systemTray.getMenu().add(new MenuItem("Quit Goobox", new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                systemTray.shutdown();
                if (shutdownListener == null) {
                    System.exit(0);
                } else {
                    shutdownListener.shutdown();
                }
            }
        })).setShortcut('q');

        return true;
    }

    private static boolean initialized() {
        return systemTray != null || init();
    }

}
