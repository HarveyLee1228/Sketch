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

package me.xiaopan.sketch.request;

import android.graphics.Bitmap;
import android.util.Log;

import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.decode.DecodeResult;
import me.xiaopan.sketch.drawable.RecycleBitmapDrawable;
import me.xiaopan.sketch.feature.ImagePreprocessor;
import me.xiaopan.sketch.feature.PreProcessResult;
import me.xiaopan.sketch.process.ImageProcessor;
import me.xiaopan.sketch.util.SketchUtils;

/**
 * 加载请求
 */
public class LoadRequest extends DownloadRequest {
    private LoadOptions loadOptions;
    private LoadListener loadListener;

    private DataSource dataSource;
    private LoadResult loadResult;

    public LoadRequest(
            Sketch sketch, RequestAttrs requestAttrs,
            LoadOptions loadOptions, LoadListener loadListener,
            DownloadProgressListener downloadProgressListener) {
        super(sketch, requestAttrs, loadOptions, null, downloadProgressListener);

        this.loadOptions = loadOptions;
        this.loadListener = loadListener;

        setLogName("LoadRequest");
    }

    /**
     * 获取加载选项
     */
    @Override
    public LoadOptions getOptions() {
        return loadOptions;
    }

    /**
     * 获取加载结果
     */
    @SuppressWarnings("WeakerAccess")
    public LoadResult getLoadResult() {
        return loadResult;
    }

    /**
     * 设置加载结果
     */
    @SuppressWarnings("unused")
    protected void setLoadResult(LoadResult loadResult) {
        this.loadResult = loadResult;
    }

    /**
     * 获取数据源
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public void failed(FailedCause failedCause) {
        super.failed(failedCause);

        if (loadListener != null) {
            postRunFailed();
        }
    }

    @Override
    public void canceled(CancelCause cancelCause) {
        super.canceled(cancelCause);

        if (loadListener != null) {
            postRunCanceled();
        }
    }

    @Override
    protected void runDispatch() {
        setStatus(Status.DISPATCHING);

        // 本地请求直接执行加载
        if (getAttrs().getUriScheme() != UriScheme.NET) {
            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runDispatch",
                        " - ", "local",
                        " - ", getAttrs().getId()));
            }
            submitRunLoad();
            return;
        }

        super.runDispatch();
    }

    @Override
    protected void downloadComplete() {
        DownloadResult downloadResult = getDownloadResult();
        if (downloadResult == null || (downloadResult.getDiskCacheEntry() == null && downloadResult.getImageData() == null)) {
            failed(FailedCause.DOWNLOAD_FAIL);
            return;
        }

        ImageFrom imageFrom = downloadResult.isFromNetwork() ? ImageFrom.NETWORK : ImageFrom.DISK_CACHE;
        if (downloadResult.getDiskCacheEntry() != null) {
            dataSource = new DataSource(downloadResult.getDiskCacheEntry(), imageFrom);
        } else if (downloadResult.getImageData() != null && downloadResult.getImageData().length > 0) {
            dataSource = new DataSource(downloadResult.getImageData(), imageFrom);
        } else {
            failed(FailedCause.DOWNLOAD_FAIL);
            return;
        }

        submitRunLoad();
    }

    @Override
    protected void runLoad() {
        if (isCanceled()) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runLoad",
                        " - ", "canceled",
                        " - ", "startLoad",
                        " - ", getAttrs().getId()));
            }
            return;
        }

        setStatus(Status.LOADING);

        // 尝试用图片预处理器处理一下特殊的本地图片，并得到他们的缓存
        ImagePreprocessor imagePreprocessor = getSketch().getConfiguration().getImagePreprocessor();
        if (imagePreprocessor.isSpecific(this)) {
            PreProcessResult prePrecessResult = imagePreprocessor.prePrecess(this);
            if (prePrecessResult != null) {
                if (prePrecessResult.diskCacheEntry != null) {
                    dataSource = new DataSource(prePrecessResult.diskCacheEntry, prePrecessResult.imageFrom);
                } else if (prePrecessResult.imageData != null) {
                    dataSource = new DataSource(prePrecessResult.imageData, prePrecessResult.imageFrom);
                } else {
                    failed(FailedCause.NOT_GET_SPECIFIC_LOCAL_IMAGE_CACHE_FILE);
                    return;
                }
            } else {
                failed(FailedCause.NOT_GET_SPECIFIC_LOCAL_IMAGE_CACHE_FILE);
                return;
            }
        }

        // 解码
        DecodeResult decodeResult = getSketch().getConfiguration().getImageDecoder().decode(this);
        if (decodeResult == null || (decodeResult.getBitmap() == null && decodeResult.getGifDrawable() == null)) {
            failed(FailedCause.DECODE_FAIL);
            return;
        }

        // 是Bitmap
        if (decodeResult.getBitmap() != null) {
            // 过滤已回收
            if (decodeResult.getBitmap().isRecycled()) {
                if (Sketch.isDebugMode()) {
                    Log.e(Sketch.TAG, SketchUtils.concat(getLogName(),
                            " - ", "runLoad",
                            " - ", "decode failed bitmap recycled",
                            " - ", "decode after",
                            " - ", RecycleBitmapDrawable.getInfo(decodeResult.getBitmap(), decodeResult.getMimeType()),
                            " - ", getAttrs().getId()));
                }
                failed(FailedCause.DECODE_FAIL);
                return;
            }

            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runLoad",
                        " - ", "new bitmap",
                        " - ", RecycleBitmapDrawable.getInfo(decodeResult.getBitmap(), decodeResult.getMimeType()),
                        " - ", getAttrs().getId()));
            }

            if (isCanceled()) {
                if (Sketch.isDebugMode()) {
                    Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                            " - ", "runLoad",
                            " - ", "canceled",
                            " - ", "decode after",
                            " - ", "recycle bitmap",
                            " - ", RecycleBitmapDrawable.getInfo(decodeResult.getBitmap(), decodeResult.getMimeType()),
                            " - ", getAttrs().getId()));
                }
                decodeResult.getBitmap().recycle();
                return;
            }

            // 处理
            ImageProcessor imageProcessor = loadOptions.getImageProcessor();
            if (imageProcessor != null) {
                Bitmap newBitmap = imageProcessor.process(
                        getSketch(), decodeResult.getBitmap(),
                        loadOptions.getResize(), loadOptions.isForceUseResize(),
                        loadOptions.isLowQualityImage());

                // 确实是一张新图片，就替换掉旧图片
                if (newBitmap != null && !newBitmap.isRecycled() && newBitmap != decodeResult.getBitmap()) {
                    if (Sketch.isDebugMode()) {
                        Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                                " - ", "runLoad",
                                " - ", "process after",
                                " - ", "newBitmap",
                                " - ", RecycleBitmapDrawable.getInfo(newBitmap, decodeResult.getMimeType()),
                                " - ", "recycled old bitmap",
                                " - ", getAttrs().getId()));
                    }

                    decodeResult.getBitmap().recycle();
                    decodeResult.setBitmap(newBitmap);
                }
            }

            if (isCanceled()) {
                if (Sketch.isDebugMode()) {
                    Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                            " - ", "runLoad",
                            " - ", "canceled",
                            " - ", "process after",
                            " - ", "recycle bitmap",
                            " - ", RecycleBitmapDrawable.getInfo(decodeResult.getBitmap(), decodeResult.getMimeType()),
                            " - ", getAttrs().getId()));
                }
                decodeResult.getBitmap().recycle();
                return;
            }

            // 最后一次验证
            if (decodeResult.getBitmap() == null || decodeResult.getBitmap().isRecycled()) {
                failed(FailedCause.DECODE_FAIL);
                return;
            }

            loadResult = new LoadResult(decodeResult.getBitmap(), decodeResult.getImageFrom(), decodeResult.getMimeType());
            loadCompleted();
            return;
        }

        // 是GIF图
        if (decodeResult.getGifDrawable() != null) {
            // 验证一下
            if (decodeResult.getGifDrawable().isRecycled()) {
                if (Sketch.isDebugMode()) {
                    Log.e(Sketch.TAG, SketchUtils.concat(getLogName(),
                            " - ", "runLoad",
                            " - ", "gif drawable recycled",
                            " - ", decodeResult.getGifDrawable().getInfo(),
                            " - ", getAttrs().getId()));
                }
                failed(FailedCause.DECODE_FAIL);
                return;
            }

            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runLoad",
                        " - ", "new gif drawable",
                        " - ", decodeResult.getGifDrawable().getInfo(),
                        " - ", getAttrs().getId()));
            }

            decodeResult.getGifDrawable().setMimeType(decodeResult.getMimeType());

            loadResult = new LoadResult(decodeResult.getGifDrawable(), decodeResult.getImageFrom(), decodeResult.getMimeType());
            loadCompleted();
            //noinspection UnnecessaryReturnStatement
            return;
        }
    }

    protected void loadCompleted() {
        postRunCompleted();
    }

    @Override
    protected void runCanceledInMainThread() {
        if (loadListener != null) {
            loadListener.onCanceled(getCancelCause());
        }
    }

    @Override
    protected void runCompletedInMainThread() {
        if (isCanceled()) {
            // 已经取消了就直接把图片回收了
            if (loadResult != null) {
                if (loadResult.getBitmap() != null) {
                    loadResult.getBitmap().recycle();
                }
                if (loadResult.getGifDrawable() != null) {
                    loadResult.getGifDrawable().recycle();
                }
            }
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runCompletedInMainThread",
                        " - ", "canceled",
                        " - ", getAttrs().getId()));
            }
            return;
        }

        setStatus(Status.COMPLETED);

        if (loadListener != null && loadResult != null) {
            if (loadResult.getBitmap() != null) {
                loadListener.onCompleted(loadResult.getBitmap(), loadResult.getImageFrom(), loadResult.getMimeType());
            } else if (loadResult.getGifDrawable() != null) {
                loadListener.onCompleted(loadResult.getGifDrawable(), loadResult.getImageFrom(), loadResult.getMimeType());
            }
        }
    }

    @Override
    protected void runFailedInMainThread() {
        if (isCanceled()) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runFailedInMainThread",
                        " - ", "canceled",
                        " - ", getAttrs().getId()));
            }
            return;
        }

        if (loadListener != null) {
            loadListener.onFailed(getFailedCause());
        }
    }
}
