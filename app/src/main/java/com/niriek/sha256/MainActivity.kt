package com.niriek.sha256

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// 1. 状态定义 (状态机)
sealed class ValidationState {
    object Idle : ValidationState()
    data class FileSelected(val name: String, val size: String) : ValidationState()
    object Calculating : ValidationState()
    object Success : ValidationState()
    object Error : ValidationState()
    object MissingInput : ValidationState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
                    Sha256ValidatorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sha256ValidatorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var validationState by remember { mutableStateOf<ValidationState>(ValidationState.Idle) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var calculatedHash by remember { mutableStateOf("") }
    var inputHash by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedUri = it
            val (name, size) = HashUtils.getFileMetadata(context, it)
            validationState = ValidationState.FileSelected(name, size)
            calculatedHash = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SHA256校验器", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

        val cardColor = when (validationState) {
            ValidationState.Success -> Color(0xFFA5D6A7)
            ValidationState.Error -> Color(0xFFFFCDD2)
            ValidationState.MissingInput -> Color(0xFFFFF9C4)
            else -> Color(0xFFE0E0E0)
        }

        Card(
            modifier = Modifier.fillMaxWidth().height(160.dp).clickable { filePicker.launch("*/*") },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (val s = validationState) {
                    ValidationState.Idle -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddCircle, null, Modifier.size(40.dp))
                        Text("点击添加待校验文件")
                    }
                    is ValidationState.FileSelected -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.name, fontWeight = FontWeight.Bold)
                        Text(s.size, fontSize = 12.sp)
                        Text("文件已添加，准备计算~")
                    }
                    ValidationState.Calculating -> CircularProgressIndicator(color = Color.DarkGray)
                    ValidationState.Success -> Icon(Icons.Default.CheckCircle, null, Modifier.size(60.dp), tint = Color(0xFF2E7D32))
                    ValidationState.Error -> Icon(Icons.Default.Close, null, Modifier.size(60.dp), tint = Color.Red)
                    ValidationState.MissingInput -> Icon(Icons.Default.Info, null, Modifier.size(60.dp), tint = Color(0xFFF57F17))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 计算结果显示区 (现在支持点击复制了)
        InfoBox(label = "Output SHA256", value = calculatedHash.ifEmpty { "根据您添加的文件计算输出" })

        Button(
            onClick = {
                selectedUri?.let {
                    validationState = ValidationState.Calculating
                    scope.launch {
                        calculatedHash = HashUtils.calculateSHA256(context, it)
                        validationState = ValidationState.FileSelected(
                            HashUtils.getFileMetadata(context, it).first,
                            HashUtils.getFileMetadata(context, it).second
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF556B2F))
        ) { Text("计算SHA256") }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = inputHash,
            onValueChange = { inputHash = it },
            label = { Text("Paste RAW SHA256") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                if (inputHash.isBlank()) {
                    validationState = ValidationState.MissingInput
                } else if (inputHash.trim().equals(calculatedHash, ignoreCase = true)) {
                    validationState = ValidationState.Success
                } else {
                    validationState = ValidationState.Error
                }
            },
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF556B2F))
        ) { Text("立刻校验") }

        // 添加一个弹性间距，把后面的内容推到底部
        Spacer(modifier = Modifier.weight(1f))

        // 添加邮箱联系方式
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "By Niriek_33",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = "cathyrinn97@gmail.com",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = "2025 DEC V1.00",
                fontSize = 10.sp,
                color = Color.LightGray
            )
        }
    } // 这是 Sha256ValidatorScreen 最后的反花括号
}

@Composable
fun InfoBox(label: String, value: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F0F0), RoundedCornerShape(12.dp))
            .clickable {
                // 如果计算出了哈希值（长度通常为64），点击就复制
                if (value.length > 30) {
                    clipboardManager.setText(AnnotatedString(value))
                    Toast.makeText(context, "已复制哈希值", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(16.dp)
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}