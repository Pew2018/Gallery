package org.fossify.gallery.helpers

/** Stable theme IDs and their exact seed RGB colors, from the TiebaLite preset table.
 * Gallery uses View/AppCompat, not Compose.
 */
data class OxygenPreset(val id: String, val label: String, val hex: String, val group: String) {
    val color: Int get() = OxygenThemeEngine.parseHex(hex)!!
}

object OxygenThemePresets {
    const val DEFAULT_ID = "oxygen_blue"
    private val featuredIds = listOf("oxygen_blue", "oxygen_deep_sea_blue", "oxygen_grass_green",
        "oxygen_jade_green", "oxygen_indigo_blue", "oxygen_golden", "oxygen_charm_purple")
    val featured: List<OxygenPreset> get() = featuredIds.map { byId.getValue(it) }

    val all: List<OxygenPreset> = listOf(
        OxygenPreset("oxygen_blue", "晴空蓝", "#42A5F5", "classic"),
        OxygenPreset("oxygen_deep_sea_blue", "深海蓝", "#0071CD", "classic"),
        OxygenPreset("oxygen_golden", "流金橙", "#CC6F4E", "classic"),
        OxygenPreset("oxygen_lemon_yellow", "柠檬黄", "#E6A545", "classic"),
        OxygenPreset("oxygen_grass_green", "青草绿", "#7DC22F", "classic"),
        OxygenPreset("oxygen_jade_green", "碧玉绿", "#01998C", "classic"),
        OxygenPreset("oxygen_charm_purple", "魅力紫", "#9575CD", "classic"),
        OxygenPreset("oxygen_indigo_blue", "靛青蓝", "#3F3DA2", "classic"),
        OxygenPreset("oxygen_sky_blue", "天空蓝", "#26C6DA", "classic"),
        OxygenPreset("oxygen_vigour_red", "活力红", "#F06292", "classic"),
        OxygenPreset("oxygen_fashion_pink", "时尚粉", "#BA68C8", "classic"),
        OxygenPreset("material_blue", "蓝", "#2196F3", "material"),
        OxygenPreset("material_teal", "青绿", "#009688", "material"),
        OxygenPreset("material_green", "绿", "#4CAF50", "material"),
        OxygenPreset("material_red", "红", "#F44336", "material"),
        OxygenPreset("material_orange", "橙", "#FF9800", "material"),
        OxygenPreset("material_purple", "紫", "#9C27B0", "material"),
        OxygenPreset("material_cyan", "青", "#00BCD4", "material"),
        OxygenPreset("material_indigo", "靛蓝", "#3F51B5", "material"),
        OxygenPreset("material_pink", "粉", "#E91E63", "material"),
        OxygenPreset("material_blue_grey", "蓝灰", "#607D8B", "material"),
        OxygenPreset("material_deep_orange", "深橙", "#FF5722", "material"),
        OxygenPreset("material_light_green", "浅绿", "#8BC34A", "material")
    )
    val byId: Map<String, OxygenPreset> = all.associateBy { it.id }

    /** Filter without sorting: persisted IDs and visual order remain stable. */
    fun group(id: String): List<OxygenPreset> = all.filter { it.group == id }

    /** A saved custom color cannot select a similar-looking preset by RGB. */
    fun isSelected(activated: Boolean, savedId: String, swatchId: String): Boolean =
        activated && savedId == swatchId && byId.containsKey(swatchId)

    /** Four columns at 360dp; shrink for narrow screens or larger system fonts. */
    fun columnsForWidth(widthDp: Int, fontScale: Float): Int {
        val minimumCell = (80f * fontScale.coerceAtLeast(1f)).toInt().coerceAtLeast(80)
        return ((widthDp - 32).coerceAtLeast(0) / minimumCell).coerceIn(1, 4)
    }
}
