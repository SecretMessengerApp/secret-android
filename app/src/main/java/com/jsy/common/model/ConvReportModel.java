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
package com.jsy.common.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;


public class ConvReportModel implements Serializable {

    private boolean ok;
    @SerializedName(value = "error_code", alternate = {"code"})
    private int error_code;
    private String description;
    @SerializedName(value = "result", alternate = {"data"})
    private ConvReportEntity result;

    public boolean isOk() {
        return (ok || error_code == 200);
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public int getError_code() {
        return error_code;
    }

    public void setError_code(int error_code) {
        this.error_code = error_code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ConvReportEntity getResult() {
        return result;
    }

    public void setResult(ConvReportEntity result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "ConvReportModel{" +
            "ok=" + ok +
            ", error_code=" + error_code +
            ", description='" + description + '\'' +
            ", result=" + (null == result ? "" : result.toString()) +
            '}';
    }

    public class ConvReportEntity implements Serializable {
        private String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @Override
        public String toString() {
            return "ConvReportEntity{" +
                "url='" + url + '\'' +
                '}';
        }
    }
}
