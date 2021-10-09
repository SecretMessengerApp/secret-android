/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.common.httpapi;

import com.waz.zclient.emoji.bean.EmojiResponse;

import java.util.Collections;

public class EmojiGifService {
    private static volatile EmojiGifService service;

    public static EmojiGifService getInstance() {
        if(service == null) {
            synchronized(EmojiGifService.class) {
                if(service == null) {
                    service = new EmojiGifService();
                }
            }
        }
        return service;
    }

    public void getEmojiGifs(OnHttpListener<EmojiResponse> listener) {
        NormalServiceAPI.getInstance().get(EmojiGifApiCode.PATH_EMOJI_GIF_LIST, Collections.emptyMap(), listener);
    }

}
