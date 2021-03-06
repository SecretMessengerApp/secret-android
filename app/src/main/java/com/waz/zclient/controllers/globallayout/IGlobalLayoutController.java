/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.controllers.globallayout;

import android.app.Activity;
import android.view.View;
import com.waz.zclient.controllers.navigation.Page;

public interface IGlobalLayoutController {
    void tearDown();

    void addKeyboardVisibilityObserver(KeyboardVisibilityObserver keyboardVisibilityObserver);

    void removeKeyboardVisibilityObserver(KeyboardVisibilityObserver keyboardVisibilityObserver);

    void addKeyboardHeightObserver(KeyboardHeightObserver keyboardHeightObserver);

    void removeKeyboardHeightObserver(KeyboardHeightObserver keyboardHeightObserver);

    void setSoftInputModeForPage(Page page);

    int getSoftInputModeForPage(Page page);

    boolean isKeyboardVisible();

    void setActivity(Activity activity);

    void keepScreenAwake();

    void resetScreenAwakeState();
}
