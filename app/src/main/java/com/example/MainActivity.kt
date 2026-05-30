package com.example

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import kotlin.math.roundToInt
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.alarm.AlarmConfig
import com.example.alarm.AlarmReceiver
import com.example.alarm.AlarmStorage
import com.example.alarm.RingtoneHelper
import com.example.ui.theme.MyApplicationTheme
import com.example.weather.WeatherInfo
import com.example.weather.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var alarmStorage: AlarmStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        alarmStorage = AlarmStorage(this)

        setContent {
            MyApplicationTheme {
                SleepCycleScreen(
                    modifier = Modifier.fillMaxSize(),
                    fusedLocationClient = fusedLocationClient,
                    alarmStorage = alarmStorage,
                    onCancelAlarm = { cancelAlarm() },
                    onScheduleAlarm = { alarmTimeMs -> scheduleAlarm(alarmTimeMs) }
                )
            }
        }
    }

    private fun scheduleAlarm(triggerTimeMs: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    // Fallback to normal alarm or ask user
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    val settingsIntent = Intent().apply {
                        action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    }
                    startActivity(settingsIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
            
            // Sync Homescreen Widget
            updateHomescreenWidgets()
            
            val sdf = SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault())
            val formatted = sdf.format(Date(triggerTimeMs))
            Toast.makeText(this, "Đã đặt báo thức sinh học lúc $formatted", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể lên lịch báo thức chính xác. Đã chuyển sang chế độ dự phòng.", Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        alarmStorage.clearAlarm()
        
        updateHomescreenWidgets()
        Toast.makeText(this, "Đã hủy báo thức sinh học", Toast.LENGTH_SHORT).show()
    }

    private fun updateHomescreenWidgets() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)

            // SleepCycleWidgetProvider
            val compSleep = ComponentName(this, com.example.widget.SleepCycleWidgetProvider::class.java)
            val idsSleep = appWidgetManager.getAppWidgetIds(compSleep)
            if (idsSleep != null && idsSleep.isNotEmpty()) {
                val intent = Intent(this, com.example.widget.SleepCycleWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, idsSleep)
                }
                sendBroadcast(intent)
            }

            // Widget2x2Provider
            val comp2x2 = ComponentName(this, com.example.widget.Widget2x2Provider::class.java)
            val ids2x2 = appWidgetManager.getAppWidgetIds(comp2x2)
            if (ids2x2 != null && ids2x2.isNotEmpty()) {
                val intent = Intent(this, com.example.widget.Widget2x2Provider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids2x2)
                }
                sendBroadcast(intent)
            }

            // Widget1x4Provider
            val comp1x4 = ComponentName(this, com.example.widget.Widget1x4Provider::class.java)
            val ids1x4 = appWidgetManager.getAppWidgetIds(comp1x4)
            if (ids1x4 != null && ids1x4.isNotEmpty()) {
                val intent = Intent(this, com.example.widget.Widget1x4Provider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids1x4)
                }
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun SleepCycleScreen(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient,
    alarmStorage: AlarmStorage,
    onCancelAlarm: () -> Unit,
    onScheduleAlarm: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State Variables
    var currentTab by remember { mutableStateOf(0) } // 0 = Báo thức, 1 = Thống kê, 2 = Cá nhân
    var cycles by remember { mutableStateOf(3) } // 1 cycle = 90 mins, default is 3
    var fallAsleepMinutes by remember { mutableStateOf(0) } // 0, 5, 10 or 15 mins
    var selectedRingtoneName by remember { mutableStateOf("Mặc định hệ thống") }
    var selectedRingtoneUri by remember { mutableStateOf("") }
    
    // Loaded alarm configuration status
    var initialAlarm by remember { mutableStateOf(alarmStorage.getAlarm()) }
    var isAlarmScheduled by remember { mutableStateOf(initialAlarm.isEnabled && initialAlarm.triggerTimeMs > System.currentTimeMillis()) }

    var alarmType by remember { mutableStateOf(initialAlarm.alarmType) }
    var selectedDays by remember {
        mutableStateOf(
            if (initialAlarm.daysOfWeek.isNotEmpty()) {
                initialAlarm.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            } else {
                emptySet()
            }
        )
    }

    // Observing Room Database History
    val database = remember { com.example.alarm.SleepDatabase.getDatabase(context) }
    val historyList by database.historyDao().getAllHistory().collectAsState(initial = emptyList())

    // Ringtones and Music States
    var isRingtoneDialogVisible by remember { mutableStateOf(false) }
    val defaultRingtones = remember(context) {
        listOf(
            com.example.alarm.RingtoneOption("Mặc định hệ thống", android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)?.toString() ?: ""),
            com.example.alarm.RingtoneOption("Army", "android.resource://" + context.packageName + "/raw/army")
        )
    }
    var presetRingtones by remember { mutableStateOf<List<com.example.alarm.RingtoneOption>>(defaultRingtones) }
    var showDeviceMusic by remember { mutableStateOf(false) }
    var deviceMusicList by remember { mutableStateOf<List<com.example.alarm.RingtoneOption>>(emptyList()) }

    LaunchedEffect(isRingtoneDialogVisible) {
        if (isRingtoneDialogVisible) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RingtoneHelper.getSystemRingtones(context)
            }.let { presetRingtones = it }
        }
    }

    LaunchedEffect(isRingtoneDialogVisible, showDeviceMusic) {
        if (isRingtoneDialogVisible && showDeviceMusic) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RingtoneHelper.getDeviceMusic(context)
            }.let { deviceMusicList = it }
        }
    }

    // Music Permission request
    val musicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Đã cấp quyền truy cập nhạc trên thiết bị!", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    RingtoneHelper.getDeviceMusic(context)
                }
                deviceMusicList = list
            }
        } else {
            Toast.makeText(context, "Quyền bị từ chối. Vui lòng cấp quyền trong phần Cấu hình ứng dụng.", Toast.LENGTH_LONG).show()
        }
    }

    var userLatitude by remember { mutableStateOf<Double?>(null) }
    var userLongitude by remember { mutableStateOf<Double?>(null) }
    var districtName by remember { mutableStateOf<String?>(null) }
    
    // Real-time Weather
    var currentWeatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    var isWeatherLoading by remember { mutableStateOf(false) }

    // Alarm Time Weather
    var alarmWeatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    var isAlarmWeatherLoading by remember { mutableStateOf(false) }

    // Time Calculations
    val currentTime = remember { System.currentTimeMillis() }
    val totalSleepMinutes = (cycles * 90) + fallAsleepMinutes
    val computedAlarmTimeMs = currentTime + (totalSleepMinutes * 60 * 1000)

    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val finalAlarmTimeFormatted = dateFormat.format(Date(computedAlarmTimeMs))
    
    // Synchronize states upon initial loading
    LaunchedEffect(Unit) {
        if (isAlarmScheduled) {
            cycles = initialAlarm.cycles
            fallAsleepMinutes = initialAlarm.fallAsleepMinutes
            selectedRingtoneName = initialAlarm.ringtoneName
            selectedRingtoneUri = initialAlarm.ringtoneUri
            alarmType = initialAlarm.alarmType
            selectedDays = if (initialAlarm.daysOfWeek.isNotEmpty()) {
                initialAlarm.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            } else {
                emptySet()
            }
        }
    }

    // Dynamic Location & Weather Estimation
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permMap ->
        val fineLocationGranted = permMap[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permMap[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                            if (loc != null) {
                                userLatitude = loc.latitude
                                userLongitude = loc.longitude
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val geocoder = Geocoder(context.applicationContext, Locale("vi", "VN"))
                                        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                        val subAdmin = addresses?.firstOrNull()?.subAdminArea
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            districtName = subAdmin
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Sync state on Resume (to catch cancellations/changes from external widgets)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // Profile state
    var userName by remember { mutableStateOf(alarmStorage.getUserName()) }
    var userAvatarUri by remember { mutableStateOf(alarmStorage.getUserAvatarUri()) }
    var showNameDialog by remember { mutableStateOf(false) }

    // New Avatar Editing State
    var candidateAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var showAvatarEditor by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            candidateAvatarUri = uri
            showAvatarEditor = true
        }
    }

    if (showAvatarEditor && candidateAvatarUri != null) {
        // Avatar Editor Dialog with Zoom/Pan
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        AlertDialog(
            onDismissRequest = { showAvatarEditor = false },
            title = { Text("Chỉnh sửa ảnh đại diện") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale *= zoom
                                offset += pan
                            }
                        }
                ) {
                    coil.compose.AsyncImage(
                        model = candidateAvatarUri,
                        contentDescription = "Edit Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uriString = candidateAvatarUri.toString()
                    userAvatarUri = uriString
                    alarmStorage.setUserAvatarUri(uriString)
                    // Persist permission for the URI
                    context.contentResolver.takePersistableUriPermission(candidateAvatarUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    showAvatarEditor = false
                }) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarEditor = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val freshAlarm = alarmStorage.getAlarm()
                initialAlarm = freshAlarm
                val isActive = freshAlarm.isEnabled && freshAlarm.triggerTimeMs > System.currentTimeMillis()
                isAlarmScheduled = isActive
                if (isActive) {
                    cycles = freshAlarm.cycles
                    fallAsleepMinutes = freshAlarm.fallAsleepMinutes
                    selectedRingtoneName = freshAlarm.ringtoneName
                    selectedRingtoneUri = freshAlarm.ringtoneUri
                    alarmType = freshAlarm.alarmType
                    selectedDays = if (freshAlarm.daysOfWeek.isNotEmpty()) {
                        freshAlarm.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }.toSet()
                    } else {
                        emptySet()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-Trigger location & notification permission request safely on app start
    var hasRequestedPermissions by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasRequestedPermissions) {
            hasRequestedPermissions = true
            try {
                // Settle down UI thread before requesting
                kotlinx.coroutines.delay(300)
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissions.toTypedArray())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Refresh current weather whenever location changes
    LaunchedEffect(userLatitude, userLongitude) {
        if (userLatitude != null && userLongitude != null) {
            kotlinx.coroutines.delay(1000)
            isWeatherLoading = true
            currentWeatherInfo = WeatherService.getCurrentWeather(userLatitude, userLongitude)
            isWeatherLoading = false
        }
    }

    // Refresh alarm weather whenever alarm time changes or location changes
    LaunchedEffect(computedAlarmTimeMs, userLatitude, userLongitude) {
        if (userLatitude != null && userLongitude != null) {
            kotlinx.coroutines.delay(1000)
            isAlarmWeatherLoading = true
            alarmWeatherInfo = WeatherService.getEstimatedWeather(context, userLatitude, userLongitude, computedAlarmTimeMs)
            isAlarmWeatherLoading = false
        }
    }

    // RINGTONE DIALOG
    if (isRingtoneDialogVisible) {
        AlertDialog(
            onDismissRequest = { isRingtoneDialogVisible = false },
            title = {
                Text(
                    text = "Chọn nhạc âm báo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            val isMusicPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                            } else {
                                ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showDeviceMusic) Color(0x33818CF8) else Color(0x11818CF8))
                                    .clickable {
                                        showDeviceMusic = !showDeviceMusic
                                        if (!isMusicPermissionGranted) {
                                            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                Manifest.permission.READ_MEDIA_AUDIO
                                            } else {
                                                Manifest.permission.READ_EXTERNAL_STORAGE
                                            }
                                            musicPermissionLauncher.launch(perm)
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Nhạc trên thiết bị",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (showDeviceMusic) "Đang hiển thị nhạc trên máy • Nhấn để thu gọn" else "Nhấn để hiển thị danh sách nhạc trên thiết bị",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (showDeviceMusic) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (showDeviceMusic) {
                            if (deviceMusicList.isEmpty()) {
                                item {
                                    Text(
                                        text = "Không tìm thấy tệp nhạc nào.",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
                                    )
                                }
                            } else {
                                items(deviceMusicList.size) { index ->
                                    val music = deviceMusicList[index]
                                    val isSelected = selectedRingtoneName == music.name
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0x2210B981) else Color.Transparent)
                                            .clickable {
                                                selectedRingtoneName = music.name
                                                selectedRingtoneUri = music.uriString
                                                isRingtoneDialogVisible = false
                                            }
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.MusicVideo else Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF10B981) else Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = music.name,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Preset/Regular ringtones list separator
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(
                                color = Color(0x1A94A3B8),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        items(presetRingtones.size) { idx ->
                            val item = presetRingtones[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedRingtoneName == item.name) Color(0x276366F1) else Color.Transparent)
                                    .clickable {
                                        selectedRingtoneName = item.name
                                        selectedRingtoneUri = item.uriString
                                        isRingtoneDialogVisible = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedRingtoneName == item.name) Icons.Default.MusicVideo else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (selectedRingtoneName == item.name) Color(0xFF818CF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item.name,
                                    color = if (selectedRingtoneName == item.name) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedRingtoneName == item.name) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isRingtoneDialogVisible = false }) {
                    Text("Đóng", color = Color(0xFF818CF8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0E14), // Sleek Midnight
                        Color(0xFF0F121C), // Deep Indigo tint
                        Color(0xFF0B0E14)  // Sleek Midnight
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Surface(
                    color = Color(0xF2090C11), // Translucent dark midnight slate
                    tonalElevation = 8.dp,
                    border = BorderStroke(0.5.dp, Color(0x33818CF8)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 6.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Báo thức bottom tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .alpha(if (currentTab == 0) 1f else 0.4f)
                                .clickable { currentTab = 0 }
                                .padding(vertical = 2.dp, horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Báo thức",
                                tint = if (currentTab == 0) Color(0xFF818CF8) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Báo thức",
                                fontSize = 10.sp,
                                fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentTab == 0) Color(0xFF818CF8) else Color(0xFFCBD5E1)
                            )
                        }

                        // Thống kê bottom tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .alpha(if (currentTab == 1) 1f else 0.4f)
                                .clickable { currentTab = 1 }
                                .padding(vertical = 2.dp, horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = "Thống kê",
                                tint = if (currentTab == 1) Color(0xFF818CF8) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Thống kê",
                                fontSize = 10.sp,
                                fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentTab == 1) Color(0xFF818CF8) else Color(0xFFCBD5E1)
                            )
                        }

                        // Cá nhân bottom tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .alpha(if (currentTab == 2) 1f else 0.4f)
                                .clickable { currentTab = 2 }
                                .padding(vertical = 2.dp, horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Cá nhân",
                                tint = if (currentTab == 2) Color(0xFF818CF8) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Cá nhân",
                                fontSize = 10.sp,
                                fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentTab == 2) Color(0xFF818CF8) else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
        // App Header (Sleek Theme style)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "BÁO THỨC SINH HỌC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF818CF8), // text-indigo-400
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Tối ưu hóa giấc ngủ của bạn",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B), // text-slate-500
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Header Settings Circle Button (Links functionally to Ringtone Dialog)
                IconButton(
                    onClick = { if (!isAlarmScheduled) isRingtoneDialogVisible = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x331E293B))
                        .border(1.dp, Color(0x3364748B), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cài đặt âm báo",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (currentTab == 0) {
            // HYBRID CLOCK AREA WITH FLOATING WEATHER
            item {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 260.dp, height = 276.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // Main Clock Circle Body
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        // Sleek outer thin border + subtle inner shadow/glow emulation
                        .border(1.dp, Color(0xFF1E293B), CircleShape)
                        .background(Color(0xFF0F121C).copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner accent ring border (inset 16.dp = size 208.dp)
                    Box(
                        modifier = Modifier
                            .size(208.dp)
                            .border(0.5.dp, Color(0x2264748B), CircleShape)
                    )

                    // Draw minimalist hour markers (12 is Indigo, others are soft Slate)
                    val alarmHour = Calendar.getInstance().apply { timeInMillis = computedAlarmTimeMs }.get(Calendar.HOUR_OF_DAY)
                    val alarmMinute = Calendar.getInstance().apply { timeInMillis = computedAlarmTimeMs }.get(Calendar.MINUTE)

                    Canvas(modifier = Modifier.size(208.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.width / 2

                        val markLength = 10.dp.toPx()
                        val positions = listOf(0, 90, 180, 270)
                        for (angleDeg in positions) {
                            val angleRad = Math.toRadians(angleDeg.toDouble())
                            val isTop = angleDeg == 270
                            val markerColor = if (isTop) Color(0xFF818CF8) else Color(0x6664748B)

                            val start = Offset(
                                (center.x + (radius - markLength) * cos(angleRad)).toFloat(),
                                (center.y + (radius - markLength) * sin(angleRad)).toFloat()
                            )
                            val end = Offset(
                                (center.x + radius * cos(angleRad)).toFloat(),
                                (center.y + radius * sin(angleRad)).toFloat()
                            )
                            drawLine(
                                color = markerColor,
                                start = start,
                                end = end,
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Hand rotation calculus (relative to 12 o'clock pointing)
                        val minuteAngle = Math.toRadians((alarmMinute * 6 - 90).toDouble())
                        val hourAngle = Math.toRadians(((alarmHour % 12) * 30 + alarmMinute * 0.5 - 90))

                        // Lengths adjusted precisely so they look incredibly high-end and do not overlap center text
                        val hourHandStart = radius * 0.44f
                        val hourHandEnd = radius * 0.68f
                        val minuteHandStart = radius * 0.44f
                        val minuteHandEnd = radius * 0.86f

                        // Hour Hand (Indigo Accent)
                        drawLine(
                            color = Color(0xFF818CF8),
                            start = Offset(
                                (center.x + hourHandStart * cos(hourAngle)).toFloat(),
                                (center.y + hourHandStart * sin(hourAngle)).toFloat()
                            ),
                            end = Offset(
                                (center.x + hourHandEnd * cos(hourAngle)).toFloat(),
                                (center.y + hourHandEnd * sin(hourAngle)).toFloat()
                            ),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Minute Hand (Sleek Slate/Lavender indicator)
                        drawLine(
                            color = Color(0xFF4F46E5),
                            start = Offset(
                                (center.x + minuteHandStart * cos(minuteAngle)).toFloat(),
                                (center.y + minuteHandStart * sin(minuteAngle)).toFloat()
                            ),
                            end = Offset(
                                (center.x + minuteHandEnd * cos(minuteAngle)).toFloat(),
                                (center.y + minuteHandEnd * sin(minuteAngle)).toFloat()
                            ),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Digital text display inside Clock circle
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = finalAlarmTimeFormatted,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White,
                            letterSpacing = (-1.5).sp
                        )
                        Text(
                            text = if (computedAlarmTimeMs > currentTime) {
                                val calendar = Calendar.getInstance().apply { timeInMillis = computedAlarmTimeMs }
                                if (calendar.get(Calendar.AM_PM) == Calendar.AM) "SÁNG" else "CHIỀU"
                            } else "AM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8),
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // Floating Weather Widget overlapping the bottom edge of the clock
                val alarmForecast = alarmWeatherInfo
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEA111622)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, Color(0x3364748B)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isAlarmWeatherLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFF818CF8)
                            )
                        } else {
                            val weatherIcon = when (alarmForecast?.type) {
                                "sunny" -> Icons.Default.WbSunny
                                "rainy" -> Icons.Default.WaterDrop
                                "windy" -> Icons.Default.Air
                                "thunderstorm" -> Icons.Default.Thunderstorm
                                else -> Icons.Default.Cloud
                            }
                            val iconColor = when (alarmForecast?.type) {
                                "sunny" -> Color(0xFFFBBF24)
                                "rainy" -> Color(0xFF60A5FA)
                                "windy" -> Color(0xFFCBD5E1)
                                "thunderstorm" -> Color(0xFFA78BFA)
                                else -> Color(0xFF818CF8)
                            }
                            Icon(
                                imageVector = weatherIcon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Column {
                            val sdfMin = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                            val forecastTime = sdfMin.format(Date(computedAlarmTimeMs))
                            Text(
                                text = "Dự báo $forecastTime",
                                fontSize = 9.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 10.sp
                            )
                            val weatherString = if (alarmForecast != null) {
                                "${alarmForecast.temperature}°C • ${alarmForecast.description}"
                            } else {
                                "..."
                            }
                            Text(
                                text = weatherString,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Stats Summary (Sleek Side-by-Side dual column layout)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Card: Total sleep hours in 24-hour format
                val hh = totalSleepMinutes / 60
                val mm = totalSleepMinutes % 60
                val totalTimeFormatted = String.format(Locale.getDefault(), "%02d:%02d", hh, mm)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "TỔNG THỜI GIAN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = totalTimeFormatted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // Right Card: Number of sleep cycles
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SỐ CHU KỲ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$cycles Chu kỳ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }

        // Active State Status Ribbon below Stats
        item {
            AnimatedVisibility(
                visible = isAlarmScheduled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val sdf = SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault())
                val activeTime = sdf.format(Date(initialAlarm.triggerTimeMs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1B10B981), RoundedCornerShape(12.dp))
                        .border(0.5.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Báo thức đã bật: $activeTime (${initialAlarm.cycles} chu kỳ)",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // WORKINGS OF SLEEP SCIENCE CARD (Cài đặt chu kỳ ngủ)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chọn số chu kỳ Ngủ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // "Khuyên dùng" indicator pill
                        if (cycles in 5..6) {
                            Text(
                                text = "Khuyên dùng",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF818CF8),
                                modifier = Modifier
                                    .background(Color(0x1F818CF8), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Slider(
                        value = cycles.toFloat(),
                        onValueChange = { if (!isAlarmScheduled) cycles = it.roundToInt() },
                        valueRange = 1f..7f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF4F46E5),
                            inactiveTrackColor = Color(0xFF1E293B),
                            thumbColor = Color.White,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Symmetric monospace step intervals ranging from 1 to 7
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 Chu kỳ", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("2", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("3", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("4", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("5", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("6", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("7 Chu kỳ", fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Suggested chips selection
                    Text(
                        text = "Lựa chọn nhanh đề xuất:",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val suggested = listOf(3, 5, 7)
                        suggested.forEach { sug ->
                            val isSelected = cycles == sug
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0x334F46E5) else Color(0xFF0F121C))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF818CF8) else Color(0x1A64748B),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { if (!isAlarmScheduled) cycles = sug }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$sug cycles",
                                    color = if (isSelected) Color.White else Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Scientific Advice
                    val advice = when (cycles) {
                        1 -> "Siêu ngắn (1.5h): Thích hợp cho giấc ngủ trưa phục hồi nhanh sinh lực sóng não."
                        2 -> "Ngắn hạn (3.0h): Giúp tỉnh táo tức thì để xử lý công việc khẩn cấp."
                        3 -> "Khuyên nghị tối thiểu (4.5h): Thích hợp khi làm việc muộn, đủ hoàn thành 3 vòng ngủ sâu ngắn."
                        4 -> "Phục hồi mức trung bình (6.0h): Sức khỏe được bảo toàn đầy đủ, thích hợp cho ngày bận rộn."
                        5 -> "Tối ưu xuất sắc (7.5h): Khoa học khuyên dùng để tinh thần sảng khoái và bảo vệ tim mạch tối đa."
                        6 -> "Ngủ dầy đủ tiêu chuẩn (9.0h): Cơ thể thải độc hoàn hảo, hồi phục sinh lực và cơ bắp toàn diện."
                        else -> "Thời lượng ngủ kéo dài (${cycles * 1.5}h): Thích hợp để bù đắp năng lượng sau chuỗi ngày kiệt sức."
                    }

                    Text(
                        text = advice,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // FALL ASLEEP OFFSET BUTTON CHIPS (Thời gian ru ngủ)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thời gian chìm vào giấc ngủ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val offsets = listOf(0, 5, 10, 15)
                        offsets.forEach { offset ->
                            val isSelected = fallAsleepMinutes == offset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF4F46E5) else Color(0xFF0B0E14))
                                    .border(0.5.dp, if (isSelected) Color(0xFF818CF8) else Color(0x3364748B), RoundedCornerShape(12.dp))
                                    .clickable { if (!isAlarmScheduled) fallAsleepMinutes = offset }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (offset == 0) "Có sẵn" else "+$offset p",
                                    color = if (isSelected) Color.White else Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "Khoa học chứng minh con người trung bình mất 10-15 phút để đi vào giấc ngủ thật sự sóng chậm.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // GPS Location & Weather Sync Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                      ) {
                          Icon(
                              imageVector = Icons.Default.LocationOn,
                              contentDescription = null,
                              tint = Color(0xFF38BDF8),
                              modifier = Modifier.size(18.dp)
                          )
                          Spacer(modifier = Modifier.width(8.dp))
                          Text(
                              text = "Vị trí hiện tại của bạn",
                              fontWeight = FontWeight.Bold,
                              fontSize = 14.sp,
                              color = Color.White
                          )
                          Spacer(modifier = Modifier.weight(1f))
                      }
                      
                      Spacer(modifier = Modifier.height(12.dp))

                      if (userLatitude != null && userLongitude != null) {
                          Row(
                              modifier = Modifier.fillMaxWidth(),
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.SpaceBetween
                          ) {
                              Column {
                                  Text(
                                      text = "Huyện hiện tại",
                                      color = Color(0xFF64748B),
                                      fontSize = 11.sp
                                  )
                                  Text(
                                      text = districtName ?: "Đang xác định...",
                                      color = Color(0xFFE2E8F0),
                                      fontSize = 13.sp,
                                      fontWeight = FontWeight.Medium
                                  )
                              }
                              
                              // Real-Time Weather Display
                              if (isWeatherLoading) {
                                  CircularProgressIndicator(modifier = Modifier.size(20.dp))
                              } else if (currentWeatherInfo != null) {
                                  Column(horizontalAlignment = Alignment.End) {
                                      Row(verticalAlignment = Alignment.CenterVertically) {
                                          Icon(
                                              imageVector = Icons.Default.Cloud,
                                              contentDescription = null,
                                              tint = Color.White,
                                              modifier = Modifier.size(16.dp)
                                          )
                                          Spacer(modifier = Modifier.width(4.dp))
                                          Text(
                                              text = "${currentWeatherInfo!!.temperature}°C",
                                              color = Color.White,
                                              fontSize = 16.sp,
                                              fontWeight = FontWeight.Bold
                                          )
                                      }
                                      Text(
                                          text = currentWeatherInfo!!.description,
                                          color = Color(0xFF64748B),
                                          fontSize = 10.sp
                                      )
                                  }
                              }
                          }
                      } else {
                          Text(
                              text = "Đang quét GPS tự động và đồng bộ thời tiết khu vực...",
                              color = Color(0xFF64748B),
                              fontSize = 12.sp,
                              fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                          )
                      }
                  }
              }
          }

        // OPTIONS AND DAY-OF-WEEK SELECTOR CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tùy chọn Báo thức",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Single, Repeat, Custom Selector (Row of 3 cards or segments)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val alarmTypes = listOf(
                            "single" to "Một lần",
                            "repeat" to "Lặp lại",
                            "custom" to "Tùy chỉnh"
                        )
                        alarmTypes.forEach { (typeKey, label) ->
                            val isTypeSelected = alarmType == typeKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isTypeSelected) Color(0xFF4F46E5) else Color(0xFF0F121C))
                                    .border(
                                        width = 0.5.dp,
                                        color = if (isTypeSelected) Color(0xFF818CF8) else Color(0x3364748B),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { if (!isAlarmScheduled) alarmType = typeKey }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isTypeSelected) Color.White else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    if (alarmType == "repeat") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x12818CF8)),
                            border = BorderStroke(0.5.dp, Color(0xFF818CF8).copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Báo thức sẽ tự động lặp lại 5 lần, mỗi lần cách nhau 5 phút nếu không được tắt hoàn toàn.",
                                    color = Color(0xFFC7D2FE),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Day of the Week Selector
                    val isAllowedToSelectDays = alarmType == "custom"
                    Text(
                        text = if (isAllowedToSelectDays) "Chọn ngày trong tuần:" else "Chọn ngày trong tuần (Chỉ có ở Tùy chỉnh):",
                        color = if (isAllowedToSelectDays) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dayLabels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                        dayLabels.forEachIndexed { index, label ->
                            val dayInt = index + 1
                            val isDaySelected = selectedDays.contains(dayInt)
                            val dayOpacity = if (isAllowedToSelectDays) 1.0f else 0.4f
                            
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDaySelected && isAllowedToSelectDays) 
                                            Color(0xFF818CF8).copy(alpha = dayOpacity) 
                                        else 
                                            Color(0xFF0F121C).copy(alpha = dayOpacity)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isDaySelected && isAllowedToSelectDays) 
                                            Color(0xFFC7D2FE).copy(alpha = dayOpacity) 
                                        else 
                                            Color(0x2264748B).copy(alpha = dayOpacity),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (!isAlarmScheduled && isAllowedToSelectDays) {
                                            selectedDays = if (isDaySelected) {
                                                selectedDays - dayInt
                                            } else {
                                                selectedDays + dayInt
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = (if (isDaySelected && isAllowedToSelectDays) Color.White else Color(0xFF64748B)).copy(alpha = dayOpacity),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // RINGTONE CHANGER PANEL
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!isAlarmScheduled) isRingtoneDialogVisible = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Âm Nhạc Báo Thức",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = selectedRingtoneName,
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                    if (!isAlarmScheduled) {
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Mở rộng",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }

        if (currentTab == 1) {
            // STATISTICS / ALARM HISTORY CARD
            item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thống kê & Lịch sử báo thức",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "1 Chu kỳ",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .background(Color(0x1F94A3B8), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tính từ lúc đặt báo thức cho tới khi kết thúc",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    if (historyList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Chưa có lịch sử báo thức thành công.",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val currentMillis = System.currentTimeMillis()
                                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                            
                                            database.historyDao().insertHistory(
                                                com.example.alarm.HistoryEntry(
                                                    dateString = sdf.format(Date(currentMillis - 24L * 3600L * 1000L)),
                                                    cycles = 5,
                                                    totalSleepMinutes = 5 * 90,
                                                    timestamp = currentMillis - 24L * 3600L * 1000L
                                                )
                                            )
                                            database.historyDao().insertHistory(
                                                com.example.alarm.HistoryEntry(
                                                    dateString = sdf.format(Date(currentMillis - 2L * 24L * 3600L * 1000L)),
                                                    cycles = 3,
                                                    totalSleepMinutes = 3 * 90 + 10,
                                                    timestamp = currentMillis - 2L * 24L * 3600L * 1000L
                                                )
                                            )
                                            database.historyDao().insertHistory(
                                                com.example.alarm.HistoryEntry(
                                                    dateString = sdf.format(Date(currentMillis - 3L * 24L * 3600L * 1000L)),
                                                    cycles = 7,
                                                    totalSleepMinutes = 7 * 90 + 15,
                                                    timestamp = currentMillis - 3L * 24L * 3600L * 1000L
                                                )
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22818CF8)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color(0xFFA5B4FC),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Mô phỏng dữ liệu lịch sử",
                                        color = Color(0xFFA5B4FC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            historyList.forEach { entry ->
                                val entryHour = entry.totalSleepMinutes / 60
                                val entryMin = entry.totalSleepMinutes % 60
                                val entrySleepStr = String.format(Locale.getDefault(), "%02d:%02d", entryHour, entryMin)
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F121C), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, Color(0x3364748B), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = entry.dateString,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${entry.cycles} chu kỳ ngủ",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = entrySleepStr,
                                            color = Color(0xFF34D399),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            database.historyDao().clearAllHistory()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Xóa toàn bộ lịch sử",
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        if (currentTab == 0) {
            // PROMINENT 'SET ALARM' BUTTONS (Cài đặt báo thức hành động)
            item {
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedContent(
                targetState = isAlarmScheduled,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "alarm_button_states"
            ) { scheduled ->
                if (scheduled) {
                    Button(
                        onClick = {
                            onCancelAlarm()
                            isAlarmScheduled = false
                            initialAlarm = alarmStorage.getAlarm()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(16.dp),
                                clip = false,
                                spotColor = Color(0xFFEF4444).copy(alpha = 0.5f)
                            ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlarmOff,
                            contentDescription = "Hủy báo thức",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "HỦY BÁO THỨC SINH HỌC",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val daysOfWeekStr = if (alarmType == "custom") selectedDays.joinToString(",") else ""
                            val newAlarm = AlarmConfig(
                                id = 1,
                                triggerTimeMs = computedAlarmTimeMs,
                                cycles = cycles,
                                fallAsleepMinutes = fallAsleepMinutes,
                                isEnabled = true,
                                ringtoneName = selectedRingtoneName,
                                ringtoneUri = selectedRingtoneUri,
                                alarmType = alarmType,
                                daysOfWeek = daysOfWeekStr
                            )
                            alarmStorage.saveAlarm(newAlarm)
                            alarmStorage.setRepeatCount(0)
                            onScheduleAlarm(computedAlarmTimeMs)
                            isAlarmScheduled = true
                            initialAlarm = newAlarm
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(16.dp),
                                clip = false,
                                spotColor = Color(0xFF4F46E5).copy(alpha = 0.5f)
                            ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Mở báo thức",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ĐẶT BÁO THỨC SINH HỌC",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }

        if (currentTab == 2) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0x33818CF8))
                                .border(1.5.dp, Color(0xFF818CF8), CircleShape)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (userAvatarUri.isNotEmpty()) {
                                coil.compose.AsyncImage(
                                    model = userAvatarUri,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Avatar",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = userName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showNameDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        
                        // Name Change Dialog
                        if (showNameDialog) {
                            var tempName by remember { mutableStateOf(userName) }
                            AlertDialog(
                                onDismissRequest = { showNameDialog = false },
                                title = { Text("Đổi tên") },
                                text = {
                                    OutlinedTextField(
                                        value = tempName,
                                        onValueChange = { tempName = it },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        userName = tempName
                                        alarmStorage.setUserName(tempName)
                                        showNameDialog = false
                                    }) {
                                        Text("Lưu")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showNameDialog = false }) {
                                        Text("Hủy")
                                    }
                                }
                            )
                        }
                        
                        Text(
                            text = "Người dùng Báo Thức Sinh Học",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        HorizontalDivider(color = Color(0x2264748B), thickness = 0.5.dp)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Phiên bản ứng dụng", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                            Text(text = "V2.0.6", fontSize = 13.sp, color = Color(0xFF818CF8), fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Trạng thái", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                            Text(text = "Đã tối ưu hóa", fontSize = 13.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Bản quyền", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                            Text(text = "© 2026 cường lâm", fontSize = 13.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            }
        }

    }
        }
    }
}
