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

import java.io.Serializable;

public class UpdateGroupSettingResponseModel implements Serializable {

    /**
     * conversation : 1b062690-376a-46ed-a8b4-76f64608f4b0
     * time : 2019-01-25T07:37:43.890Z
     * data : {"new_name":"bg20000","new_creator":"a4eec1a2-cdcd-4ac0-b223-cb7fbcfaa045","opt_name":"bg20005","opt_id":"1f915dfd-e239-4afc-bfe9-97b85b96d1f7"}
     * from : 1f915dfd-e239-4afc-bfe9-97b85b96d1f7
     * type : conversation.update
     */

    private String conversation;
    private String time;
    private DataBean data;
    private String eid;
    private String from;
    private String type;

    public String getConversation() {
        return conversation;
    }

    public void setConversation(String conversation) {
        this.conversation = conversation;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public DataBean getData() {
        return data;
    }

    public void setData(DataBean data) {
        this.data = data;
    }

    public String getEid() {
        return eid;
    }

    public void setEid(String eid) {
        this.eid = eid;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static class DataBean implements Serializable{
        /**
         * new_name : bg20000
         * new_creator : a4eec1a2-cdcd-4ac0-b223-cb7fbcfaa045
         * opt_name : bg20005
         * opt_id : 1f915dfd-e239-4afc-bfe9-97b85b96d1f7
         */

        private String new_name;
        private String new_creator;
        private String opt_name;
        private String opt_id;
        private boolean confirm;
        private boolean addright;
        private boolean url_invite;
        private boolean viewmem;
        private boolean memberjoin_confirm;
        private boolean enabled_edit_msg;
        private boolean show_memsum;

        public String getNew_name() {
            return new_name;
        }

        public void setNew_name(String new_name) {
            this.new_name = new_name;
        }

        public String getNew_creator() {
            return new_creator;
        }

        public void setNew_creator(String new_creator) {
            this.new_creator = new_creator;
        }

        public String getOpt_name() {
            return opt_name;
        }

        public void setOpt_name(String opt_name) {
            this.opt_name = opt_name;
        }

        public String getOpt_id() {
            return opt_id;
        }

        public void setOpt_id(String opt_id) {
            this.opt_id = opt_id;
        }

        public boolean isConfirm() {
            return confirm;
        }

        public void setConfirm(boolean confirm) {
            this.confirm = confirm;
        }

        public boolean isAddright() {
            return addright;
        }

        public void setAddright(boolean addright) {
            this.addright = addright;
        }

        public boolean isUrl_invite() {
            return url_invite;
        }

        public void setUrl_invite(boolean url_invite) {
            this.url_invite = url_invite;
        }

        public boolean isViewmem() {
            return viewmem;
        }

        public void setViewmem(boolean viewmem) {
            this.viewmem = viewmem;
        }

        public boolean isMemberjoin_confirm() {
            return memberjoin_confirm;
        }

        public void setMemberjoin_confirm(boolean memberjoin_confirm) {
            this.memberjoin_confirm = memberjoin_confirm;
        }

        public boolean isEnabled_edit_msg() {
            return enabled_edit_msg;
        }

        public void setEnabled_edit_msg(boolean enabled_edit_msg) {
            this.enabled_edit_msg = enabled_edit_msg;
        }

        public boolean isShow_memsum() {
            return show_memsum;
        }

        public void setShow_memsum(boolean show_memsum) {
            this.show_memsum = show_memsum;
        }
    }
}
