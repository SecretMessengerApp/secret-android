/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.model;

import java.io.Serializable;
import java.util.List;

public class SearchUserInfo implements Serializable {
    private String handle;
    private String asset;
    private String name;
    private String id;

    private String email;
    private String status;
    private String extid;

    private String locale;
    private int accent_id;
    private List<AssetInfo> assets;
    private String user_address;

    public SearchUserInfo() {

    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExtid() {
        return extid;
    }

    public void setExtid(String extid) {
        this.extid = extid;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public int getAccent_id() {
        return accent_id;
    }

    public void setAccent_id(int accent_id) {
        this.accent_id = accent_id;
    }

    public List<AssetInfo> getAssets() {
        return assets;
    }

    public void setAssets(List<AssetInfo> assets) {
        this.assets = assets;
    }

    public String getUser_address() {
        return user_address;
    }

    public void setUser_address(String user_address) {
        this.user_address = user_address;
    }

    public static SearchUserInfo initBy(String name, String id) {
        SearchUserInfo userInfo = new SearchUserInfo();
        userInfo.setName(name);
        userInfo.setId(id);
        return userInfo;
    }

    public static SearchUserInfo initBy(String name, String id, String handle, String asset) {
        SearchUserInfo userInfo = new SearchUserInfo();
        userInfo.setName(name);
        userInfo.setId(id);
        userInfo.setHandle(handle);
        userInfo.setAsset(asset);
        return userInfo;
    }

    public String userAvatar() {
        int size = null == assets ? 0 : assets.size();
        if (size == 0) {
            return null;
        }
        for (int i = 0; i < size; i++) {
            AssetInfo info = assets.get(i);
            if (null != info && "image".equalsIgnoreCase(info.type) && "preview".equalsIgnoreCase(info.size)) {
                return info.key;
            }
        }
        AssetInfo info = assets.get(0);
        return null != info ? info.key : null;
    }

    public static class AssetInfo implements Serializable {
        private String size;
        private String key;
        private String type;

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
