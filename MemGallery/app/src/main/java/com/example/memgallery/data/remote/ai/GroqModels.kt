package com.example.memgallery.data.remote.ai

data class GroqModel(
    val id: String,
    val rpm: Int,
    val rpd: Int,
    val tpm: Int, // K (thousands) or actual value? User table has "6K", "500K". We'll store as Int (e.g., 6000) for calculation if needed, or Strings for display. Let's use Strings for flexible display "6K", "500K".
    val tpd: String,
    val ash: String = "-", // Audio Seconds Hour
    val asd: String = "-", // Audio Seconds Day
    val description: String = ""
)

object GroqModels {
    val models = listOf(
        GroqModel("meta-llama/llama-4-maverick-17b-128e-instruct", 30, 1000, 6000, "500K", description = "Llama 4 Maverick - High reasoning & Multimodal"),
        GroqModel("meta-llama/llama-4-scout-17b-16e-instruct", 30, 1000, 30000, "500K", description = "Llama 4 Scout - Fast text & vision"),
        GroqModel("llama-3.3-70b-versatile", 30, 1000, 12000, "100K", description = "Llama 3.3 70B - Versatile & Powerful"),
        GroqModel("llama-3.1-8b-instant", 30, 14400, 6000, "500K", description = "Llama 3.1 8B - Instant speed"),
        GroqModel("mixtral-8x7b-32768", 30, 14400, 5000, "500K", description = "Mixtral 8x7B - High context"),
        GroqModel("gemma2-9b-it", 30, 14400, 15000, "500K", description = "Gemma 2 9B - Google's open model"),
        GroqModel("groq/compound", 30, 250, 70000, "-", description = "Compound - Specialized"),
        GroqModel("groq/compound-mini", 30, 250, 70000, "-", description = "Compound Mini"),
        GroqModel("moonshotai/kimi-k2-instruct", 60, 1000, 10000, "300K", description = "Kimi K2 Instruct"),
        GroqModel("qwen/qwen3-32b", 60, 1000, 6000, "500K", description = "Qwen 3 32B - Strong performance"),
        GroqModel("openai/gpt-oss-120b", 30, 1000, 8000, "200K", description = "GPT-OSS 120B"),
        GroqModel("openai/gpt-oss-20b", 30, 1000, 8000, "200K", description = "GPT-OSS 20B"),
        GroqModel("whisper-large-v3", 20, 2000, 0, "-", "7.2K", "28.8K", "Whisper V3 - Audio Transcription"),
        GroqModel("whisper-large-v3-turbo", 20, 2000, 0, "-", "7.2K", "28.8K", "Whisper V3 Turbo - Fast Audio")
    )
    
    const val DEFAULT_MODEL_ID = "meta-llama/llama-4-maverick-17b-128e-instruct"
}
