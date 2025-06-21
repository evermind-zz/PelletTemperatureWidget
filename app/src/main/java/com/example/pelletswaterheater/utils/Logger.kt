package com.example.pelletswaterheater.utils

interface ILogger {
    fun i(tag: String, msg: String)
    fun d(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}

object AndroidLogger : ILogger {
    override fun i(tag: String, msg: String) {
        android.util.Log.i(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
    }

    override fun w(tag: String, msg: String) {
        android.util.Log.w(tag, msg)
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        android.util.Log.e(tag, msg, throwable)
    }
}

object ConsoleLogger : ILogger {
    override fun i(tag: String, msg: String) = println("I/$tag: $msg")
    override fun d(tag: String, msg: String) = println("D/$tag: $msg")
    override fun w(tag: String, msg: String) = println("W/$tag: $msg")
    override fun e(tag: String, msg: String, throwable: Throwable?) {
        println("E/$tag: $msg")
        throwable?.printStackTrace()
    }
}

object Logger : ILogger {

    private val delegate: ILogger by lazy {
        if (isAndroidRuntime()) AndroidLogger else ConsoleLogger
    }

    override fun i(tag: String, msg: String) = delegate.i(tag, msg)
    override fun d(tag: String, msg: String) = delegate.d(tag, msg)
    override fun w(tag: String, msg: String) = delegate.w(tag, msg)
    override fun e(tag: String, msg: String, throwable: Throwable?) =
        delegate.e(tag, msg, throwable)

    fun isAndroidRuntime(): Boolean {
        return try {
            android.os.Build.VERSION.SDK_INT >= 1
        } catch (e: Throwable) {
            false
        }
    }


}

