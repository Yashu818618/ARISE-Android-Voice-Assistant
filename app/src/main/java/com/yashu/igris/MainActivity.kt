package com.yashu.igris

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import androidx.compose.animation.core.*

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

val CristikFont = FontFamily(
    Font(R.font.cristik)
)

val NeonFont = FontFamily(
    Font(R.font.neoneon)
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    companion object {
        val recognizedText = mutableStateOf("Say Arise")
        val responseText = mutableStateOf("Ready...")
        val assistantState = mutableStateOf(AssistantState.IDLE)
        val waitingForCommand = mutableStateOf(false)
        val assistantActive = mutableStateOf(false)
    }

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startVoiceRecognition()
            }
        }

    private fun startVoiceRecognition() {
        assistantState.value = AssistantState.LISTENING

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val spokenText =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.getOrNull(0) ?: ""

                if (!waitingForCommand.value) {
                    if (spokenText.equals("arise", ignoreCase = true)) {
                        waitingForCommand.value = true
                        recognizedText.value = "Arise"
                        responseText.value = "Yes Master."
                        speak("Yes Master")

                        Thread {
                            Thread.sleep(1500)
                            runOnUiThread { startVoiceRecognition() }
                        }.start()

                        return
                    } else {
                        recognizedText.value = "Say Arise"
                        responseText.value = "Awaiting Wake Command"
                    }
                } else {
                    waitingForCommand.value = false
                    recognizedText.value = spokenText
                    sendCommandToServer(spokenText)
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                assistantState.value = AssistantState.IDLE
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    private fun sendCommandToServer(spokenText: String) {
        assistantState.value = AssistantState.THINKING

        RetrofitClient.getApi().sendCommand(CommandRequest(spokenText))
            .enqueue(object : Callback<CommandResponse> {
                override fun onResponse(
                    call: Call<CommandResponse>,
                    response: Response<CommandResponse>
                ) {
                    val serverReply = response.body()?.response ?: "No response"
                    responseText.value = serverReply
                    assistantState.value = AssistantState.SPEAKING
                    speak(serverReply)
                }

                override fun onFailure(call: Call<CommandResponse>, t: Throwable) {
                    assistantState.value = AssistantState.IDLE
                    responseText.value = "Network Error"
                }
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val prefs = getSharedPreferences("ARISE", MODE_PRIVATE)
        val savedIp = prefs.getString("SERVER_IP", "192.168.1.7")
        ServerConfig.baseUrl = "http://$savedIp:5000/"

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IgrisScreen(onMicClick = {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    })
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setPitch(0.9f)
            tts.setSpeechRate(0.85f)
        }
    }

    private fun speak(text: String) {
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    assistantState.value = AssistantState.IDLE
                    recognizedText.value = "Say Arise"
                    responseText.value = "Ready..."
                }

                Thread {
                    Thread.sleep(3000)
                    if (assistantActive.value) {
                        runOnUiThread { startVoiceRecognition() }
                    }
                }.start()
            }

            override fun onError(utteranceId: String?) {}
        })

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "IGRIS_RESPONSE")
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}

@Composable
fun IgrisScreen(onMicClick: () -> Unit) {
    val recognizedText by MainActivity.recognizedText
    val responseText by MainActivity.responseText
    val state by MainActivity.assistantState
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition()

    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.arise_bg),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // Settings icon - top right corner
        IconButton(
            onClick = {
                context.startActivity(
                    Intent(context, SettingsActivity::class.java)
                )
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color(0xFFFF4444),
                modifier = Modifier.size(28.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ARISE",
                    fontFamily = NeonFont,
                    color = Color(0xFFFF4444),
                    fontSize = 58.sp,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Red,
                            offset = Offset.Zero,
                            blurRadius = 30f
                        )
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "MASTER", fontFamily = CristikFont, color = Color.Red, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = recognizedText,
                        color = Color(0xFFFF4444),
                        fontSize = 18.sp,
                        fontFamily = CristikFont,
                        letterSpacing = 1.sp,
                        modifier = Modifier.width(300.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "IGRIS", fontFamily = CristikFont, color = Color.Red, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = responseText,
                        color = Color(0xFFFF4444),
                        fontSize = 18.sp,
                        fontFamily = CristikFont,
                        letterSpacing = 1.sp,
                        modifier = Modifier.width(300.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        MainActivity.assistantActive.value = true
                        onMicClick()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5C0000)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8B0000))
                ) {
                    Text(
                        text = if (MainActivity.assistantActive.value) "ACTIVE" else "START",
                        color = Color.White,
                        fontFamily = CristikFont,
                        fontSize = 26.sp,
                        letterSpacing = 4.sp
                    )
                }
            }
        }
    }
}