/**
 * Secret
 * Copyright (C) 2021 Secret
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
package com.waz.zclient.emoji.bean;

import java.io.Serializable;
import java.util.List;

public class EmojiResponse implements Serializable {

    private String Name;
    private String Version;
    private String Path;
    private List<EmojiBean>Zips;

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getVersion() {
        return Version;
    }

    public void setVersion(String version) {
        Version = version;
    }

    public String getPath() {
        return Path;
    }

    public void setPath(String path) {
        Path = path;
    }

    public List<EmojiBean> getZips() {
        return Zips;
    }

    public void setZips(List<EmojiBean> zips) {
        Zips = zips;
    }
}
