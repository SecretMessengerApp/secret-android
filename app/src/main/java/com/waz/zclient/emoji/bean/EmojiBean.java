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

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.Serializable;
import java.util.List;

@DatabaseTable(tableName = "EmojiBean")
public class EmojiBean implements Serializable {

    @DatabaseField(columnName = "id", generatedId = true)
    private int id;

    @DatabaseField(columnName = "packageId")
    private int Id;
    @DatabaseField(columnName = "Name")
    private String Name;
    @DatabaseField(columnName = "Icon")
    private String Icon;

    @DatabaseField(columnName ="Folder")
    private String Folder;

    private List<EmojiCell> Emojis;

    @DatabaseField(columnName ="items")
    private String items;

    @DatabaseField(columnName = "userId")
    private String userId;

    @DatabaseField(columnName = "sort")
    private int sort;

    @DatabaseField(columnName = "local")
    private boolean local;

    @SerializedName("Default")
    @DatabaseField(columnName = "isDefault")
    private boolean bDefault;

    public EmojiBean() {
    }

    public EmojiBean(int id){
        this.Id=id;
    }

    public int getId() {
        return Id;
    }

    public void setId(int id) {
        Id = id;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getIcon() {
        return Icon;
    }

    public void setIcon(String icon) {
        Icon = icon;
    }

    public List<EmojiCell> getEmojis() {
        return Emojis;
    }

    public void setGifs(List<EmojiCell> emojis) {
        this.Emojis = emojis;
    }

    public boolean isDefault() {
        return bDefault;
    }

    public void setDefault(boolean bDefault) {
        this.bDefault = bDefault;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }


    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
    }


    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public int getPrimaryKey(){
        return id;
    }

    public void setPrimaryKey(int id){
        this.id=id;
    }


    public String getFolder() {
        return Folder;
    }

    public void setFolder(String folder) {
        Folder = folder;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(this==obj){
            return true;
        }
        else if(obj!=null && this.getClass()==obj.getClass()){
            EmojiBean other=(EmojiBean)obj;
            return this.Id==other.Id;
        }
        return false;
    }

    public int getGifSize() {
        if(Emojis != null) {
            return Emojis.size();
        }
        return 0;
    }
}
