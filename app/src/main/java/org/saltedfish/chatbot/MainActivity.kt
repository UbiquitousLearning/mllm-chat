package org.saltedfish.chatbot

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.rememberAsyncImagePainter
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.origeek.imageViewer.previewer.ImagePreviewerState
import com.origeek.imageViewer.previewer.rememberPreviewerState
import gomllm.Gomllm
import kotlinx.coroutines.CoroutineScope
import org.saltedfish.chatbot.ui.theme.ChatBotTheme
import org.saltedfish.chatbot.ui.theme.Purple80
import java.util.concurrent.CountDownLatch

fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

class PickRingtone : ActivityResultContract<Int, Uri?>() {
    override fun createIntent(context: Context, input: Int) =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, input)
        }
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) else null
}

class MyPickContact : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        val mimeType = when (input) {
            "PHONE" -> ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            "EMAIL" -> ContactsContract.CommonDataKinds.Email.CONTENT_TYPE
            "ADDRESS" -> ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_TYPE
            else -> ContactsContract.Contacts.CONTENT_TYPE
        }
        return Intent(Intent.ACTION_PICK).setType(mimeType)
    }
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
}

open class CreateDocumentWithMime : ActivityResultContract<Map<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Map<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType(input["mime_type"])
            .putExtra(Intent.EXTRA_TITLE, input["file_name"])
    final override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
}

class MainActivity : ComponentActivity() {
    var latch = CountDownLatch(1)
    var uri = ""
    var uris: List<String> = listOf()

    val creatDocumentLauncher = registerForActivityResult(CreateDocumentWithMime()) { uri: Uri? -> this.uri = uri.toString(); latch.countDown() }
    val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> this.uri = uri.toString(); latch.countDown() }
    val openMultipleDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri>? -> this.uris = uris?.map { it.toString() } ?: emptyList(); latch.countDown() }
    val getMultipleContents = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? -> this.uris = uris?.map { it.toString() } ?: emptyList(); latch.countDown() }
    val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> this.uri = uri.toString(); latch.countDown() }
    val getContact = registerForActivityResult(MyPickContact()) { uri: Uri? -> this.uri = uri.toString(); latch.countDown() }
    val getRingtone = registerForActivityResult(PickRingtone()) { uri: Uri? -> this.uri = uri.toString(); latch.countDown() }
    val takePicLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success -> latch.countDown() }
    val takeVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success -> latch.countDown() }

    val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.startActivity(intent)
            }
        }

        startLocalMllmServer()

        setContent {
            ChatBotTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { Home(navController) }
                    composable("settings") { SettingsPage(navController) }
                    composable(
                        "chat/{id}?type={type}&device={device}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.IntType },
                            navArgument("type") { type = NavType.IntType; defaultValue = 0 },
                            navArgument("device") { type = NavType.IntType; defaultValue = 0 }
                        )
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("id") ?: 0
                        val type = backStackEntry.arguments?.getInt("type") ?: 0
                        val device = backStackEntry.arguments?.getInt("device") ?: 0
                        Chat(navController, chatType = type, modelId = id, deviceId = device)
                    }
                    composable("photo") { Photo(navController) }
                    composable("vqa") { VQA(navController) }
                }
            }
        }
    }

    private fun startLocalMllmServer() {
        if (!AppConfig.useLocalModel) return

        Thread {
            val qwenPath = "/sdcard/Download/model/qwen3"
            val ocrPath = "/sdcard/Download/model/deepseek_ocr"

            try {
                val cacheDir = this.cacheDir.absolutePath
                android.system.Os.setenv("TMPDIR", cacheDir, true)
                val status = Gomllm.startServer(qwenPath, ocrPath, cacheDir)
                runOnUiThread {
                    Toast.makeText(this, "Engine: $status", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(navController: NavController) {
    var useLocal by remember { mutableStateOf(AppConfig.useLocalModel) }
    var apiKey by remember { mutableStateOf(AppConfig.apiKey) }
    var cloudUrl by remember { mutableStateOf(AppConfig.cloudApiUrl) }
    var cloudModel by remember { mutableStateOf(AppConfig.cloudModelName) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(
                "API Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Use Local Model (Offline)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (useLocal) "Connecting to: 127.0.0.1:8080" else "Connecting to Cloud API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useLocal,
                    onCheckedChange = {
                        useLocal = it
                        AppConfig.useLocalModel = it
                    }
                )
            }

            if (!useLocal) {
                Spacer(modifier = Modifier.height(16.dp))

                // API Key Input
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        AppConfig.apiKey = it
                    },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // API URL Input
                OutlinedTextField(
                    value = cloudUrl,
                    onValueChange = {
                        cloudUrl = it
                        AppConfig.cloudApiUrl = it
                    },
                    label = { Text("API URL") },
                    placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model Name Input
                OutlinedTextField(
                    value = cloudModel,
                    onValueChange = {
                        cloudModel = it
                        AppConfig.cloudModelName = it
                    },
                    label = { Text("Model Name") },
                    placeholder = { Text("gpt-3.5-turbo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    "Configuration is stored in memory temporarily.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chat(
    navController: NavController,
    chatType: Int = 0,
    modelId: Int = 0,
    deviceId: Int = 0,
    vm: ChatViewModel = viewModel()
) {
    val messages = vm.messageList
    val isBusy = vm.isBusy.value
    val previewUri by vm.previewUri.observeAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var useNpu by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Chat", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    if (AppConfig.useLocalModel) {
                        Text(
                            text = if (useNpu) "Powered by NPU" else "Powered by CPU",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Cloud API",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }, actions = {
                if (AppConfig.useLocalModel) {
                    IconButton(onClick = { useNpu = !useNpu }) {
                        Icon(
                            imageVector = if (useNpu) Icons.Rounded.Star else Icons.Rounded.Build,
                            contentDescription = "Switch Processor",
                            tint = if (useNpu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            })
        },
        bottomBar = {
            ChatInput(
                enable = !isBusy,
                withImage = (chatType == 1),
                onImageSelected = { uri -> vm.setPreviewUri(uri) }
            ) { message ->
                val finalMsg = if (chatType == 1 && message.content != null) {
                    message.copy(type = MessageType.IMAGE)
                } else {
                    message.copy(type = MessageType.TEXT, content = null)
                }
                vm.sendMessage(context, finalMsg)
                vm.setPreviewUri(null)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .systemBarsPadding()
                    .verticalScroll(scrollState)
            ) {
                if (messages.isEmpty()) {
                    val initMsg = if (AppConfig.useLocalModel) "Hi! I am connected to local Go Server." else "Hi! I am connected to Cloud API."
                    ChatBubble(message = Message(initMsg, false, 0))
                }
                messages.forEach { msg ->
                    ChatBubble(message = msg)
                }
            }
            if (previewUri != null) {
                PreviewBubble(preview = previewUri!!)
            }
        }
    }
}

@Composable
fun MainEntryCards(
    modifier: Modifier = Modifier,
    navController: NavController,
    selectedIndex: Int = 0,
    selectedBackend: Int = 0
) {
    Column(Modifier.padding(8.dp).padding(top = 10.dp)) {
        Row {
            EntryCard(
                icon = R.drawable.text,
                backgroundColor = Color(0xEDADE6AA),
                title = "Chat",
                subtitle = "\" The meaning of life is ....\"",
                onClick = { navController.navigate("chat/0") }
            )
            Spacer(Modifier.width(8.dp))

            EntryCard(
                icon = R.drawable.image,
                backgroundColor = Purple80,
                title = "Image Reader",
                subtitle = "\"Read text from images\"",
                onClick = { navController.navigate("chat/0?type=1") }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatInput(
    enable: Boolean,
    withImage: Boolean,
    onImageSelected: (Uri?) -> Unit = {},
    onMessageSend: (Message) -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    var imageUri = remember { mutableStateOf<Uri?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let {
            imageUri.value = it
            onImageSelected(it)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        if (withImage) IconButton(onClick = {
            launcher.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, Modifier.padding(10.dp)) {
            Image(
                painter = painterResource(id = if (imageUri.value != null) R.drawable.add_done else R.drawable.add_other),
                contentDescription = "Add Other Resources.",
                Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).padding(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(0.5f),
                focusedContainerColor = Color.White.copy(0.5f)
            )
        )
        IconButton(onClick = {
            keyboardController?.hide()
            if (text.isNotEmpty() || imageUri.value != null) {
                val msgType = if (imageUri.value != null) MessageType.IMAGE else MessageType.TEXT
                onMessageSend(Message(text, true, 0, type = msgType, content = imageUri.value))
                text = ""
                imageUri.value = null
            }
        }, enabled = enable) {
            Icon(painter = painterResource(id = R.drawable.up), contentDescription = "Send", Modifier.size(36.dp))
        }
    }
}

@Composable
fun ColumnScope.ChatBubble(
    message: Message,
    vm: ChatViewModel? = null,
    scope: CoroutineScope = rememberCoroutineScope(),
    imageViewerState: ImagePreviewerState = rememberPreviewerState(pageCount = { 1 })
) {
    if (message.text.isNotEmpty()) ChatBubbleBox(isUser = message.isUser) {
        SelectionContainer {
            if (message.isUser) {
                Text(
                    text = message.text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Markdown(
                    content = message.text,
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.titleLarge,
                        h2 = MaterialTheme.typography.titleMedium,
                        h3 = MaterialTheme.typography.titleSmall,
                        h4 = MaterialTheme.typography.titleSmall,
                        h5 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        h6 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        paragraph = MaterialTheme.typography.bodyLarge
                    )
                )
            }
        }
    }
    if (message.type == MessageType.IMAGE && message.content != null) {
        ChatBubbleBox(isUser = message.isUser) {
            val imageUri = message.content as? Uri
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = "User Image",
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun ColumnScope.ChatBubbleBox(isUser: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .padding(5.dp)
            .align(if (isUser) Alignment.End else Alignment.Start)
            .wrapContentWidth(align = if (isUser) Alignment.End else Alignment.Start)
            .widthIn(min = 40.dp, max = 300.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 48f,
                    topEnd = 48f,
                    bottomStart = if (isUser) 48f else 0f,
                    bottomEnd = if (isUser) 0f else 48f
                )
            )
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun BoxScope.PreviewBubble(preview: Uri) {
    val density = LocalDensity.current
    val arrowHeight = 8.dp

    val bubbleShape = remember {
        getBubbleShape(
            density = density,
            cornerRadius = 10.dp,
            arrowWidth = 20.dp,
            arrowHeight = arrowHeight,
            arrowOffset = 30.dp
        )
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .shadow(10.dp, bubbleShape)
            .padding(start = 5.dp)
            .fillMaxWidth(0.2f)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Image(
            painter = rememberAsyncImagePainter(preview),
            contentDescription = "Image Description",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = arrowHeight)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}

fun getBubbleShape(
    density: Density,
    cornerRadius: Dp,
    arrowWidth: Dp,
    arrowHeight: Dp,
    arrowOffset: Dp
): GenericShape {
    val cornerRadiusPx: Float
    val arrowWidthPx: Float
    val arrowHeightPx: Float
    val arrowOffsetPx: Float

    with(density) {
        cornerRadiusPx = cornerRadius.toPx()
        arrowWidthPx = arrowWidth.toPx()
        arrowHeightPx = arrowHeight.toPx()
        arrowOffsetPx = arrowOffset.toPx()
    }

    return GenericShape { size: Size, layoutDirection: LayoutDirection ->
        this.addRoundRect(
            RoundRect(
                rect = Rect(
                    offset = Offset(0f, 0f),
                    size = Size(size.width, size.height - arrowHeightPx)
                ),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        )
        moveTo(arrowOffsetPx, size.height - arrowHeightPx)
        lineTo(arrowOffsetPx + arrowWidthPx / 2, size.height)
        lineTo(arrowOffsetPx + arrowWidthPx, size.height - arrowHeightPx)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 56.dp, start = 20.dp)) {
        Text(
            text = "Let's Chat",
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 30.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = "See what I can do for you",
            style = MaterialTheme.typography.titleLarge,
            lineHeight = 24.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.EntryCard(
    icon: Int,
    backgroundColor: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .weight(0.5f)
            .aspectRatio(0.8f),
        shape = RoundedCornerShape(20),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            RoundIcon(id = icon, backgoundColor = Color.White)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.Black
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.next),
                    contentDescription = "Icon Description",
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(36.dp),
                )
            }
        }
    }
}

@Composable
fun RoundIcon(id: Int, backgoundColor: Color) {
    Box(
        modifier = Modifier
            .padding(end = 16.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(backgoundColor)
    ) {
        Image(
            painter = painterResource(id),
            contentDescription = "Icon Description",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(navController: NavController) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        contentWindowInsets = WindowInsets(16, 20, 16, 0),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Greeting("Android")
                IconButton(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 56.dp, end = 8.dp)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(it)
        ) {
            MainEntryCards(
                navController = navController,
                selectedIndex = 0,
                selectedBackend = 0
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VQA(navController: NavController) {}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Photo(navController: NavController) {}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ChatBotTheme {
        Greeting("Android")
    }
}