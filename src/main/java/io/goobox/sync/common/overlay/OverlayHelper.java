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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.liferay.nativity.control.NativityControl;
import com.liferay.nativity.control.NativityControlUtil;
import com.liferay.nativity.modules.contextmenu.ContextMenuControlCallback;
import com.liferay.nativity.modules.contextmenu.model.ContextMenuAction;
import com.liferay.nativity.modules.contextmenu.model.ContextMenuItem;
import com.liferay.nativity.modules.fileicon.FileIconControl;
import com.liferay.nativity.modules.fileicon.FileIconControlCallback;
import com.liferay.nativity.modules.fileicon.FileIconControlUtil;
import com.liferay.nativity.util.OSDetector;

public class OverlayHelper implements FileIconControlCallback, ContextMenuControlCallback {

    private static final Logger logger = LoggerFactory.getLogger(OverlayHelper.class);

    private Path syncDir;
    private OverlayIconProvider iconProvider;

    private NativityControl nativityControl;
    private FileIconControl fileIconControl;

    private int globalStateIconId = OverlayIcon.NONE.id();

    private boolean shutdown = false;

    public OverlayHelper(Path syncDir, OverlayIconProvider syncStateProvider) {
        this.syncDir = syncDir;
        this.iconProvider = syncStateProvider;

        if (!OSDetector.isWindows()) {
            return;
        }

        nativityControl = NativityControlUtil.getNativityControl();

        if (nativityControl != null) {
            new Thread() {
                public void run() {
                    init();
                };
            }.start();
        }
    }

    private void init() {
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

        nativityControl.setFilterFolder(syncDir.toString());

        // Make Goobox a system folder
        DosFileAttributeView attr = Files.getFileAttributeView(syncDir, DosFileAttributeView.class);
        try {
            attr.setSystem(true);
        } catch (IOException e) {
            logger.error("Cannot set system folder", e);
        }

        fileIconControl = FileIconControlUtil.getFileIconControl(nativityControl, this);
        fileIconControl.enableFileIcons();

        /* Context Menus */
        // No context menu yet
        // ContextMenuControlUtil.getContextMenuControl(nativityControl, this);refresh
    }

    public void setOK() {
        if (!OSDetector.isWindows()) {
            return;
        }

        globalStateIconId = OverlayIcon.OK.id();
        refresh();
    }

    public void setSynchronizing() {
        if (!OSDetector.isWindows()) {
            return;
        }

        globalStateIconId = OverlayIcon.SYNCING.id();
        refresh();
    }

    public void shutdown() {
        if (!OSDetector.isWindows()) {
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
            String[] pathAndParents = Stream.iterate(path, p -> p.getParent())
                    .limit(syncDir.relativize(path).getNameCount())
                    .map(Path::toString)
                    .toArray(String[]::new);
            fileIconControl.refreshIcons(pathAndParents);
        }
    }

    private void refresh() {
        if (fileIconControl != null) {
            fileIconControl.refreshIcons(new String[] { syncDir.toString() });
        }
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
            } catch (IOException e) {
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

        List<ContextMenuItem> contextMenuItems = new ArrayList<ContextMenuItem>();
        contextMenuItems.add(contextMenuItem);

        // Mac Finder Sync will only show the parent level of context menus
        return contextMenuItems;
    }

}
