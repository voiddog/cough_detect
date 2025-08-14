package org.voiddog.coughdetect.plugin

import android.content.Context
import org.voiddog.coughdetect.data.CoughRecord

/**
 * 音频事件记录插件接口
 */
interface AudioEventRecordPlugin {
    /**
     * 插件名称
     */
    val name: String
    
    /**
     * 初始化插件
     * @param context 应用上下文
     */
    fun initialize(context: Context)
    
    /**
     * 处理音频事件记录
     * @param record 原始音频事件记录
     * @return 包含扩展数据的 JSON 字符串
     */
    fun processRecord(record: CoughRecord): String
}