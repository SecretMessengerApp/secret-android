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
package com.jsy.common.model.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.waz.zclient.emoji.bean.EmojiBean;
import com.waz.zclient.emoji.bean.GifSavedItem;

import java.sql.SQLException;

public class OrmliteDbHelper extends OrmLiteSqliteOpenHelper {

    private static final String DB_NAME = "conversationPlus.db";

    private static final int VERSION = 18;

    public OrmliteDbHelper(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource) {
        try {
            TableUtils.createTableIfNotExists(connectionSource, GifSavedItem.class);
            TableUtils.createTableIfNotExists(connectionSource, EmojiBean.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource, int oldVersion,
                          int newVersion) {
    }

    private RuntimeExceptionDao<GifSavedItem, Long> gifSavedItemDao;
    public RuntimeExceptionDao<GifSavedItem, Long> getGifSavedItemDao(){
        if(gifSavedItemDao == null) {
            gifSavedItemDao = getRuntimeExceptionDao(GifSavedItem.class);
        }
        return gifSavedItemDao;
    }

    private RuntimeExceptionDao<EmojiBean, Long> emojiBeanDao;
    public RuntimeExceptionDao<EmojiBean, Long> getEmojiBeanDao(){
        if(emojiBeanDao == null) {
            emojiBeanDao = getRuntimeExceptionDao(EmojiBean.class);
        }
        return emojiBeanDao;
    }

    @Override
    public void close() {
        super.close();
        emojiBeanDao=null;
        gifSavedItemDao = null;
    }
}
