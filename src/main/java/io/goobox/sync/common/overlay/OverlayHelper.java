/*
 * Copyright (C) 2017-2018 Kaloyan Raev
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
package io.goobox.sync.common.overlay;

import com.liferay.nativity.control.NativityControl;
import com.liferay.nativity.control.NativityControlUtil;
import com.liferay.nativity.modules.contextmenu.ContextMenuControlCallback;
import com.liferay.nativity.modules.contextmenu.model.ContextMenuAction;
import com.liferay.nativity.modules.contextmenu.model.ContextMenuItem;
import com.liferay.nativity.modules.fileicon.FileIconControl;
import com.liferay.nativity.modules.fileicon.FileIconControlCallback;
import com.liferay.nativity.modules.fileicon.FileIconControlUtil;
import com.liferay.nativity.util.OSDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Stream;

public class OverlayHelper implements FileIconControlCallback, ContextMenuControlCallback {

    private static final Logger logger = LoggerFactory.getLogger(OverlayHelper.class);

    private Path syncDir;
    private OverlayIconProvider iconProvider;

    private NativityControl nativityControl;
    private FileIconControl fileIconControl;
    private BlockingQueue<String[]> queue = new LinkedBlockingDeque<>();

    private int globalStateIconId = OverlayIcon.NONE.id();

    private boolean shutdown = false;

    public OverlayHelper(Path syncDir, OverlayIconProvider syncStateProvider) {
        this.syncDir = syncDir;
        this.iconProvider = syncStateProvider;

        if (!OSDetector.isWindows() && !OSDetector.isApple()) {
            return;
        }

        nativityControl = NativityControlUtil.getNativityControl();

        if (nativityControl != null) {
            new Thread(this::init).start();
        }
    }

    private void init() {
        Thread.currentThread().setName("Init overlay icons");

        synchronized (this) {
            while (!shutdown) {
                if (nativityControl.connect()) {
                    // successfully connected - exit the loop
                    logger.debug("Successfully connected to native service.");
                    break;
                }

                // Connection failed. Most probably the port has not been released yet from a
                // previous run of the app. Retry in 30 seconds.
                logger.debug("Connection to native service failed. Retry in 30 seconds.");
                try {
                    wait(30000);
                } catch (InterruptedException e) {
                    logger.debug("init interrupted", e);
                    return;
                }
            }
        }

        if (shutdown) {
            return;
        }

        // Make Goobox a system folder
        if (OSDetector.isWindows()) {
            DosFileAttributeView attr = Files.getFileAttributeView(syncDir, DosFileAttributeView.class);
            try {
                attr.setSystem(true);
            } catch (IOException e) {
                logger.error("Cannot set system folder", e);
            }
        }

        fileIconControl = FileIconControlUtil.getFileIconControl(nativityControl, this);
        fileIconControl.enableFileIcons();

        // Register icons
        if (OSDetector.isApple() || OSDetector.isLinux()) {

            final Path resourceDir = Paths.get(System.getProperty("goobox.resource", "."));
            for (OverlayIcon state : OverlayIcon.values()) {

                final Path icon = resourceDir.resolve(String.format("overlay_%s.icns", state.name())).toAbsolutePath();
                if (Files.exists(icon)) {
                    logger.debug("Register {} with ID {} ({})", icon, String.valueOf(state.id()), state);
                    fileIconControl.registerIconWithId(
                            icon.toAbsolutePath().toString(), state.name(), String.valueOf(state.id()));
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        logger.warn("Interrupted while registering overlay icons: {}", e.getMessage());
                    }
                } else {
                    logger.warn("Cannot find overlay icon {} for ID {} ({})", icon, String.valueOf(state.id()), state);
                }

            }

        }

        nativityControl.setFilterFolder(syncDir.toString());

        /* Context Menus */
        // No context menu yet
        // ContextMenuControlUtil.getContextMenuControl(nativityControl, this);refresh

        logger.debug("OverlayHelper has been initialized");
        try {
            while (!shutdown) {
                this.refreshIcons(this.queue.take());
            }
        } catch (InterruptedException e) {
            logger.warn("Thread for overlay icons was interrupted: {}", e.getMessage());
        }

    }

    public void setOK() {
        if (!OSDetector.isWindows() && !OSDetector.isApple()) {
            return;
        }

        globalStateIconId = OverlayIcon.OK.id();
        refresh();
    }

    public void setSynchronizing() {
        if (!OSDetector.isWindows() && !OSDetector.isApple()) {
            return;
        }

        globalStateIconId = OverlayIcon.SYNCING.id();
        refresh();
    }

    public void shutdown() {
        if (!OSDetector.isWindows() && !OSDetector.isApple()) {
            return;
        }

        // interupt init() if still running
        synchronized (this) {
            shutdown = true;
            notify();
        }

        globalStateIconId = OverlayIcon.NONE.id();
        refresh();

        if (nativityControl != null) {
            nativityControl.disconnect();
        }
    }

    public void refresh(Path path) {
        if (fileIconControl != null && path != null) {
            String[] pathAndParents = Stream.iterate(path, Path::getParent)
                    .limit(syncDir.relativize(path).getNameCount())
                    .map(Path::toString)
                    .toArray(String[]::new);
            this.queue.offer(pathAndParents);
        }
    }

    private void refresh() {
        if (fileIconControl != null) {
            this.queue.offer(new String[]{syncDir.toString()});
        }
    }

    private void refreshIcons(String[] paths) {
        fileIconControl.refreshIcons(paths);
    }

    /* FileIconControlCallback used by Windows and Mac */
    @Override
    public int getIconForFile(String path) {
        Path p = Paths.get(path);
        if (!p.startsWith(syncDir)) {
            return OverlayIcon.NONE.id();
        } else if (syncDir.equals(p)) {
            return globalStateIconId;
        } else {
            try {
                return Files.walk(p)
                        .map(iconProvider::getIcon)
                        .map(OverlayIcon::id)
                        .reduce(OverlayIcon.NONE.id(), Integer::max);
            } catch (IOException | UncheckedIOException e) {
                logger.error("Failed walking the file tree", e);
            }
        }
        return 0;
    }

    @Override
    public List<ContextMenuItem> getContextMenuItems(String[] paths) {
        ContextMenuItem contextMenuItem = new ContextMenuItem("Goobox");

        ContextMenuAction contextMenuAction = new ContextMenuAction() {
            @Override
            public void onSelection(String[] paths) {
                logger.info("Context menu selection: {}", String.join("; ", paths));
            }
        };

        contextMenuItem.setContextMenuAction(contextMenuAction);

        List<ContextMenuItem> contextMenuItems = new ArrayList<>();
        contextMenuItems.add(contextMenuItem);

        // Mac Finder Sync will only show the parent level of context menus
        return contextMenuItems;
    }

}
