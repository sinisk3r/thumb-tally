package com.srik.shortstracker.tracker

/**
 * A plain copy of the on-screen view tree, taken from an AccessibilityNodeInfo.
 * Kept free of Android types so [ScreenParser] can be unit-tested on the JVM.
 */
data class NodeSnap(
    val viewId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val visible: Boolean = true,
    val scrollable: Boolean = false,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val children: List<NodeSnap> = emptyList(),
) {
    /** "reel_recycler" for "com.google.android.youtube:id/reel_recycler". */
    val idName: String? get() = viewId?.substringAfter(":id/")

    val label: String? get() = text?.takeIf { it.isNotBlank() } ?: desc?.takeIf { it.isNotBlank() }

    fun flatten(): List<NodeSnap> = buildList {
        fun walk(node: NodeSnap) {
            add(node)
            node.children.forEach(::walk)
        }
        walk(this@NodeSnap)
    }

    /** Indented, human-readable tree, used for the debug dump. */
    fun dump(): String = buildString {
        fun walk(node: NodeSnap, depth: Int) {
            append("  ".repeat(depth))
            append(node.className?.substringAfterLast('.') ?: "?")
            node.idName?.let { append(" #").append(it) }
            node.text?.let { append(" text=\"").append(it.replace('\n', ' ')).append('"') }
            node.desc?.let { append(" desc=\"").append(it.replace('\n', ' ')).append('"') }
            if (!node.visible) append(" [hidden]")
            if (node.scrollable) append(" [scrollable]")
            append(" x=").append(node.left).append('-').append(node.right)
            append(" y=").append(node.top).append('-').append(node.bottom).append('\n')
            node.children.forEach { walk(it, depth + 1) }
        }
        walk(this@NodeSnap, 0)
    }
}
