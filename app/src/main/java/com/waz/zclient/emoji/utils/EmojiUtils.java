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
package com.waz.zclient.emoji.utils;

import android.content.Context;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.target.Target;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.stmt.QueryBuilder;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.R;
import com.waz.zclient.ZApplication;
import com.waz.zclient.emoji.Constants;
import com.waz.zclient.emoji.bean.EmojiBean;
import com.waz.zclient.emoji.bean.EmojiCell;
import com.waz.zclient.emoji.bean.EmotionItemBean;
import com.waz.zclient.emoji.bean.GifSavedItem;
import com.waz.zclient.utils.SpUtils;

import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class EmojiUtils {

    public static String getEmojiUrl(String...names){
        StringBuilder builder=new StringBuilder();
        builder.append(Constants.EMOJI_BASE_URL);
        for(String name:names){
            if(!TextUtils.isEmpty(name)){
                builder.append(name);
                builder.append("/");
            }
        }
        String str=builder.toString();
        if(str.endsWith("/")){
            str=str.substring(0,str.length()-1);
        }
        return str;
    }

    public static List<EmotionItemBean> convert2EmojiList(EmojiBean res,int len){
        List<EmotionItemBean>list=new ArrayList<>();
        List<EmojiCell>emojis=res.getEmojis();
        if(emojis!=null && emojis.size()>0) {
            int size = len > -1 ? len : emojis.size();
            if (size > emojis.size()) {
                size = emojis.size();
            }
            for (int i = 0; i < size; i++) {
                EmotionItemBean item = new EmotionItemBean(emojis.get(i), getEmojiUrl(res.getFolder(), emojis.get(i).getFile()));
                item.setFolderId(String.valueOf(res.getId()));
                item.setFolderName(res.getName());
                item.setFolderIcon(res.getIcon());
                list.add(item);
            }
        }
        return list;
    }


    public static List<EmojiBean> getAllDbEmoji(){
        List<EmojiBean>list=null;
        try {
            QueryBuilder builder=ZApplication.getInstance().getOrmliteDbHelper().getEmojiBeanDao().queryBuilder();
            builder.where().eq("userId", SpUtils.getUserId(ZApplication.getInstance()));
            builder.orderByRaw("Id asc");
            list= builder.query();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if(list!=null && list.size()>0){
            Type type = new TypeToken<List<EmojiCell>>(){}.getType();
            for(EmojiBean emojiBean:list){
                List<EmojiCell>ll=new Gson().fromJson(emojiBean.getItems(),type);
                emojiBean.setGifs(ll);
            }
        }
        return list;
    }

    public static int updateAllDbEmoji(List<EmojiBean>remoteEmojiList){
        List<EmojiBean>dbEmojiList=getAllDbEmoji();
        if(dbEmojiList!=null && dbEmojiList.size()>0) {
            if(dbEmojiList.containsAll(remoteEmojiList)){
                LogUtils.d("JACK8","containsAll not need update");
                return 0;
            }
            else {
                for (int i = 0; i < remoteEmojiList.size(); i++) {
                    EmojiBean remoteEmoji = remoteEmojiList.get(i);
                    if (dbEmojiList.contains(remoteEmoji)) {
                        remoteEmojiList.remove(i);
                        i--;
                    }
                }
            }
        }
        if(remoteEmojiList!=null && remoteEmojiList.size()>0){
            for (int i = 0; i <remoteEmojiList.size() ; i++) {
                remoteEmojiList.get(i).setUserId(SpUtils.getUserId(ZApplication.getInstance()));
                remoteEmojiList.get(i).setItems(new Gson().toJson(remoteEmojiList.get(i).getEmojis()));
            }
            return ZApplication.getInstance().getOrmliteDbHelper().getEmojiBeanDao().create(remoteEmojiList);
        }
        return 0;

    }

    public static EmojiBean getDbEmojiById(String folderId){
        List<EmojiBean>dbEmojiList=getAllDbEmoji();
        if(dbEmojiList!=null && dbEmojiList.size()>0){
            for(EmojiBean bean:dbEmojiList){
                if(String.valueOf(bean.getId()).equals(folderId)){
                    return  bean;
                }
            }
        }
        return null;
    }

    public static List<EmojiBean> getDbEmojiByDefault(boolean isDefault){
        List<EmojiBean>list=null;
        try {
            QueryBuilder builder=ZApplication.getInstance().getOrmliteDbHelper().getEmojiBeanDao().queryBuilder();
            builder.where().eq("userId", SpUtils.getUserId(ZApplication.getInstance()))
                .and()
                .eq("isDefault",isDefault);
            builder.orderByRaw("Id asc");
            list= builder.query();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if(list!=null && list.size()>0){
            Type type = new TypeToken<List<EmojiCell>>(){}.getType();
            for(EmojiBean emojiBean:list){
                List<EmojiCell>ll=new Gson().fromJson(emojiBean.getItems(),type);
                emojiBean.setGifs(ll);
            }
        }
        return list;
    }

    public static List<EmojiBean> getDefaultEmoji(){
        return getDbEmojiByDefault(true);
    }

    public static int getDefaultEmojiSize(){
        List<EmojiBean>defaultEmojiList=getDefaultEmoji();
        if(defaultEmojiList!=null && defaultEmojiList.size()>0) {
           return defaultEmojiList.size();
        }
        return 0;
    }

    public static List<EmojiBean> getDbEmoji(){
        List<EmojiBean>list=null;
        try {
              QueryBuilder builder=ZApplication.getInstance().getOrmliteDbHelper().getEmojiBeanDao().queryBuilder();
              builder.where().eq("userId", SpUtils.getUserId(ZApplication.getInstance()))
                  .and()
                  .eq("isDefault",false)
                  .and()
                  .eq("local",true);
              builder.orderByRaw("sort desc,Id asc");
              list= builder.query();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if(list!=null && list.size()>0){
            Type type = new TypeToken<List<EmojiCell>>(){}.getType();
            for(EmojiBean emojiBean:list){
                List<EmojiCell>ll=new Gson().fromJson(emojiBean.getItems(),type);
                emojiBean.setGifs(ll);
            }
        }
        return list;

    }

    public static List<EmojiBean> getAllLocalEmoji(){
        List<EmojiBean>list=new ArrayList<>();
        List<EmojiBean>defaultEmojiList=getDefaultEmoji();
        if(defaultEmojiList!=null && defaultEmojiList.size()>0) {
            list.addAll(defaultEmojiList);
        }
        List<EmojiBean>dbEmojiList=getDbEmoji();
        if(dbEmojiList!=null && dbEmojiList.size()>0){
            list.addAll(dbEmojiList);
        }
        return list;
    }

    public static int AddEmoji2Db(EmojiBean emojiBean){
        emojiBean.setLocal(true);
        emojiBean.setSort(EmojiUtils.getNextSort());
        return ZApplication.getInstance().getOrmliteDbHelper().getEmojiBeanDao().update(emojiBean);
    }

    public static int RemoveEmojiInDb(EmojiBean emojiBean){
        emojiBean.setLocal(false);
        emojiBean.setSort(0);
        return ZApplication.getInstance().getOrmliteDbHelper().getEmojiBeanDao().update(emojiBean);
    }

    public static int updateEmojiDb(EmojiBean emojiBean) {
        return ZApplication.getInstance().getOrmliteDbHelper().getEmojiBeanDao().update(emojiBean);
    }


    public static int getNextSort() {
        int maxId=0;
        GenericRawResults rawResults = null;
        try {
            rawResults = ZApplication.getInstance().getOrmliteDbHelper().getEmojiBeanDao().queryRaw("select max(sort) as maxId from EmojiBean");
            List results = rawResults.getResults();
            if(results!=null && results.size()>0){
                String[] resultArray = (String[])results.get(0);
                maxId=Integer.parseInt(resultArray[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return maxId+1;
    }

    public static boolean isGifFavorite(GifSavedItem gifSavedItem){
        return !gifSavedItem.isRecently() && !TextUtils.isEmpty(gifSavedItem.getMD5());
    }

    public static boolean isAnimatedImage(String url){
        return !TextUtils.isEmpty(url) && url.endsWith(Constants.SUFFIX_TGS);
    }

    public static boolean isGifImage(String url){
        return !TextUtils.isEmpty(url) && url.endsWith(Constants.SUFFIX_GIF);
    }

    public static void loadSticker(final Context context,final EmojiBean bean,final RLottieImageView imageView, final int width, final int height){
        loadImage(context,getEmojiUrl(bean.getFolder(),bean.getIcon()),imageView,width,height);
    }

    public static void loadSticker(final Context context,final EmotionItemBean bean,final RLottieImageView imageView, final int width, final int height){
        if(bean instanceof GifSavedItem){
            GifSavedItem gifSavedItem= (GifSavedItem)bean;
            if (TextUtils.isEmpty(gifSavedItem.getFolderName())) {
                Glide.with(context).load(gifSavedItem.getImage()).placeholder(R.drawable.emoji_placeholder).into(imageView);
                return;
            }
        }
        loadImage(context,bean.getUrl(),imageView,width,height);
    }

    public static void loadImage(final Context context, final String url, final RLottieImageView imageView, final int width, final int height){
        try {
              if(isAnimatedImage(url)) {
                  getRLottieDrawable(context, url, width, height).subscribe(new Consumer<RLottieDrawable>() {
                      @Override
                      public void accept(RLottieDrawable drawable) throws Exception {
                          if (drawable != null) {
                              imageView.setAutoRepeat(true);
                              imageView.setAnimation(drawable);
                              imageView.playAnimation();
                          }
                      }
                  });
              }
              else if(isGifImage(url)) {
                  Glide.with(context).load(url).placeholder(R.drawable.emoji_placeholder).diskCacheStrategy(DiskCacheStrategy.DATA).into(imageView);
              }
              else{
                  Glide.with(context).load(url).into(imageView);
              }

        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e("JACK8","error:"+e.getMessage());
        }
    }

    public static Observable<RLottieDrawable> getRLottieDrawable(final Context context,final String url, final int width, final int height){
            return Observable.just(url).flatMap(new Function<String, ObservableSource<RLottieDrawable>>() {
                @Override
                public ObservableSource<RLottieDrawable> apply(String url) {
                    FutureTarget<File> target= Glide.with(context).load(url).downloadOnly(Target.SIZE_ORIGINAL,Target.SIZE_ORIGINAL);
                    try {
                        File file = target.get();
                        RLottieDrawable drawable=new RLottieDrawable(file,width,height,false,false);
                        return Observable.just(drawable);
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                        LogUtils.e("JACK8","error:"+e.getMessage());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        LogUtils.e("JACK8","error:"+e.getMessage());
                    }
                    return Observable.empty();

                }
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
        }

}
