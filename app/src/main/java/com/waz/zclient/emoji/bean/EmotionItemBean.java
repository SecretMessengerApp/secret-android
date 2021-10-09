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

public class EmotionItemBean extends EmojiCell {

    @DatabaseField(columnName = "gif_url")
    private String url;

    @DatabaseField(columnName = "folder_id")
    private String folderId;

    @DatabaseField(columnName = "folder_name")
    private String folderName;

    @DatabaseField(columnName = "folder_icon")
    private String folderIcon;

    public EmotionItemBean(){

    }
    public EmotionItemBean(EmojiCell cell,String url) {
        super(cell);
        this.url=url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderIcon() {
        return folderIcon;
    }

    public void setFolderIcon(String folderIcon) {
        this.folderIcon = folderIcon;
    }

    public static EmotionItemBean createRemoteUrlBean(String url) {
        return new EmotionItemBean(new EmojiCell("", ""), url);
    }

    @Override
    public String toString() {
        return "EmotionItemBean{" +
            "Url='" + url + '\'' +
            ", Name='" + Name + '\'' +
            ", File='" + File + '\'' +
            '}';
    }
}
