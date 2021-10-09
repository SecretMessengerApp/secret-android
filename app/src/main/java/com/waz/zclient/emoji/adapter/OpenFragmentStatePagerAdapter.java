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
package com.waz.zclient.emoji.adapter;

import android.os.Bundle;
import android.os.Parcelable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public abstract class OpenFragmentStatePagerAdapter<T> extends PagerAdapter {
    private static final String TAG = "FragmentStatePagerAdapt";
    private static final boolean DEBUG = true;

    private final FragmentManager mFragmentManager;
    private FragmentTransaction mCurTransaction = null;

    private ArrayList<Fragment.SavedState> mSavedState = new ArrayList<>();
    private ArrayList<ItemInfo<T>> mItemInfos = new ArrayList<>();
    private Fragment mCurrentPrimaryItem = null;
    private boolean mNeedProcessCache = false;

    public OpenFragmentStatePagerAdapter(FragmentManager fm) {
        this.mFragmentManager = fm;
    }

    /**
     * Return the Fragment associated with a specified position.
     */
    public abstract Fragment getItem(int position);


    @Override
    public void startUpdate(ViewGroup container) {
        if (container.getId() == View.NO_ID) {
            throw new IllegalStateException("ViewPager with adapter " + this
                    + " requires a view id");
        }
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        // If we already have this item instantiated, there is nothing
        // to do.  This can happen when we are restoring the entire pager
        // from its saved state, where the fragment manager has already
        // taken care of restoring the fragments we previously had instantiated.
        Log.d(TAG, "instantiateItem() called with:position = [" + position + "]");
        if (mItemInfos.size() > position) {
            ItemInfo ii = mItemInfos.get(position);
            if (ii != null) {
                if (ii.position == position) {
                    return ii;
                } else {
                    checkProcessCacheChanged();
                }
            }
        }

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        Fragment fragment = getItem(position);
        if (DEBUG) Log.v(TAG, "Adding item #" + position + ": f=" + fragment);
        if (mSavedState.size() > position) {
            Fragment.SavedState fss = mSavedState.get(position);
            if (fss != null) {
                fragment.setInitialSavedState(fss);
            }
        }
        while (mItemInfos.size() <= position) {
            mItemInfos.add(null);
        }
        fragment.setMenuVisibility(false);
        fragment.setUserVisibleHint(false);
        ItemInfo<T> iiNew = new ItemInfo<>(fragment, getItemData(position), position);
        mItemInfos.set(position, iiNew);
        mCurTransaction.add(container.getId(), fragment);

        return iiNew;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        Log.d(TAG, "destroyItem() called with:position = [" + position + "]");
        ItemInfo ii = (ItemInfo) object;

        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }
        while (mSavedState.size() <= position) {
            mSavedState.add(null);
        }
        mSavedState.set(position, ii.fragment.isAdded()
                ? mFragmentManager.saveFragmentInstanceState(ii.fragment) : null);
        mItemInfos.set(position, null);

        mCurTransaction.remove(ii.fragment);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        ItemInfo ii = (ItemInfo) object;
        Fragment fragment = ii.fragment;
        if (fragment != mCurrentPrimaryItem) {
            if (mCurrentPrimaryItem != null) {
                mCurrentPrimaryItem.setMenuVisibility(false);
                mCurrentPrimaryItem.setUserVisibleHint(false);
            }
            if (fragment != null) {
                fragment.setMenuVisibility(true);
                fragment.setUserVisibleHint(true);
            }
            mCurrentPrimaryItem = fragment;
        }
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        if (mCurTransaction != null) {
            mCurTransaction.commitNowAllowingStateLoss();
            mCurTransaction = null;
        }
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        Fragment fragment = ((ItemInfo) object).fragment;
        return fragment.getView() == view;
    }

    @Override
    public int getItemPosition(Object object) {
        Log.d(TAG, "getItemPosition() called with: object = [" + object + "]");
        mNeedProcessCache = true;
        ItemInfo<T> itemInfo = (ItemInfo) object;
        int oldPosition = mItemInfos.indexOf(itemInfo);
        if (oldPosition >= 0) {
            T oldData = itemInfo.data;
            T newData = getItemData(oldPosition);
            if (dataEquals(oldData, newData)) {
                return POSITION_UNCHANGED;
            } else {
                ItemInfo<T> oldItemInfo = mItemInfos.get(oldPosition);
                int oldDataNewPosition = getDataPosition(oldData);
                if (oldDataNewPosition < 0) {
                    oldDataNewPosition = POSITION_NONE;
                }
                if (oldItemInfo != null) {
                    oldItemInfo.position = oldDataNewPosition;
                }
                Log.d(TAG,"oldposition:"+oldPosition+",newposition:"+oldDataNewPosition);
                return oldDataNewPosition;
            }

        }

        return POSITION_UNCHANGED;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        checkProcessCacheChanged();
    }

    private void checkProcessCacheChanged() {
        if (!mNeedProcessCache) return;
        mNeedProcessCache = false;
        ArrayList<ItemInfo<T>> pendingItemInfos = new ArrayList<>(mItemInfos.size());
        for (int i = 0; i < mItemInfos.size(); i++) {
            pendingItemInfos.add(null);
        }
        for (ItemInfo<T> itemInfo : mItemInfos) {
            if (itemInfo != null) {
                if (itemInfo.position >= 0) {
                    while (pendingItemInfos.size() <= itemInfo.position) {
                        pendingItemInfos.add(null);
                    }
                    pendingItemInfos.set(itemInfo.position, itemInfo);
                }
            }
        }
        mItemInfos = pendingItemInfos;
    }

    @Override
    public Parcelable saveState() {
        Bundle state = null;
        if (mSavedState.size() > 0) {
            state = new Bundle();
            Fragment.SavedState[] fss = new Fragment.SavedState[mSavedState.size()];
            mSavedState.toArray(fss);
            state.putParcelableArray("states", fss);
        }
        for (int i = 0; i < mItemInfos.size(); i++) {
            ItemInfo itemInfo=mItemInfos.get(i);
            if(itemInfo!=null) {
                Fragment f = itemInfo.fragment;
                if (f != null && f.isAdded()) {
                    if (state == null) {
                        state = new Bundle();
                    }
                    String key = "f" + i;
                    mFragmentManager.putFragment(state, key, f);
                }
            }
        }
        return state;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        if (state != null) {
            Bundle bundle = (Bundle) state;
            bundle.setClassLoader(loader);
            Parcelable[] fss = bundle.getParcelableArray("states");
            mSavedState.clear();
            mItemInfos.clear();
            if (fss != null) {
                for (int i = 0; i < fss.length; i++) {
                    mSavedState.add((Fragment.SavedState) fss[i]);
                }
            }
            Iterable<String> keys = bundle.keySet();
            for (String key : keys) {
                if (key.startsWith("f")) {
                    int index = Integer.parseInt(key.substring(1));
                    Fragment f = mFragmentManager.getFragment(bundle, key);
                    if (f != null) {
                        while (mItemInfos.size() <= index) {
                            mItemInfos.add(null);
                        }
                        f.setMenuVisibility(false);
                        ItemInfo<T> iiNew = new ItemInfo<>(f, getItemData(index), index);
                        mItemInfos.set(index, iiNew);
                    } else {
                        Log.w(TAG, "Bad fragment at key " + key);
                    }
                }
            }
        }
    }

    public Fragment getCurrentPrimaryItem() {
        return mCurrentPrimaryItem;
    }

    public Fragment getFragmentByPosition(int position) {
        if (position < 0 || position >= mItemInfos.size()) return null;
        if(mItemInfos.get(position)==null)return null;
        return mItemInfos.get(position).fragment;
    }

    abstract T getItemData(int position);

    abstract boolean dataEquals(T oldData, T newData);

    abstract int getDataPosition(T data);

    static class ItemInfo<T> {
        Fragment fragment;
        T data;
        int position;

        public ItemInfo(Fragment fragment, T data, int position) {
            this.fragment = fragment;
            this.data = data;
            this.position = position;
        }
    }
}
