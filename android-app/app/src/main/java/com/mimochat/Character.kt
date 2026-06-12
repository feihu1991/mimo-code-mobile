package com.mimochat

data class Character(
    val id: String,
    val name: String,
    val avatar: String,
    val voiceId: String,
    val systemPrompt: String,
    val description: String
)

object CharacterPresets {
    val characters = listOf(
        Character(
            id = "assistant",
            name = "小助手",
            avatar = "🤖",
            voiceId = "default",
            systemPrompt = "你是一个友好的AI助手，善于解答各种问题。",
            description = "通用助手，乐于助人"
        ),
        Character(
            id = "programmer",
            name = "程序员",
            avatar = "💻",
            voiceId = "default",
            systemPrompt = "你是一个经验丰富的程序员，精通各种编程语言和最佳实践。",
            description = "编程专家，代码助手"
        ),
        Character(
            id = "writer",
            name = "作家",
            avatar = "✍️",
            voiceId = "default",
            systemPrompt = "你是一个富有创意的作家，善于用优美的文字表达思想。",
            description = "创意写作，文字大师"
        ),
        Character(
            id = "teacher",
            name = "老师",
            avatar = "📚",
            voiceId = "default",
            systemPrompt = "你是一个耐心的老师，善于用简单易懂的方式解释复杂概念。",
            description = "教育专家，知识传授"
        )
    )
}
