package com.profans.elmospace

import android.content.Context

object AutoExchangeExecutor {
    fun execute(context: Context): String {
        if (!AppPreferences.isAutoExchangeEnabled(context)) return ""

        val selectedIds = AppPreferences.getAutoExchangeSelectedIds(context)
        if (selectedIds.isEmpty()) return "自动兑换未执行：未选择自动兑换资源，请到设置中选择"

        val sync = when (val result = NativeExchangeClient.sync(context)) {
            NativeExchangeClient.SyncResult.LoginInvalid ->
                return "自动兑换未执行：登录信息失效，请到 App 内完成登录"
            NativeExchangeClient.SyncResult.InterfaceUnavailable ->
                return "自动兑换未执行：无法获取兑换列表或当前积分，可能是官方更新了兑换接口"
            is NativeExchangeClient.SyncResult.Success -> result.data
        }

        AppPreferences.setAutoExchangeLastSyncItems(
            context,
            NativeExchangeClient.itemsToJson(sync.items)
        )

        var score = sync.score
        val reserveScore = AppPreferences.getAutoExchangeReserveScore(context)
        val itemById = sync.items.associateBy { it.exchangeId }
        val success = mutableListOf<String>()
        val failed = mutableListOf<String>()

        selectedIds.forEach { id ->
            val item = itemById[id]
            if (item == null) {
                failed += "未知资源#$id（已下架）"
                return@forEach
            }
            if (item.remainingCount <= 0) {
                failed += "${item.displayName}（已达限购）"
                return@forEach
            }
            if (score - item.useScore < reserveScore) {
                failed += "${item.displayName}（积分不足或触及保留底线）"
                return@forEach
            }

            when (val submit = NativeExchangeClient.submit(context, item.exchangeId)) {
                NativeExchangeClient.ExchangeResult.Success -> {
                    success += item.displayName
                    score -= item.useScore
                }
                NativeExchangeClient.ExchangeResult.LoginInvalid ->
                    return "自动兑换中断：登录信息失效，请到 App 内完成登录"
                NativeExchangeClient.ExchangeResult.LimitReached ->
                    failed += "${item.displayName}（已达限购）"
                NativeExchangeClient.ExchangeResult.ScoreNotEnough ->
                    failed += "${item.displayName}（积分不足）"
                is NativeExchangeClient.ExchangeResult.Failed ->
                    failed += "${item.displayName}（${submit.message}）"
            }
        }

        return buildNotificationText(success, failed, score)
    }

    private fun buildNotificationText(
        success: List<String>,
        failed: List<String>,
        score: Int
    ): String {
        return when {
            success.isEmpty() && failed.isEmpty() ->
                "自动兑换未执行：所选资源均不可兑换，原因可能是已达限购、积分不足或资源已下架"
            success.isEmpty() ->
                "自动兑换未执行：${compact("未兑换", failed)}，剩余积分 $score"
            failed.isEmpty() ->
                "自动兑换完成：${compact("已兑换", success)}，剩余积分 $score"
            else ->
                "自动兑换部分完成：${compact("已兑换", success)}；${compact("未兑换", failed)}，剩余积分 $score"
        }
    }

    private fun compact(label: String, values: List<String>, limit: Int = 3): String {
        val visible = values.take(limit).joinToString("、")
        val extra = values.size - limit
        return if (extra > 0) "$label：$visible 等 ${extra} 项" else "$label：$visible"
    }
}
