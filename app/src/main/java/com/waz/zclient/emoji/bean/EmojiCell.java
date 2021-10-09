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

import com.j256.ormlite.field.DatabaseField;

public class EmojiCell {

    @DatabaseField(columnName = "name")
    protected String Name;
    @DatabaseField(columnName = "file")
    protected String File;

    public EmojiCell(){

    }

    public EmojiCell(EmojiCell cell){
        this(cell.getName(),cell.getFile());
    }
    public EmojiCell(String name, String file) {
        this.Name = name;
        this.File = file;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        this.Name = name;
    }

    public String getFile() {
        return File;
    }

    public void setFile(String file) {
        this.File = file;
    }
}
