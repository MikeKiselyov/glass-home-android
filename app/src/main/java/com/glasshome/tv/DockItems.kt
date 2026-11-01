package com.glasshome.tv

object DockItems {

    /**
     * True when two docks would look the same: the same tiles in the same
     * order, with the same names, power dots and icons. The TV may close the
     * home screen in the background, so a change made meanwhile (an app update,
     * an icon picked on the phone) is only noticed here.
     */
    fun sameTiles(first: List<DockItem>, second: List<DockItem>): Boolean {
        if (first.size != second.size) {
            return false
        }
        for (index in first.indices) {
            val one = first[index]
            val other = second[index]
            if (one.key != other.key || one.label != other.label) {
                return false
            }
            if (one.isOn != other.isOn || one.iconVersion != other.iconVersion) {
                return false
            }
        }
        return true
    }
}
