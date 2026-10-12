package com.pronoscore360.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pronoscore360.app.ui.components.ErrorBox
import com.pronoscore360.app.ui.theme.Green
import com.pronoscore360.app.ui.theme.Navy
import com.pronoscore360.app.vm.SessionViewModel

@Composable fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PronoScore", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("360", color = Green, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp)); CircularProgressIndicator(color = Green)
        }
    }
}

@Composable fun WelcomeScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Navy).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("PronoScore360", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Análisis deportivo basado en datos", color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text("Probabilidades estimadas con modelos estadísticos para fútbol y NBA. Sin apuestas.", color = Color(0xFFB8C7DD), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRegister, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF04230F))) { Text("Crear cuenta") }
        OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Iniciar sesión", color = Color.White) }
        Text("Plataforma informativa. No permite apuestas ni depósitos. Las probabilidades son estimaciones y no garantizan resultados.", color = Color(0xFF8FA3C0), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
    }
}

@Composable fun LoginScreen(vm: SessionViewModel, onForgot: () -> Unit, onRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState(); val err by vm.authError.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text("Iniciar sesión", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        err?.let { ErrorBox(it) }
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Button(onClick = { vm.login(email, pass) }, enabled = !busy && email.isNotBlank() && pass.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(if (busy) "Ingresando…" else "Iniciar sesión") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = onForgot) { Text("¿Olvidaste tu contraseña?") }; TextButton(onClick = onRegister) { Text("Crear cuenta") } }
        if (com.pronoscore360.app.BuildConfig.DEMO_TOOLS) {
            Text("Cuentas de demostración (sólo build debug)", fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { email = "free@pronoscore-demo.example.com"; pass = "Demo#Free2026" }) { Text("Gratis") }
                OutlinedButton(onClick = { email = "pro@pronoscore-demo.example.com"; pass = "Demo#Pro2026" }) { Text("Pro") }
                OutlinedButton(onClick = { email = "admin@pronoscore-demo.example.com"; pass = "Demo#Admin2026" }) { Text("Admin") }
            }
        }
    }
}

@Composable fun RegisterScreen(vm: SessionViewModel, onLogin: () -> Unit, onTerms: () -> Unit) {
    var email by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsState(); val err by vm.authError.collectAsState()
    val strong = pass.length >= 10 && pass.any { it.isUpperCase() } && pass.any { it.isLowerCase() } && pass.any { it.isDigit() }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text("Crear cuenta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        err?.let { ErrorBox(it) }
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, supportingText = { Text("Mínimo 10 caracteres con mayúsculas, minúsculas y números") }, isError = pass.isNotEmpty() && !strong,
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(terms, { terms = it }); TextButton(onClick = onTerms) { Text("Acepto términos y política de privacidad") } }
        Button(onClick = { vm.register(email, pass, name, java.util.Locale.getDefault().country.ifBlank { "CL" }, java.util.TimeZone.getDefault().id) },
            enabled = !busy && terms && strong && email.contains("@"), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Creando…" else "Crear cuenta") }
        TextButton(onClick = onLogin) { Text("¿Ya tienes cuenta? Inicia sesión") }
    }
}

@Composable fun ForgotScreen(vm: SessionViewModel, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }; var sent by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Recuperar contraseña", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (sent) Text("Si el correo existe, te enviamos instrucciones.", modifier = Modifier.padding(vertical = 12.dp)) else {
            OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Button(onClick = { vm.forgot(email) { sent = true } }, enabled = email.contains("@"), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Enviar instrucciones") }
        }
        TextButton(onClick = onBack) { Text("Volver") }
    }
}
