package me.xiaopan.sketch.request;

import me.xiaopan.sketch.Sketch;

abstract class AsyncRequest extends Request implements Runnable{

    private RunStatus runStatus;
    private boolean sync;

    AsyncRequest(Sketch sketch, RequestAttrs attrs) {
        super(sketch, attrs);
    }

    @Override
    public final void run() {
        if (runStatus != null) {
            switch (runStatus) {
                case DISPATCH:
                    runDispatch();
                    break;
                case DOWNLOAD:
                    runDownload();
                    break;
                case LOAD:
                    runLoad();
                    break;
                default:
                    new IllegalArgumentException("unknown runStatus: " + runStatus.name()).printStackTrace();
                    break;
            }
        }
    }

    /**
     * 是否同步执行
     */
    public boolean isSync() {
        return sync;
    }

    /**
     * 设置是否同步执行
     */
    public void setSync(boolean sync) {
        this.sync = sync;
    }

    /**
     * 提交到分发线程执行分发
     */
    protected void submitRunDispatch() {
        this.runStatus = RunStatus.DISPATCH;
        if (sync) {
            runDispatch();
        } else {
            getSketch().getConfiguration().getRequestExecutor().submitDispatch(this);
        }
    }


    /**
     * 提交到网络线程执行下载
     */
    protected void submitRunDownload() {
        this.runStatus = RunStatus.DOWNLOAD;
        if (sync) {
            runDownload();
        } else {
            getSketch().getConfiguration().getRequestExecutor().submitDownload(this);
        }
    }

    /**
     * 提交到本地线程执行加载
     */
    protected void submitRunLoad() {
        this.runStatus = RunStatus.LOAD;
        if (sync) {
            runLoad();
        } else {
            getSketch().getConfiguration().getRequestExecutor().submitLoad(this);
        }
    }

    /**
     * 提交请求
     */
    final void submit() {
        submitRunDispatch();
    }

    /**
     * 推到主线程处理完成
     */
    protected void postRunCompleted() {
        CallbackHandler.postRunCompleted(this);
    }

    /**
     * 推到主线程处理取消
     */
    void postRunCanceled() {
        CallbackHandler.postRunCanceled(this);
    }

    /**
     * 推到主线程处理失败
     */
    protected void postRunFailed() {
        CallbackHandler.postRunFailed(this);
    }

    /**
     * 推到主线程处理进度
     */
    void postRunUpdateProgress(int totalLength, int completedLength) {
        CallbackHandler.postRunUpdateProgress(this, totalLength, completedLength);
    }

    /**
     * 在分发线程执行分发
     */
    protected abstract void runDispatch();

    /**
     * 在网络线程执行下载
     */
    protected abstract void runDownload();

    /**
     * 在本地线程执行加载
     */
    protected abstract void runLoad();

    /**
     * 在主线程处理进度
     */
    protected abstract void runUpdateProgressInMainThread(int totalLength, int completedLength);

    /**
     * 在主线程处理取消
     */
    protected abstract void runCanceledInMainThread();

    /**
     * 在主线程处理完成
     */
    protected abstract void runCompletedInMainThread();

    /**
     * 在主线程处理失败
     */
    protected abstract void runFailedInMainThread();

    /**
     * 运行状态
     */
    private enum RunStatus {
        /**
         * 分发
         */
        DISPATCH,

        /**
         * 加载
         */
        LOAD,

        /**
         * 下载
         */
        DOWNLOAD,
    }
}
