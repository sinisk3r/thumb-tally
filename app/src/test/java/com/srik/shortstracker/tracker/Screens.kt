package com.srik.shortstracker.tracker

/** Rebuilds a [NodeSnap] tree from a screen dump written by [NodeSnap.dump]. */
object Screens {
    private val LINE = Regex(
        "^( *)(\\S+)(?: #(\\S*))?(?: text=\"(.*?)\"(?= desc=| \\[| x=))?(?: desc=\"(.*?)\"(?= \\[| x=))?" +
            "( \\[hidden])?( \\[scrollable])? x=(-?\\d+)-(-?\\d+) y=(-?\\d+)-(-?\\d+)$",
    )

    fun load(name: String): NodeSnap {
        val text = checkNotNull(javaClass.classLoader?.getResource("screens/$name.txt")) { "missing screen $name" }.readText()
        // The first lines are the parse result at capture time, then a blank line, then the tree.
        return parse(text.substringAfter("\n\n"))
    }

    fun parse(dump: String): NodeSnap {
        data class Building(val depth: Int, val node: NodeSnap, val children: MutableList<NodeSnap> = mutableListOf())

        val stack = ArrayDeque<Building>()
        var root: NodeSnap? = null
        fun close(): NodeSnap {
            val done = stack.removeLast()
            val node = done.node.copy(children = done.children)
            if (stack.isEmpty()) root = node else stack.last().children += node
            return node
        }
        dump.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val m = LINE.find(line) ?: error("can't read: $line")
            val g = m.groupValues
            val depth = g[1].length / 2
            while (stack.isNotEmpty() && stack.last().depth >= depth) close()
            stack += Building(
                depth,
                NodeSnap(
                    className = g[2],
                    viewId = g[3].ifEmpty { null }?.let { "app:id/$it" },
                    text = g[4].takeIf { m.groups[4] != null },
                    desc = g[5].takeIf { m.groups[5] != null },
                    visible = g[6].isEmpty(),
                    scrollable = g[7].isNotEmpty(),
                    left = g[8].toInt(), right = g[9].toInt(), top = g[10].toInt(), bottom = g[11].toInt(),
                ),
            )
        }
        while (stack.isNotEmpty()) close()
        return checkNotNull(root)
    }
}
