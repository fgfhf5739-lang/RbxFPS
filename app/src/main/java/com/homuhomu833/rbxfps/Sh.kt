package com.homuhomu833.rbxfps

import rikka.shizuku.Shizuku

data class ShResult(val ok: Boolean, val out: String)

/** Chạy lệnh shell qua Shizuku (quyền adb shell). Gọi ngoài main thread. */
object Sh {

    fun run(cmd: String): ShResult = try {
        // Shizuku.newProcess là private ở API 13; dùng reflection.
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        ShResult(code == 0, (out + err).trim())
    } catch (t: Throwable) {
        ShResult(false, "Error: ${t.javaClass.simpleName}: ${t.message}")
    }
}
