/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;


public class ThousandGroupUserModel implements Parcelable, Serializable {

    private boolean has_more;
    private ArrayList<ThousandGroupUserItemModel> conversations;


    public boolean isHas_more() {
        return has_more;
    }

    public void setHas_more(boolean has_more) {
        this.has_more = has_more;
    }

    public ArrayList<ThousandGroupUserItemModel> getConversations() {
        return conversations;
    }

    public void setConversations(ArrayList<ThousandGroupUserItemModel> conversations) {
        this.conversations = conversations;
    }

    public ThousandGroupUserModel() {
    }

    public ThousandGroupUserModel(Parcel source) {
        this.has_more = source.readInt() != 0;
        this.conversations = source.readArrayList(ThousandGroupUserModel.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(has_more ? 1 : 0);
        dest.writeList(conversations);
    }

    public static Parcelable.Creator<ThousandGroupUserModel> CREATOR = new Parcelable.Creator<ThousandGroupUserModel>() {
        @Override
        public ThousandGroupUserModel createFromParcel(Parcel source) {
            return new ThousandGroupUserModel(source);
        }

        @Override
        public ThousandGroupUserModel[] newArray(int size) {
            return new ThousandGroupUserModel[size];
        }
    };

    public static class ThousandGroupUserItemModel implements Serializable {

        private String asset;
        private String alias_name_ref;
        private String name;
        private String id;
        private String handle;
        private boolean isSelect;

        public ThousandGroupUserItemModel() {
        }

        public String getAsset() {
            return asset;
        }

        public void setAsset(String asset) {
            this.asset = asset;
        }

        public String getAlias_name_ref() {
            return alias_name_ref;
        }

        public void setAlias_name_ref(String alias_name_ref) {
            this.alias_name_ref = alias_name_ref;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getHandle() {
            return handle;
        }

        public void setHandle(String handle) {
            this.handle = handle;
        }

        public boolean isSelect() {
            return isSelect;
        }

        public void setSelect(boolean select) {
            isSelect = select;
        }
    }

    /*
    {
        "has_more": false,
            "conversations": [
        {
            "hidden_ref": null,
                "status": 0,
                "auto_reply": 0,
                "service": null,
                "otr_muted_ref": null,
                "alias_name_ref": null,
                "asset": "3-1-3e12311a-a479-4f86-9c79-f376847cdf3d",
                "alias_name": false,
                "status_time": "1970-01-01T00:00:00.000Z",
                "auto_reply_ref": null,
                "name": "bg10009",
                "hidden": false,
                "status_ref": "0.0",
                "id": "5ea2ff9f-789a-483a-84ac-bee14695e6f2",
                "otr_archived": false,
                "otr_muted": false,
                "otr_archived_ref": null
        },
        */

}
