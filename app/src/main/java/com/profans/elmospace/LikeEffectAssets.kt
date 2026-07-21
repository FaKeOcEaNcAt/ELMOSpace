package com.profans.elmospace

import androidx.annotation.DrawableRes

data class LikeEffectOption(
    val id: String,
    val displayName: String,
    @param:DrawableRes val drawableRes: Int
)

object LikeEffectAssets {
    const val DEFAULT_ID = "m4a1"
    const val RANDOM_ID = "random"

    val randomOption = LikeEffectOption(RANDOM_ID, "随机表情包", R.drawable.like_effect_m4a1)

    val options = listOf(
        LikeEffectOption(DEFAULT_ID, "Q版热浪回想-M4A1", R.drawable.like_effect_m4a1),
        LikeEffectOption("leader", "伟大领袖翀将军", R.drawable.like_effect_leader),
        LikeEffectOption("tsar", "尼沙皇Nikita", R.drawable.like_effect_tsar),
        LikeEffectOption(
            "tomato_female",
            "神秘番茄大王(女牢指)",
            R.drawable.like_effect_tomato_female
        ),
        LikeEffectOption(
            "tomato_male",
            "神秘番茄大王(男牢指)",
            R.drawable.like_effect_tomato_male
        ),
        LikeEffectOption("leader_happy", "高兴的翀将军", R.drawable.like_effect_leader_happy),
        LikeEffectOption("doro_sop", "可爱的Doro索普", R.drawable.like_effect_doro_sop)
    )

    val pickerOptions = listOf(randomOption) + options

    fun find(id: String) = options.firstOrNull { it.id == id } ?: options.first()

    fun findSelection(id: String) = pickerOptions.firstOrNull { it.id == id } ?: options.first()
}
