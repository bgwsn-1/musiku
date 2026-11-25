package timber.log

import android.util.Log

object Timber {
    private const val DEFAULT_TAG = "MusikuAudioLab"

    class Tree(private val tag: String) {
        fun d(message: String, vararg args: Any?) = Log.d(tag, format(message, args))
        fun d(t: Throwable?, message: String = "", vararg args: Any?) = Log.d(tag, format(message, args), t)
        fun i(message: String, vararg args: Any?) = Log.i(tag, format(message, args))
        fun i(t: Throwable?, message: String = "", vararg args: Any?) = Log.i(tag, format(message, args), t)
        fun w(message: String, vararg args: Any?) = Log.w(tag, format(message, args))
        fun w(t: Throwable?, message: String = "", vararg args: Any?) = Log.w(tag, format(message, args), t)
        fun e(message: String, vararg args: Any?) = Log.e(tag, format(message, args))
        fun e(t: Throwable?, message: String = "", vararg args: Any?) = Log.e(tag, format(message, args), t)

        private fun format(msg: String, args: Array<out Any?>): String =
            if (args.isEmpty()) msg else runCatching { String.format(msg, *args) }.getOrDefault(msg)
    }

    fun tag(tag: String) = Tree(tag)
    fun d(message: String, vararg args: Any?) = Tree(DEFAULT_TAG).d(message, *args)
    fun d(t: Throwable?, message: String = "", vararg args: Any?) = Tree(DEFAULT_TAG).d(t, message, *args)
    fun i(message: String, vararg args: Any?) = Tree(DEFAULT_TAG).i(message, *args)
    fun i(t: Throwable?, message: String = "", vararg args: Any?) = Tree(DEFAULT_TAG).i(t, message, *args)
    fun w(message: String, vararg args: Any?) = Tree(DEFAULT_TAG).w(message, *args)
    fun w(t: Throwable?, message: String = "", vararg args: Any?) = Tree(DEFAULT_TAG).w(t, message, *args)
    fun e(message: String, vararg args: Any?) = Tree(DEFAULT_TAG).e(message, *args)
    fun e(t: Throwable?, message: String = "", vararg args: Any?) = Tree(DEFAULT_TAG).e(t, message, *args)
}
