package com.clauderemote.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clauderemote.app.data.ChatViewModel
import kotlinx.coroutines.launch
import com.clauderemote.app.data.Connection
import com.clauderemote.app.data.ImageAttachment
import com.clauderemote.app.ui.chat.ChatScreen
import com.clauderemote.app.ui.drawer.ConversationDrawer
import com.clauderemote.app.ui.feedback.ToastHost
import com.clauderemote.app.ui.feedback.ToastKind
import com.clauderemote.app.ui.feedback.rememberToastState
import com.clauderemote.app.ui.i18n.LocalLang
import com.clauderemote.app.ui.i18n.langFor
import com.clauderemote.app.ui.i18n.tr
import com.clauderemote.app.ui.settings.SettingsScreen
import com.clauderemote.app.ui.setup.SetupScreen
import com.clauderemote.app.ui.sheet.AttachSheet
import com.clauderemote.app.ui.sheet.ModelSheet
import com.clauderemote.app.ui.sheet.PermissionSheet
import com.clauderemote.app.ui.sheet.QueueSendSheet
import com.clauderemote.app.ui.sheet.UsageSheet
import com.clauderemote.app.ui.theme.CdsMotion
import com.clauderemote.app.ui.theme.ClaudeRemoteTheme

/** 底部两个入口各自打开的次级面板；设置已改为整页，不再走底部弹层 */
private enum class ChatSheet { Permission, Model }

/** 任务运行中按下发送后暂存的内容，等用户选择排队还是打断 */
private data class PendingSend(val text: String, val images: List<ImageAttachment>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeApp(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val toastState = rememberToastState()

    var connection by remember { mutableStateOf<Connection?>(vm.savedConnection()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var chatSheet by remember { mutableStateOf<ChatSheet?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    var pendingSend by remember { mutableStateOf<PendingSend?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    val pendingAttachments = remember { androidx.compose.runtime.mutableStateListOf<ImageAttachment>() }

    // 取消自动登录：启动时不再自动连接，只把上次保存的地址/令牌预填到登录页，
    // 连接由用户在登录页点「连接」触发。

    // 从后台 / 锁屏回来时，SSE 断线期间的增量不会补发；间隔超过 2 秒就追一次进度
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stoppedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME ->
                    if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 2000) {
                        vm.resyncActive()
                    }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 连接成功后清掉 loading
    LaunchedEffect(state.boot, state.connectionError) {
        if (state.boot != null || state.connectionError != null) connecting = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
                val mime = context.contentResolver.getType(uri) ?: "image/png"
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val attachment = ImageAttachment(
                    name = "image-${System.currentTimeMillis()}",
                    mediaType = mime,
                    base64 = b64,
                    // 预览用缩略图，整张原图仍留在 base64 里发给模型
                    preview = imagePreview(bytes),
                )
                pendingAttachments.add(attachment)
            }
        }
    }

    // 普通文件：先抱回内存，发送时由 ViewModel 上传到电脑并记下路径
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
                if (bytes.size > 24 * 1024 * 1024) {
                    toastState.show("文件超过 24MB，先在电脑上处理吧", ToastKind.Warn)
                    return@runCatching
                }
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val name = context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cur ->
                        val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cur.moveToFirst()) cur.getString(idx) else null
                    }
                    ?.takeIf { it.isNotBlank() }
                    ?: "file-${System.currentTimeMillis()}"
                val isImage = mime.startsWith("image/")
                pendingAttachments.add(
                    ImageAttachment(
                        name = name,
                        mediaType = mime,
                        base64 = b64,
                        preview = if (isImage) imagePreview(bytes) else "",
                    )
                )
            }
        }
    }

    val dark = when (state.ui.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    ClaudeRemoteTheme(darkTheme = dark, fontScale = state.ui.fontScale) {
        // 系统栏图标的明暗要跟着 App 的主题走，不能只看系统设置
        val view = LocalView.current
        val window = (view.context as? android.app.Activity)?.window
        LaunchedEffect(dark) {
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }

        val connected = state.boot != null

        // 打开抽屉、面板、偏好页或切换会话前先收起键盘。
        // 输入框的焦点不会随页面切换自动清掉，否则新页面一出来输入法又跟着弹回去。
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        fun hideInput() {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, view)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
            }
        }

        // 全面屏返回：设置整页由这里收；会话抽屉自己处理返回（先退多选，再关抽屉），
        // 避免两个返回回调同时响应导致一按就把抽屉关掉。
        // 权限 / 模型面板和用量面板是 ModalBottomSheet，返回键由它们自己的对话框处理。
        BackHandler(enabled = showSettings) {
            showSettings = false
        }

        CompositionLocalProvider(LocalLang provides langFor(state.ui.language)) {
        // 轻提示文案在组合期取一次，回调里直接用（tr 不能在点击回调里调用）
        val connectedToast = tr("已连接到电脑", "Connected to your computer")
        val queuedToast = tr("已加入队列", "Added to queue")
        val interruptToast = tr("已打断当前任务", "Interrupted the current turn")
        val allowedToast = tr("已允许", "Allowed")
        val deniedToast = tr("已拒绝", "Denied")
        var announced by remember { mutableStateOf(false) }

        LaunchedEffect(state.boot) {
            if (state.boot != null && !announced) {
                announced = true
                toastState.show(connectedToast, ToastKind.Success)
            } else if (state.boot == null) {
                announced = false
            }
        }
        // 连上以后才提示断线；登录页的首次失败由页面内文案承担，不重复弹
        LaunchedEffect(state.connectionError) {
            val err = state.connectionError
            if (err != null && announced) toastState.show(err, ToastKind.Error)
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = connected,
            drawerContent = {
                ConversationDrawer(
                    drawerOpen = drawerState.isOpen,
                    conversations = state.conversations,
                    activeId = state.activeId,
                    onSelect = { id ->
                        hideInput()
                        scope.launch { drawerState.close() }
                        vm.openConversation(id)
                    },
                    onRename = { id, title -> vm.renameConversation(id, title) },
                    onTogglePin = { id -> vm.togglePin(id) },
                    onDelete = { ids -> vm.deleteConversations(ids) },
                    onOpenSettings = {
                        hideInput()
                        scope.launch { drawerState.close() }
                        showSettings = true
                    },
                    onSearch = { vm.searchConversations(it) },
                    searchQuery = state.searchQuery,
                    searchResults = state.searchResults,
                    searching = state.searching,
                    onClose = { scope.launch { drawerState.close() } },
                )
            },
        ) {
            Box(Modifier.fillMaxSize()) {
            if (!connected) {
                SetupScreen(
                    initial = connection,
                    connecting = connecting,
                    error = state.connectionError,
                    language = state.ui.language,
                    onLanguage = { vm.setLanguage(it) },
                    onConnect = { c ->
                        connecting = true
                        connection = c
                        vm.saveConnection(c)
                        vm.connect(c)
                    },
                )
            } else {
                ChatScreen(
                    state = state,
                    onOpenDrawer = { hideInput(); scope.launch { drawerState.open() } },
                    onNewChat = { hideInput(); vm.newConversation() },
                    onOpenPermission = { hideInput(); chatSheet = ChatSheet.Permission },
                    onOpenModel = {
                        hideInput()
                        vm.refreshModels()
                        chatSheet = ChatSheet.Model
                    },
                    onRename = { title ->
                        state.activeId?.let { vm.renameConversation(it, title) }
                    },
                    onSend = { text ->
                        val imgs = pendingAttachments.toList()
                        if (state.running) {
                            // 先问排队还是打断，附件保留到用户做决定
                            pendingSend = PendingSend(text, imgs)
                        } else {
                            pendingAttachments.clear()
                            vm.send(text, imgs)
                        }
                    },
                    onStop = { vm.stop() },
                    onAttach = { showAttach = true },
                    attachments = pendingAttachments.toList(),
                    onRemoveAttachment = { pendingAttachments.remove(it) },
                    onAnswer = { id, answers -> vm.answer(id, answers) },
                    onDraftConsumed = { vm.clearDraft() },
                    onDismissError = { vm.clearError() },
                    onRetry = { vm.retry() },
                    onDecide = { id, allow ->
                        vm.decide(id, allow)
                        toastState.show(if (allow) allowedToast else deniedToast, ToastKind.Info)
                    },
                )

                when (chatSheet) {
                    ChatSheet.Permission -> PermissionSheet(
                        state = state,
                        onDismiss = { chatSheet = null },
                        onPermission = { vm.setPermissionMode(it) },
                    )

                    ChatSheet.Model -> ModelSheet(
                        state = state,
                        onDismiss = { chatSheet = null },
                        onModel = { vm.setModel(it) },
                        onEffort = { vm.setEffort(it) },
                        onChannel = { vm.switchChannel(it) },
                        onRefreshModels = { vm.refreshModels() },
                    )

                    null -> Unit
                }

                // 运行中发送：排队，或者先打断当前任务
                pendingSend?.let { pending ->
                    QueueSendSheet(
                        onDismiss = {
                            // 返回键 / 点遮罩 / 取消都会走这里：把待发文字放回输入框，
                            // 别让它跟着面板一起消失（附件本来就没清，仍留在预览行里）
                            pendingSend = null
                            vm.restoreDraft(pending.text)
                        },
                        onQueue = {
                            pendingSend = null
                            pendingAttachments.clear()
                            vm.send(pending.text, pending.images, queued = true)
                            toastState.show(queuedToast, ToastKind.Info)
                        },
                        onInterrupt = {
                            pendingSend = null
                            pendingAttachments.clear()
                            vm.interruptAndSend(pending.text, pending.images)
                            toastState.show(interruptToast, ToastKind.Info)
                        },
                    )
                }

                if (showAttach) {
                    AttachSheet(
                        onDismiss = { showAttach = false },
                        onPickImages = {
                            showAttach = false
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onPickFiles = {
                            showAttach = false
                            filePicker.launch(arrayOf("*/*"))
                        },
                    )
                }

                if (showUsage) {
                    UsageSheet(
                        state = state,
                        onDismiss = { showUsage = false },
                        onRefresh = { vm.refreshUsage() },
                    )
                }

                // 设置整页：从右侧滑入，和官方 App 的层级切换一致
                AnimatedVisibility(
                    visible = showSettings,
                    enter = slideInHorizontally(animationSpec = tween(CdsMotion.DurSheet, easing = CdsMotion.EaseOut)) { it },
                    exit = slideOutHorizontally(animationSpec = tween(CdsMotion.DurBase, easing = CdsMotion.EaseSnap)) { it },
                ) {
                    SettingsScreen(
                        state = state,
                        onFontScale = { vm.setFontScale(it) },
                        onThemeMode = { vm.setThemeMode(it) },
                        onShowStats = { vm.setShowStats(it) },
                        onCodeWrap = { vm.setCodeWrap(it) },
                        onCurrency = { vm.setCurrency(it) },
                        onDetailLevel = { vm.setDetailLevel(it) },
                        onCollapseCommands = { vm.setCollapseCommands(it) },
                        onLanguage = { vm.setLanguage(it) },
                        onOpenUsage = {
                            showUsage = true
                            vm.refreshUsage()
                        },
                        onDisconnect = {
                            // 不只清登录信息：SSE、后台请求和当前会话状态一起复位，
                            // 否则界面会停在聊天页、连接还在后台偷偷重连
                            vm.disconnect()
                            connection = null
                            pendingSend = null
                            pendingAttachments.clear()
                            chatSheet = null
                            showUsage = false
                            showAttach = false
                            showSettings = false
                        },
                        onClose = { showSettings = false },
                    )
                }
            }

                // 轻提示：浮在输入框上方，连接状态、排队 / 打断、权限结果都从这里出现
                ToastHost(
                    state = toastState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 92.dp),
                )
            }
    }
        }
    }
}

/**
 * 把原图压成一张 1080px 内的 JPEG 预览。
 *
 * 预览只用于界面缩略图和历史记录，原始 base64 仍完整发给模型；
 * 小图也让服务端保存历史时不会因为 preview 太长而把它丢掉。
 */
private fun imagePreview(bytes: ByteArray): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""
    var sample = 1
    while (bounds.outWidth / (sample * 2) > 1080 || bounds.outHeight / (sample * 2) > 1080) {
        sample *= 2
    }
    val bitmap = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return ""
    val out = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out)
    bitmap.recycle()
    return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
