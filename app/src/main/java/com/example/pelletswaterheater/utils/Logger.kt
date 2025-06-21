package com.example.pelletswaterheater.utils

/**
 * Interface for logging operations, decoupling from Android's [android.util.Log].
 */
interface ILogger {
    /**
     * Logs an info message.
     *
     * @param tag The log tag.
     * @param msg The message to log.
     */
    fun i(tag: String, msg: String)

    /**
     * Logs a debug message.
     *
     * @param tag The log tag.
     * @param msg The message to log.
     */
    fun d(tag: String, msg: String)

    /**
     * Logs a warning message.
     *
     * @param tag The log tag.
     * @param msg The message to log.
     */
    fun w(tag: String, msg: String)

    /**
     * Logs an error message with an optional throwable.
     *
     * @param tag The log tag.
     * @param msg The message to log.
     * @param throwable The optional throwable to log.
     */
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}

/**
 * Implementation of [ILogger] using Android's [android.util.Log].
 */
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

/**
 * Implementation of [ILogger] for console output, used in non-Android environments.
 */
object ConsoleLogger : ILogger {
    override fun i(tag: String, msg: String) = println("I/$tag: $msg")
    override fun d(tag: String, msg: String) = println("D/$tag: $msg")
    override fun w(tag: String, msg: String) = println("W/$tag: $msg")
    override fun e(tag: String, msg: String, throwable: Throwable?) {
        println("E/$tag: $msg")
        throwable?.printStackTrace()
    }
}

/**
 * Delegating logger that selects [AndroidLogger] or [ConsoleLogger] based on runtime environment.
 */
object Logger : ILogger {
    private val delegate: ILogger by lazy {
        if (isAndroidRuntime()) AndroidLogger else ConsoleLogger
    }

    override fun i(tag: String, msg: String) = delegate.i(tag, msg)
    override fun d(tag: String, msg: String) = delegate.d(tag, msg)
    override fun w(tag: String, msg: String) = delegate.w(tag, msg)
    override fun e(tag: String, msg: String, throwable: Throwable?) =
        delegate.e(tag, msg, throwable)

    /**
     * Checks if the runtime environment is Android.
     *
     * @return True if running on Android, false otherwise.
     */
    fun isAndroidRuntime(): Boolean {
        return try {
            android.os.Build.VERSION.SDK_INT >= 1
        } catch (e: Throwable) {
            false
        }
    }
}