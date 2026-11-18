package app.musiku.data

import org.json.JSONArray
import org.json.JSONObject

/** Tiny helpers for walking YouTube's deeply nested JSON without a schema. */

/** Follow a dotted path; numeric segments index into arrays. Returns null on any miss. */
fun Any?.at(path: String): Any? {
    var cur: Any? = this
    for (seg in path.split('.')) {
        cur = when (cur) {
            is JSONObject -> if (cur.has(seg)) cur.opt(seg) else return null
            is JSONArray -> seg.toIntOrNull()?.let { cur.opt(it) } ?: return null
            else -> return null
        }
        if (cur == JSONObject.NULL) return null
    }
    return cur
}

fun Any?.obj(path: String): JSONObject? = at(path) as? JSONObject
fun Any?.arr(path: String): JSONArray? = at(path) as? JSONArray
fun Any?.str(path: String): String? = (at(path) as? String)?.takeIf { it.isNotEmpty() }

fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) (opt(i) as? JSONObject)?.let(out::add)
    return out
}

/** Every value stored under [key] anywhere in the tree, in document order. */
fun Any?.collectAll(key: String, out: MutableList<JSONObject> = ArrayList()): MutableList<JSONObject> {
    when (this) {
        is JSONObject -> {
            val iter = this.keys()
            while (iter.hasNext()) {
                val k = iter.next()
                val v = this.opt(k)
                if (k == key && v is JSONObject) out.add(v)
                else v.collectAll(key, out)
            }
        }
        is JSONArray -> for (i in 0 until length()) opt(i).collectAll(key, out)
    }
    return out
}

/** First value stored under any of [keys], searching depth-first. */
fun Any?.findFirst(vararg names: String): JSONObject? {
    when (this) {
        is JSONObject -> {
            for (k in names) (this.opt(k) as? JSONObject)?.let { return it }
            val iter = this.keys()
            while (iter.hasNext()) this.opt(iter.next()).findFirst(*names)?.let { r -> return r }
        }
        is JSONArray -> for (i in 0 until length()) opt(i).findFirst(*names)?.let { r -> return r }
    }
    return null
}

/** `runs` array of a text object → list of (text, endpoint-owner) pairs. */
fun JSONObject?.runs(): List<JSONObject> = this?.arr("runs").objects()

/** First JSON array stored under [key] anywhere in the tree. */
fun Any?.findArray(key: String): JSONArray? {
    when (this) {
        is JSONObject -> {
            (this.opt(key) as? JSONArray)?.let { return it }
            val iter = this.keys()
            while (iter.hasNext()) this.opt(iter.next()).findArray(key)?.let { r -> return r }
        }
        is JSONArray -> for (i in 0 until length()) opt(i).findArray(key)?.let { r -> return r }
    }
    return null
}
