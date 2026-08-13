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
        val outcomes = mutableListOf<ExchangeOutcome>()

        selectedIds.forEach { id ->
            val item = itemById[id]
            if (item == null) {
                outcomes += ExchangeOutcome.skipped("未知资源#$id", "已下架")
                return@forEach
            }

            val targetCount = targetCount(context, item)
            val alreadyCount = item.exchangeCount.coerceAtLeast(0)
            val neededCount = (targetCount - alreadyCount).coerceAtLeast(0)
            if (neededCount <= 0) {
                outcomes += ExchangeOutcome.alreadySatisfied(
                    item.displayName,
                    targetCount
                )
                return@forEach
            }

            if (item.remainingCount <= 0) {
                outcomes += ExchangeOutcome.skipped(item.displayName, "已达限购")
                return@forEach
            }

            var successCount = 0
            var stopReason: String? = null
            val plannedCount = minOf(neededCount, item.remainingCount)

            repeat(plannedCount) {
                if (stopReason != null) return@repeat
                if (score - item.useScore < reserveScore) {
                    stopReason = "积分不足或触及保留底线"
                    return@repeat
                }

                when (val submit = NativeExchangeClient.submit(context, item.exchangeId)) {
                    NativeExchangeClient.ExchangeResult.Success -> {
                        successCount++
                        score -= item.useScore
                    }
                    NativeExchangeClient.ExchangeResult.LoginInvalid -> {
                        outcomes += ExchangeOutcome.forItem(
                            name = item.displayName,
                            targetCount = targetCount,
                            alreadyCount = alreadyCount,
                            successCount = successCount,
                            reason = null
                        )
                        return buildInterruptedText(
                            outcomes,
                            "随后登录信息失效，请到 App 内完成登录",
                            score
                        )
                    }
                    NativeExchangeClient.ExchangeResult.LimitReached -> {
                        stopReason = "已达限购"
                        return@repeat
                    }
                    NativeExchangeClient.ExchangeResult.ScoreNotEnough -> {
                        stopReason = "积分不足"
                        return@repeat
                    }
                    is NativeExchangeClient.ExchangeResult.Failed -> {
                        stopReason = submit.message
                        return@repeat
                    }
                }
            }

            outcomes += ExchangeOutcome.forItem(
                name = item.displayName,
                targetCount = targetCount,
                alreadyCount = alreadyCount,
                successCount = successCount,
                reason = stopReason ?: if (successCount < neededCount) "已达限购" else null
            )
        }

        return buildNotificationText(outcomes, score)
    }

    private fun targetCount(context: Context, item: ExchangeItem): Int {
        val maxTarget = if (item.cycle == "day") {
            item.maxExchangeCount.coerceAtLeast(1)
        } else {
            1
        }
        return AppPreferences.getAutoExchangeTargetCount(context, item.exchangeId)
            .coerceIn(1, maxTarget)
    }

    private fun buildNotificationText(outcomes: List<ExchangeOutcome>, score: Int): String {
        val success = outcomes.mapNotNull { it.successText }
        val satisfied = outcomes.mapNotNull { it.satisfiedText }
        val failed = outcomes.mapNotNull { it.failedText }

        return when {
            success.isEmpty() && failed.isEmpty() && satisfied.isEmpty() ->
                "自动兑换未执行：所选资源均不可兑换，原因可能是已达限购、积分不足、触及积分保留底线或资源已下架，剩余积分 $score"
            success.isEmpty() && failed.isEmpty() ->
                "自动兑换未执行：${compact("已满足目标", satisfied)}，剩余积分 $score"
            success.isEmpty() ->
                "自动兑换未执行：${compact("未兑换", failed)}，剩余积分 $score"
            failed.isEmpty() && satisfied.isEmpty() ->
                "自动兑换完成：${compact("已兑换", success)}，剩余积分 $score"
            failed.isEmpty() ->
                "自动兑换完成：${compact("已兑换", success)}；${compact("已满足目标", satisfied)}，剩余积分 $score"
            else ->
                "自动兑换部分完成：${compact("已兑换", success)}；${compact("未兑换", failed)}，剩余积分 $score"
        }
    }

    private fun buildInterruptedText(
        outcomes: List<ExchangeOutcome>,
        reason: String,
        score: Int
    ): String {
        val success = outcomes.mapNotNull { it.successText }
        return if (success.isEmpty()) {
            "自动兑换中断：$reason，剩余积分 $score"
        } else {
            "自动兑换中断：${compact("已兑换", success)}；$reason，剩余积分 $score"
        }
    }

    private fun compact(label: String, values: List<String>, limit: Int = 3): String {
        val visible = values.take(limit).joinToString("、")
        val extra = values.size - limit
        return if (extra > 0) "$label：$visible 等 ${extra} 项" else "$label：$visible"
    }

    private data class ExchangeOutcome(
        val successText: String? = null,
        val failedText: String? = null,
        val satisfiedText: String? = null
    ) {
        companion object {
            fun skipped(name: String, reason: String) =
                ExchangeOutcome(failedText = "$name（$reason）")

            fun alreadySatisfied(name: String, targetCount: Int) =
                ExchangeOutcome(satisfiedText = "$name 今日目标 ${targetCount} 个，今日已完成目标")

            fun forItem(
                name: String,
                targetCount: Int,
                alreadyCount: Int,
                successCount: Int,
                reason: String?
            ): ExchangeOutcome {
                val successText = when {
                    successCount <= 0 -> null
                    alreadyCount > 0 && alreadyCount + successCount >= targetCount ->
                        "$name 今日目标 ${targetCount} 个，已兑换过 ${alreadyCount} 个，本次补兑 ${successCount} 个"
                    else -> "$name×$successCount"
                }
                val failedText = when {
                    reason == null -> null
                    successCount > 0 ->
                        "$name 目标 ${targetCount} 个，本次仅兑换 ${successCount} 个（$reason）"
                    else -> "$name（$reason）"
                }
                return ExchangeOutcome(
                    successText = successText,
                    failedText = failedText
                )
            }
        }
    }
}
