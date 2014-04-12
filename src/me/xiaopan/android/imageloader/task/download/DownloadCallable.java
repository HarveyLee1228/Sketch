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

package me.xiaopan.android.imageloader.task.download;

import java.io.*;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import me.xiaopan.android.imageloader.ImageLoader;
import me.xiaopan.android.imageloader.util.ImageLoaderUtils;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;

import android.util.Log;
import org.apache.http.entity.BufferedHttpEntity;

public class DownloadCallable implements Callable<Object>{
	private static final String NAME = DownloadCallable.class.getSimpleName();
	private static final Map<String, ReentrantLock> urlLocks = new WeakHashMap<String, ReentrantLock>();
	private DownloadRequest downloadRequest;
	
	public DownloadCallable(DownloadRequest downloadRequest) {
		this.downloadRequest = downloadRequest;
	}

	@Override
	public Object call(){
		ReentrantLock urlLock = getUrlLock(downloadRequest.getUri());
		urlLock.lock();
		Object result = download();
		urlLock.unlock();
		return result;
	}

    /**
     * 下载
     * @return 下载结果，可能是一个File也可能是一个byte[]
     */
	private Object download(){
		//如果已经存在就直接返回原文件
		if(downloadRequest.getCacheFile() != null && downloadRequest.getCacheFile().exists()){
			if(downloadRequest.getConfiguration().isDebugMode()){
				Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("文件已存在，无需下载").append("；").append(downloadRequest.getName()).toString());
			}
			return downloadRequest.getCacheFile();
		}
		
		if(downloadRequest.getConfiguration().isDebugMode()){
			Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("下载开始").append("；").append(downloadRequest.getName()).toString());
		}
		
		Object result = null;
		int numberOfLoaded = 0;	//已加载次数
		HttpClient defaultHttpClient = downloadRequest.getConfiguration().getHttpClientCreator().onCreatorHttpClient();
        while(true){
			numberOfLoaded++;//加载次数加1
			HttpGet httpGet = null;
			InputStream inputStream = null;
			OutputStream outputStream = null;
			try {
				//发送请求
				httpGet = new HttpGet(downloadRequest.getUri());
				HttpResponse httpResponse = defaultHttpClient.execute(httpGet);
				long fileLength = parseContentLength(httpResponse);
				
				//读取数据
                inputStream = new BufferedHttpEntity(httpResponse.getEntity()).getContent();
                if(downloadRequest.getCacheFile() != null && ImageLoaderUtils.createFile(downloadRequest.getCacheFile()) && downloadRequest.getConfiguration().getDiskCache().applyForSpace(fileLength)){
                    // 如果可以缓存到本地
                    outputStream = new BufferedOutputStream(new FileOutputStream(downloadRequest.getCacheFile(), false), 8*1024);
                    ImageLoaderUtils.copy(inputStream, outputStream, downloadRequest.getDownloadListener(), fileLength);
                    result = downloadRequest.getCacheFile();
                    if(downloadRequest.getConfiguration().isDebugMode()){
                        Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("下载成功 - FILE").append("；").append(downloadRequest.getName()).toString());
                    }
				}else{
                    // 如果需要直接读到内存
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    outputStream = new BufferedOutputStream(byteArrayOutputStream);
                    ImageLoaderUtils.copy(inputStream, outputStream, downloadRequest.getDownloadListener(), fileLength);
                    result = byteArrayOutputStream.toByteArray();
                    if(downloadRequest.getConfiguration().isDebugMode()){
                        Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("下载成功 - BYTE_ARRAY").append("；").append(downloadRequest.getName()).toString());
                    }
                }
                break;
			} catch (Throwable e2) {
                if(httpGet != null) httpGet.abort();
				if(downloadRequest.getCacheFile() != null && downloadRequest.getCacheFile().exists()) downloadRequest.getCacheFile().delete();

				boolean isRetry = false;	//如果尚未达到最大重试次数，那么就再尝试一次
				if(e2 instanceof ConnectTimeoutException || e2 instanceof SocketTimeoutException  || e2 instanceof  ConnectionPoolTimeoutException){
					if(downloadRequest.getDownloadOptions() != null && downloadRequest.getDownloadOptions().getMaxRetryCount() > 0){
						isRetry = numberOfLoaded < downloadRequest.getDownloadOptions().getMaxRetryCount();
					}
				}else{
				    e2.printStackTrace();
                }
				
				if(downloadRequest.getConfiguration().isDebugMode()) Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("下载异常").append("；").append(downloadRequest.getName()).append("；").append("异常信息").append("=").append(e2.toString()).append("；").append(isRetry?"重新下载":"不再下载").toString());

				if(!isRetry){
					break;
				}
			}finally {
                ImageLoaderUtils.close(inputStream);
                ImageLoaderUtils.close(outputStream);
            }
        }
		return result;
	}

    /**
     * 解析内容长度
     * @param httpResponse http响应
     * @return 内容长度
     * @throws Exception
     */
	private static long parseContentLength(HttpResponse httpResponse) throws Exception{
		Header[] contentTypeString = httpResponse.getHeaders("Content-Length");
        if(contentTypeString.length <= 0){
            throw new Exception("在Http响应中没有取到Content-Length参数");
        }

        long fileLength = Long.valueOf(contentTypeString[0].getValue());
        if(fileLength <= 0){
            throw new Exception("文件长度为0");
        }
		return fileLength;
	}

    /**
     * 获取一个URL锁，通过此锁可以过滤重复下载
     * @param url 下载地址
     * @return URL锁
     */
	private static ReentrantLock getUrlLock(String url){
		ReentrantLock urlLock = urlLocks.get(url);
		if(urlLock == null){
			urlLock = new ReentrantLock();
			urlLocks.put(url, urlLock);
		}
		return urlLock;
	}
}