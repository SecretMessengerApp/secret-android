/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.jsy.common.acts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.jsy.common.fragment.SelectUserShareGroupFragment;
import com.jsy.common.listener.OnSelectUserDataListener;
import com.jsy.common.model.SearchUserInfo;
import com.waz.api.IConversation;
import com.waz.model.RConvId;
import com.waz.model.UserData;
import com.waz.model.UserId;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;

/**
 * Created by eclipse on 2018/12/21.
 */

public class GroupShareSelectUserActivity extends BaseActivity implements OnSelectUserDataListener {
    private final static String SHARE_URL = "share_url";
    public final static String CONVERSATION_TYPE = "conversation_type";
    public static final String CONVERSATION_CREATE = "conversation_create";

    public static void start(Context context, String linkUrl, RConvId rConvId, UserId creator, IConversation.Type convType) {
        if (context != null) {
            Intent intent = new Intent(context, GroupShareSelectUserActivity.class);
            intent.putExtra(SHARE_URL, linkUrl);
            intent.putExtra(RConvId.class.getSimpleName(), rConvId);
            if (null != creator) {
                intent.putExtra(CONVERSATION_CREATE, creator.str());
            }
            intent.putExtra(CONVERSATION_TYPE, convType.id);
            context.startActivity(intent);
        }
    }

    private RConvId rConvId;
    private String groupCreateId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_select_user);
        Intent intent = getIntent();
        String linkUrl = intent.getStringExtra(SHARE_URL);
        rConvId = (RConvId) intent.getSerializableExtra(RConvId.class.getSimpleName());
        groupCreateId = intent.getStringExtra(CONVERSATION_CREATE);
        IConversation.Type convType = IConversation.Type.withId(intent.getIntExtra(CONVERSATION_TYPE, IConversation.Type.GROUP.id));
        Fragment fragment = null;
        String tag = null;
//        if (convType == IConversation.Type.THROUSANDS_GROUP) {
//            fragment = SelectUserShareThousandsFragment.newInstance(rConvId, linkUrl);
//            tag = SelectUserShareThousandsFragment.TAG();
//        } else if (convType == IConversation.Type.GROUP) {
            fragment = SelectUserShareGroupFragment.newInstance(linkUrl);
            tag = SelectUserShareGroupFragment.TAG();
//        }
        if (null != fragment) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment, tag)
                .commit();
        }
    }

    public boolean canUseSwipeBackLayout() {
        return true;
    }

    @Override
    public void onSelectData(SearchUserInfo userData) {

    }

    @Override
    public void onNormalData(UserData userData) {

    }

    @Override
    public void onThousandsData(String userId, String userName, String asset) {

    }
}
