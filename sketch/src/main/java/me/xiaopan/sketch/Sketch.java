/*
 * Copyright (C) 2013 Peng fei Pan <sky@xiaopan.me>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import me.xiaopan.sketch.drawable.BindFixedRecycleBitmapDrawable;
import me.xiaopan.sketch.feature.ImagePreprocessor;
import me.xiaopan.sketch.request.DisplayHelper;
import me.xiaopan.sketch.request.DisplayOptions;
import me.xiaopan.sketch.request.DisplayParams;
import me.xiaopan.sketch.request.DisplayRequest;
import me.xiaopan.sketch.request.DownloadHelper;
import me.xiaopan.sketch.request.DownloadListener;
import me.xiaopan.sketch.request.DownloadOptions;
import me.xiaopan.sketch.request.ImageViewInterface;
import me.xiaopan.sketch.request.LoadHelper;
import me.xiaopan.sketch.request.LoadListener;
import me.xiaopan.sketch.request.LoadOptions;
import me.xiaopan.sketch.request.UriScheme;
import me.xiaopan.sketch.util.SketchUtils;

/**
 * 图片加载器，可以从网络或者本地加载图片，并且支持自动清除缓存
 */
public class Sketch {
    public static final String TAG = "Sketch";

    private static Sketch instance;
    private static boolean debugMode;    //调试模式，在控制台输出日志
    private static Map<Enum<?>, Object> optionsMap;

    private Configuration configuration;

    private Sketch(Context context) {
        Log.i(TAG, SketchUtils.concat("Sketch", " ", BuildConfig.BUILD_TYPE, " ", BuildConfig.VERSION_NAME, "(", BuildConfig.VERSION_CODE, ")"));
        this.configuration = new Configuration(context);
    }

    public static Sketch with(Context context) {
        if (instance == null) {
            synchronized (Sketch.class) {
                if (instance == null) {
                    instance = new Sketch(context);
                }
            }
        }
        return instance;
    }

    /**
     * 获取配置
     */
    public Configuration getConfiguration() {
        return configuration;
    }


    /**
     * 下载图片
     *
     * @param uri 图片Uri，支持以下几种
     *            <blockQuote>“http://site.com/image.png“  // from Web
     *            <br>“https://site.com/image.png“ // from Web
     *            </blockQuote>
     */
    @SuppressWarnings("unused")
    public DownloadHelper download(String uri, DownloadListener downloadListener) {
        return configuration.getHelperFactory().getDownloadHelper(this, uri).listener(downloadListener);
    }


    /**
     * 根据URI加载图片
     *
     * @param uri 图片Uri，支持以下几种
     *            <blockQuote>"http://site.com/image.png"; // from Web
     *            <br>"https://site.com/image.png"; // from Web
     *            <br>"file:///mnt/sdcard/image.png"; // from SD card
     *            <br>"/mnt/sdcard/image.png"; // from SD card
     *            <br>"/mnt/sdcard/app.apk"; // from SD card apk file
     *            <br>"content://media/external/audio/albumart/13"; // from content provider
     *            <br>"asset://image.png"; // from assets
     *            <br>"drawable://" + R.drawable.image; // from drawables (only images, non-9patch)
     *            </blockQuote>
     */
    public LoadHelper load(String uri, LoadListener loadListener) {
        return configuration.getHelperFactory().getLoadHelper(this, uri).listener(loadListener);
    }

    /**
     * 加载Asset中的图片
     */
    @SuppressWarnings("unused")
    public LoadHelper loadFromAsset(String fileName, LoadListener loadListener) {
        return configuration.getHelperFactory().getLoadHelper(this, UriScheme.ASSET.createUri(fileName)).listener(loadListener);
    }

    /**
     * 加载资源中的图片
     */
    @SuppressWarnings("unused")
    public LoadHelper loadFromResource(int drawableResId, LoadListener loadListener) {
        return configuration.getHelperFactory().getLoadHelper(this, UriScheme.DRAWABLE.createUri(String.valueOf(drawableResId))).listener(loadListener);
    }

    /**
     * 加载URI指向的图片
     */
    @SuppressWarnings("unused")
    public LoadHelper loadFromURI(Uri uri, LoadListener loadListener) {
        return configuration.getHelperFactory().getLoadHelper(this, uri.toString()).listener(loadListener);
    }

    /**
     * 加载已安装APP的图标
     */
    @SuppressWarnings("unused")
    public LoadHelper loadInstalledAppIcon(String packageName, int versionCode, LoadListener loadListener) {
        return configuration.getHelperFactory().getLoadHelper(this, ImagePreprocessor.createInstalledAppIconUri(packageName, versionCode)).listener(loadListener);
    }


    /**
     * 显示图片
     *
     * @param uri 图片Uri，支持以下几种
     *            <blockQuote>"http://site.com/image.png"; // from Web
     *            <br>"https://site.com/image.png"; // from Web
     *            <br>"file:///mnt/sdcard/image.png"; // from SD card
     *            <br>"/mnt/sdcard/image.png"; // from SD card
     *            <br>"/mnt/sdcard/app.apk"; // from SD card apk file
     *            <br>"content://media/external/audio/albumart/13"; // from content provider
     *            <br>"asset://image.png"; // from assets
     *            <br>"drawable://" + R.drawable.image; // from drawables (only images, non-9patch)
     *            </blockQuote>
     */
    public DisplayHelper display(String uri, ImageViewInterface imageViewInterface) {
        return configuration.getHelperFactory().getDisplayHelper(this, uri, imageViewInterface);
    }

    /**
     * 显示Asset中的图片
     */
    public DisplayHelper displayFromAsset(String fileName, ImageViewInterface imageViewInterface) {
        return configuration.getHelperFactory().getDisplayHelper(this, UriScheme.ASSET.createUri(fileName), imageViewInterface);
    }

    /**
     * 显示资源中的图片
     */
    public DisplayHelper displayFromResource(int drawableResId, ImageViewInterface imageViewInterface) {
        return configuration.getHelperFactory().getDisplayHelper(this, UriScheme.DRAWABLE.createUri(String.valueOf(drawableResId)), imageViewInterface);
    }

    /**
     * 显示URI指向的图片
     */
    public DisplayHelper displayFromURI(Uri uri, ImageViewInterface imageViewInterface) {
        return configuration.getHelperFactory().getDisplayHelper(this, uri != null ? uri.toString() : null, imageViewInterface);
    }

    /**
     * 显示已安装APP的图标
     */
    public DisplayHelper displayInstalledAppIcon(String packageName, int versionCode, ImageViewInterface imageViewInterface) {
        return configuration.getHelperFactory().getDisplayHelper(this, ImagePreprocessor.createInstalledAppIconUri(packageName, versionCode), imageViewInterface);
    }

    /**
     * 显示图片，主要用于配合SketchImageView兼容RecyclerView
     */
    public DisplayHelper display(DisplayParams displayParams, ImageViewInterface imageViewInterface) {
        return configuration.getHelperFactory().getDisplayHelper(this, displayParams, imageViewInterface);
    }


    /**
     * 取消
     *
     * @return true：当前ImageView有正在执行的任务并且取消成功；false：当前ImageView没有正在执行的任务
     */
    public static boolean cancel(ImageViewInterface imageViewInterface) {
        final DisplayRequest displayRequest = BindFixedRecycleBitmapDrawable.findDisplayRequest(imageViewInterface);
        if (displayRequest != null && !displayRequest.isFinished()) {
            displayRequest.cancel();
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取下载选项
     */
    public static DownloadOptions getDownloadOptions(Enum<?> optionsName) {
        return optionsMap != null ? (DownloadOptions) optionsMap.get(optionsName) : null;
    }

    /**
     * 获取加载选项
     */
    public static LoadOptions getLoadOptions(Enum<?> optionsName) {
        return optionsMap != null ? (LoadOptions) optionsMap.get(optionsName) : null;
    }

    /**
     * 获取显示选项
     */
    public static DisplayOptions getDisplayOptions(Enum<?> optionsName) {
        return optionsMap != null ? (DisplayOptions) optionsMap.get(optionsName) : null;
    }

    /**
     * 安装选项Map
     */
    private static void installOptionsMap() {
        if (optionsMap == null) {
            synchronized (Sketch.class) {
                if (optionsMap == null) {
                    optionsMap = new HashMap<Enum<?>, Object>();
                }
            }
        }
    }

    /**
     * 放入下载选项
     */
    @SuppressWarnings("unused")
    public static void putOptions(Enum<?> optionsName, DownloadOptions options) {
        installOptionsMap();
        optionsMap.put(optionsName, options);
    }

    /**
     * 放入加载选项
     */
    public static void putOptions(Enum<?> optionsName, LoadOptions options) {
        installOptionsMap();
        optionsMap.put(optionsName, options);
    }

    /**
     * 放入显示选项
     */
    public static void putOptions(Enum<?> optionsName, DisplayOptions options) {
        installOptionsMap();
        optionsMap.put(optionsName, options);
    }

    /**
     * 是否开启调试模式，开启调试模式后会在控制台输出LOG
     */
    public static boolean isDebugMode() {
        return debugMode;
    }

    /**
     * 设置是否开启调试模式，开启调试模式后会在控制台输出LOG
     */
    public static void setDebugMode(boolean debugMode) {
        Sketch.debugMode = debugMode;
    }
}