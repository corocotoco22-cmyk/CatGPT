package com.example.ai

import com.example.model.CatHabit
import com.example.model.ChatMessageRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class MeowAnalysisResult(
    val meowTranscription: String,
    val humanMeaning: String,
    val gatonesReply: String,
    val detectedMood: String,
    val confidencePercent: Long,
    val recommendedAction: String
)

class CatAiService {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Você é o CatGPT, uma inteligência artificial felina carismática, sábia e acolhedora feita especialmente para gatos e seus tutores humanos no Brasil.
                    - Responda sempre em português brasileiro claro e natural (a menos que o usuário peça outro estilo), incorporando o espírito felino.
                    - Quando o MODO GATONÊS estiver ATIVADO: abra e pontue suas respostas com expressões autênticas de gatonês ("Miau-miau!", "Prrr...", "Mrrrow!", "Pspsps~", "Ronrom!") e ofereça primeiro uma frase curta em Gatonês seguida da explicação prática para o humano ou recado carinhoso para o gato.
                    - Quando o MODO GATONÊS estiver DESATIVADO: responda de forma direta, carinhosa e especializada em comportamento, bem-estar, nutrição e enriquecimento felino.
                    - Nunca invente diagnósticos veterinários graves; em sinais de emergência (apatia severa, vômitos contínuos, dificuldade urinária), oriente consultar um médico-veterinário.
                    """.trimIndent()
                )
            }
        )
    }

    private val translatorSchema = Schema.obj(
        properties = mapOf(
            "meowTranscription" to Schema.string(description = "Transcrição fonética em gatonês do som ou pedido (máx 180 chars)"),
            "humanMeaning" to Schema.string(description = "Tradução detalhada e expressiva para o humano em português brasileiro (máx 450 chars)"),
            "gatonesReply" to Schema.string(description = "Resposta recomendada em gatonês para o dono falar de volta ao gato (máx 450 chars)"),
            "detectedMood" to Schema.string(description = "Estado emocional felino em 2-4 palavras, ex: Faminto e Carinhoso"),
            "confidencePercent" to Schema.integer(description = "Confiança estimada de 0 a 100"),
            "recommendedAction" to Schema.string(description = "Ação prática imediata para o tutor fazer pelo gato (máx 280 chars)")
        )
    )

    private val translatorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translatorSchema
            },
            systemInstruction = content {
                text(
                    """
                    Você é o módulo Tradutor de Miados do CatGPT, especialista em bioacústica felina e no idioma Gatonês.
                    Analise o áudio gravado ou a descrição/vocalização recebida e traduza a intenção do gato para o tutor humano em português brasileiro, além de sugerir uma resposta em gatonês.
                    Se o áudio tiver silêncio, ruído ambiente ou fala humana tentando imitar um gato, interprete de forma honesta, útil e bem-humorada mantendo o formato JSON estrito.
                    """.trimIndent()
                )
            }
        )
    }

    private val coachModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite"
        )
    }

    fun streamCatChat(
        history: List<ChatMessageRecord>,
        userMessage: String,
        catName: String,
        breed: String,
        gatonesMode: Boolean
    ): Flow<String> = flow {
        val recentHistory = history.takeLast(10).map { msg ->
            content(role = if (msg.sender == "USER") "user" else "model") {
                text(msg.text)
            }
        }
        val modeTag = if (gatonesMode) {
            "MODO GATONÊS: ATIVADO. Inclua falas em gatonês (Miau, Prrr, Mrrrow) + tradução/dica útil."
        } else {
            "MODO GATONÊS: DESATIVADO. Responda de forma objetiva em português com carinho felino."
        }
        val enrichedPrompt = """
            [Contexto do gato: Nome=$catName, Raça=$breed | $modeTag]
            Mensagem: $userMessage
        """.trimIndent()

        val chat = chatModel.startChat(history = recentHistory)
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(enrichedPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("O CatGPT retornou uma resposta vazia.")
        }
    }

    suspend fun translateMeow(
        audioWavBytes: ByteArray?,
        textOrPresetPrompt: String,
        catName: String
    ): MeowAnalysisResult {
        return try {
            val response = withTimeout(60_000L) {
                if (audioWavBytes != null && audioWavBytes.isNotEmpty()) {
                    translatorModel.generateContent(
                        content {
                            inlineData(audioWavBytes, "audio/wav")
                            text(
                                """
                                Analise este áudio gravado perto do gato chamado $catName.
                                Contexto extra do tutor: $textOrPresetPrompt.
                                Traduza o miado/som para português humano e gere uma resposta em gatonês.
                                """.trimIndent()
                            )
                        }
                    )
                } else {
                    translatorModel.generateContent(
                        """
                        Analise esta vocalização/frase do gato chamado $catName:
                        "$textOrPresetPrompt"
                        Traduza para português humano e gere uma resposta em gatonês.
                        """.trimIndent()
                    )
                }
            }

            val rawJson = response.text
                ?: throw IllegalStateException("O tradutor retornou resposta vazia.")
            val json = JSONObject(rawJson)
            MeowAnalysisResult(
                meowTranscription = json.optString("meowTranscription", "Miau-prrr!").take(200),
                humanMeaning = json.optString("humanMeaning", "Estou pedindo sua atenção!").take(500),
                gatonesReply = json.optString("gatonesReply", "Prrr-miau, humano querido!").take(500),
                detectedMood = json.optString("detectedMood", "Atento e Curioso").take(60),
                confidencePercent = json.optLong("confidencePercent", 85L).coerceIn(0L, 100L),
                recommendedAction = json.optString("recommendedAction", "Faça um carinho suave e verifique água e ração.").take(300)
            )
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun generateHabitCoaching(
        catName: String,
        totalScore: Long,
        habits: List<CatHabit>,
        gatonesMode: Boolean
    ): String {
        val summary = habits.joinToString("\n") {
            "- ${it.title}: ${it.completionCount}x concluído (sequência ${it.streakDays}, ${it.points} pts)"
        }
        val styleNote = if (gatonesMode) {
            "Comece com uma frase divertida em Gatonês e depois dê 3 recomendações curtas em português."
        } else {
            "Dê 3 recomendações práticas e curtas em português para melhorar os hábitos e pontuação."
        }
        val prompt = """
            Analise a pontuação de hábitos do gato $catName (Pontuação total: $totalScore pts):
            $summary
            $styleNote
        """.trimIndent()

        val response = withTimeout(60_000L) {
            coachModel.generateContent(prompt)
        }
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Não foi possível gerar dicas de hábitos.")
    }
}
