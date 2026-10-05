package app.aaps.plugins.aps.openAPSAIMI.llm.openai

/**
 * Shared OpenAI model ids for every AIMI path that talks to OpenAI
 * (Physio analyzer, AI Auditor, AI Coach, Meal vision).
 *
 * [HIGH] is the capable tier. [CHEAP] is the efficient tier.
 * AutoISF keeps its own constants on purpose — it must not depend on AIMI.
 */
object OpenAiModels {

    /** Capable tier: analyzer, auditor high-perf, meal vision. */
    const val HIGH = "gpt-6.1-sol"

    /** Efficient tier: coaching, auditor default. */
    const val CHEAP = "gpt-6-luna"
}
