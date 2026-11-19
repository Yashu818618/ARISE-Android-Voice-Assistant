import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import com.yashu.igris.ui.theme.ShadowBlack
import com.yashu.igris.ui.theme.ShadowPurple
import com.yashu.igris.ui.theme.GlowPurple
import com.yashu.igris.ui.theme.TextWhite
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

// Define Cristik font family — make sure res/font/cristik.ttf exists
val CristikFont = FontFamily(
    Font(R.font.cristik)
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech

    companion object {
        val recognizedText = mutableStateOf("Waiting...")
        val responseText = mutableStateOf("Ready...")
    }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val spokenText =
                    result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.getOrNull(0)
                        ?: ""

                recognizedText.value = spokenText
                Log.d("IGRIS", "Recognized: $spokenText")

                RetrofitClient.api.sendCommand(
                    CommandRequest(spokenText)
                ).enqueue(object : Callback<CommandResponse> {
                    override fun onResponse(
                        call: Call<CommandResponse>,
                        response: Response<CommandResponse>
                    ) {
                        Log.d("IGRIS", "Server: ${response.body()?.response}")
                        val serverReply = response.body()?.response ?: "No response from server"
                        responseText.value = serverReply
                        speak(serverReply)
                    }

                    override fun onFailure(call: Call<CommandResponse>, t: Throwable) {
                        responseText.value = t.message ?: "Network Error"
                        Log.e("IGRIS", t.message ?: "Network Error")
                    }
                })
            }
        }

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Igris")
                speechLauncher.launch(intent)
            }
        }

    private fun startVoiceRecognition() {
        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())

        tts = TextToSpeech(this, this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IgrisScreen(onMicClick = { startVoiceRecognition() })
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
        tts.setOnUtteranceProgressListener(
            object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        startVoiceRecognition()
                    }
                }

                override fun onError(utteranceId: String?) {}
            }
        )

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "IGRIS_RESPONSE"
        )
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun IgrisScreen(
    onMicClick: () -> Unit
) {
    val recognizedText by MainActivity.recognizedText
    val responseText by MainActivity.responseText

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.arise_bg),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MASTER  -  $recognizedText",
                    color = Color(0xFF8B0000),
                    fontSize = 18.sp,
                    fontFamily = CristikFont,   // ← Cristik font applied
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = "IGRIS  -  $responseText",
                    color = Color(0xFF8B0000),
                    fontSize = 18.sp,
                    fontFamily = CristikFont,   // ← Cristik font applied
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = onMicClick,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5C0000)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp, Color(0xFF8B0000)
                    )
                ) {
                    Text(
                        text = "START",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = CristikFont,   // ← Cristik font on button too
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}