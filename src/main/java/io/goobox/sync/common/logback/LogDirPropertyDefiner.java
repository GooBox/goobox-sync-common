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
package io.goobox.sync.common.logback;

import ch.qos.logback.core.PropertyDefinerBase;
import io.goobox.sync.common.Utils;
import net.harawata.appdirs.AppDirsFactory;

/**
 * Property definer for the log directory Logback property.
 */
public class LogDirPropertyDefiner extends PropertyDefinerBase {

    /**
     * Returns the path to the log directory.
     * 
     * <p>
     * This method uses the {@link AppDirsFactory} class which also uses SLF4J for
     * logging. The logging framework is not initialized yet, so SLF4J will print an
     * error in the console: "A number (1) of logging calls during the
     * initialization phase have been intercepted and are now being replayed.". The
     * message is harmless and there is no way to suppress it if we want to use the
     * {@link AppDirsFactory} class.
     * </p>
     * 
     * @return path to the log directory
     */
    @Override
    public String getPropertyValue() {
        return AppDirsFactory.getInstance().getUserLogDir(Utils.APP_NAME, null, "");
    }

}
