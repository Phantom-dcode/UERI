package com.example.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.FirebaseAuthManager
import com.example.auth.OtpChannel
import com.example.auth.OtpPurpose
import com.example.util.EmailValidator
import com.example.util.EmailValidationResult
import com.example.model.ueri.UserAccount
import com.example.model.ueri.UserRole
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanCanvas
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TextSlate400
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate600
import com.example.ui.theme.TextSlate800
import com.example.ui.theme.TextSlate900
import com.example.ui.viewmodel.EmergencyViewModel
import com.example.util.CountryInfo
import com.example.util.PhoneNumberValidator
import com.example.util.PhoneValidationResult

enum class AuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

enum class RegistrationRole {
    CITIZEN,
    RESPONDER
}

enum class RegistrationStep {
    DETAILS,
    OTP_VERIFICATION,
    CREATE_PASSWORD
}

enum class ForgotPasswordStep {
    IDENTIFY_AND_CHANNEL,
    VERIFY_OTP,
    RESET_PASSWORD
}

/**
 * UERI Welcome & Authentication Screen.
 * 
 * Features:
 * - Persistent Cloud & Local Authentication
 * - Multi-channel OTP verification (Mail ID, Phone SMS, Phone WhatsApp) before password creation
 * - Strict login verification ("Kindly register first" for unknown accounts, "Incorrect email ID or password" for wrong credentials)
 * - Forgot Password portal with OTP verification via Mail ID, SMS, or WhatsApp
 * - Strict responder department authorization
 */
@Composable
fun WelcomeAuthScreen(
    viewModel: EmergencyViewModel,
    onAuthSuccess: (UserAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    var authMode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var selectedRole by rememberSaveable { mutableStateOf(RegistrationRole.CITIZEN) }

    // Registration Flow Step State
    var registrationStep by rememberSaveable { mutableStateOf(RegistrationStep.DETAILS) }
    var regOtpChannel by rememberSaveable { mutableStateOf(OtpChannel.EMAIL) }
    var regOtpCode by rememberSaveable { mutableStateOf("") }
    var regConfirmPassword by rememberSaveable { mutableStateOf("") }
    var activeRegTarget by rememberSaveable { mutableStateOf("") }

    // Forgot Password Flow Step State
    var forgotStep by rememberSaveable { mutableStateOf(ForgotPasswordStep.IDENTIFY_AND_CHANNEL) }
    var forgotQuery by rememberSaveable { mutableStateOf("") }
    var forgotOtpTarget by rememberSaveable { mutableStateOf("") }
    var forgotOtpChannel by rememberSaveable { mutableStateOf(OtpChannel.EMAIL) }
    var forgotOtpCode by rememberSaveable { mutableStateOf("") }
    var forgotNewPassword by rememberSaveable { mutableStateOf("") }
    var forgotConfirmPassword by rememberSaveable { mutableStateOf("") }

    // Form fields
    var fullName by rememberSaveable { mutableStateOf("") }
    var selectedCountryIso by rememberSaveable { mutableStateOf("IN") }
    var nationalPhoneNumber by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var responderId by rememberSaveable { mutableStateOf("") }

    var showCountryDialog by rememberSaveable { mutableStateOf(false) }
    var countrySearchQuery by rememberSaveable { mutableStateOf("") }
    var phoneFieldError by rememberSaveable { mutableStateOf<String?>(null) }

    val supportedCountries = remember { PhoneNumberValidator.getSupportedCountries() }
    val currentCountry = remember(selectedCountryIso, supportedCountries) {
        supportedCountries.find { it.isoCode == selectedCountryIso }
            ?: PhoneNumberValidator.getDefaultCountry()
    }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var resetSuccessMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CleanCanvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Brand Header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandBlue.copy(alpha = 0.12f))
                    .border(2.dp, BrandBlue.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "UERI Security Shield",
                    tint = BrandBlue,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "UERI",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = TextSlate900
            )

            Text(
                text = "UNIVERSAL EMERGENCY RESPONSE INFRASTRUCTURE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = BrandBlue,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Mission-critical incident broadcast, dynamic escalation & responder mesh",
                fontSize = 12.sp,
                color = TextSlate500,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Auth Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mode Switcher: SIGN IN vs REGISTER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (authMode == AuthMode.LOGIN || authMode == AuthMode.FORGOT_PASSWORD) CleanWhite else Color.Transparent)
                                .clickable {
                                    authMode = AuthMode.LOGIN
                                    errorMessage = null
                                    resetSuccessMessage = null
                                }
                                .padding(vertical = 10.dp)
                                .testTag("tab_login_mode"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SIGN IN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (authMode == AuthMode.LOGIN || authMode == AuthMode.FORGOT_PASSWORD) BrandBlue else TextSlate500
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (authMode == AuthMode.REGISTER) CleanWhite else Color.Transparent)
                                .clickable {
                                    authMode = AuthMode.REGISTER
                                    registrationStep = RegistrationStep.DETAILS
                                    errorMessage = null
                                    resetSuccessMessage = null
                                }
                                .padding(vertical = 10.dp)
                                .testTag("tab_register_mode"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "REGISTER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (authMode == AuthMode.REGISTER) BrandBlue else TextSlate500
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Error Message Banner
                    AnimatedVisibility(visible = errorMessage != null) {
                        errorMessage?.let { error ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFEF2F2))
                                    .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("auth_error_banner")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = EmergencyRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = error,
                                        color = EmergencyRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (error.contains("register first", ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                authMode = AuthMode.REGISTER
                                                registrationStep = RegistrationStep.DETAILS
                                                errorMessage = null
                                                resetSuccessMessage = null
                                            }
                                            .background(BrandBlue.copy(alpha = 0.1f))
                                            .padding(vertical = 6.dp, horizontal = 8.dp)
                                            .testTag("prompt_register_button")
                                    ) {
                                        Text(
                                            text = "👉 New to UERI? Tap here to Register first",
                                            color = BrandBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // Success Message Banner
                    AnimatedVisibility(visible = resetSuccessMessage != null) {
                        resetSuccessMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF0FDF4))
                                    .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("auth_success_banner"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = SafetyGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    color = SafetyGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // =========================================================
                    // 1. SIGN IN MODE
                    // =========================================================
                    if (authMode == AuthMode.LOGIN) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMessage = null
                            },
                            label = { Text("Email Address or Phone") },
                            placeholder = { Text("name@example.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = TextSlate400)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = TextSlate400)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = TextSlate400
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Forgot Password Link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Forgot password?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue,
                                modifier = Modifier
                                    .clickable {
                                        authMode = AuthMode.FORGOT_PASSWORD
                                        forgotStep = ForgotPasswordStep.IDENTIFY_AND_CHANNEL
                                        forgotQuery = email.trim()
                                        forgotOtpTarget = ""
                                        errorMessage = null
                                        resetSuccessMessage = null
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                                    .testTag("forgot_password_button")
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val trimmedEmail = email.trim()
                                val trimmedPass = password.trim()

                                if (trimmedEmail.isEmpty()) {
                                    errorMessage = "Please enter your email address."
                                    return@Button
                                }
                                if (!EmailValidator.isValid(trimmedEmail)) {
                                    errorMessage = EmailValidator.ERROR_MALFORMED
                                    return@Button
                                }
                                if (trimmedPass.isEmpty()) {
                                    errorMessage = "Please enter your password."
                                    return@Button
                                }
                                if (trimmedPass.length < 6) {
                                    errorMessage = "Password must be at least 6 characters."
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null
                                resetSuccessMessage = null

                                viewModel.login(trimmedEmail, trimmedPass) { result ->
                                    isLoading = false
                                    result.fold(
                                        onSuccess = { user ->
                                            onAuthSuccess(user)
                                        },
                                        onFailure = { err ->
                                            errorMessage = err.message ?: "Incorrect email ID or password"
                                        }
                                    )
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AUTHENTICATING...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("SIGN IN TO UERI", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            }
                        }
                    }

                    // =========================================================
                    // 2. REGISTER MODE (3-Step OTP Verification)
                    // =========================================================
                    else if (authMode == AuthMode.REGISTER) {
                        // Step Indicator
                        RegisterStepIndicator(currentStep = registrationStep)
                        Spacer(modifier = Modifier.height(16.dp))

                        when (registrationStep) {
                            RegistrationStep.DETAILS -> {
                                // Full Name
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = {
                                        fullName = it
                                        errorMessage = null
                                    },
                                    label = { Text("Full Name") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSlate400)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_name_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Role Selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RoleCard(
                                        title = "CITIZEN",
                                        subtitle = "Instant SOS & Alerts",
                                        icon = Icons.Default.DirectionsRun,
                                        isSelected = selectedRole == RegistrationRole.CITIZEN,
                                        onClick = { selectedRole = RegistrationRole.CITIZEN },
                                        modifier = Modifier.weight(1f)
                                    )
                                    RoleCard(
                                        title = "RESPONDER",
                                        subtitle = "Tactical Dispatch",
                                        icon = Icons.Default.Badge,
                                        isSelected = selectedRole == RegistrationRole.RESPONDER,
                                        onClick = { selectedRole = RegistrationRole.RESPONDER },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (selectedRole == RegistrationRole.RESPONDER) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = responderId,
                                        onValueChange = { responderId = it; errorMessage = null },
                                        label = { Text("Responder / Badge ID (Optional)") },
                                        placeholder = { Text("Auto-assigned if blank (UER-RSP-...)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Badge, contentDescription = null, tint = TextSlate400)
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = cleanTextFieldColors(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("auth_responder_id_input")
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Country-aware Phone Number Input
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { showCountryDialog = true },
                                        shape = RoundedCornerShape(14.dp),
                                        color = CleanWhite,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (phoneFieldError != null) EmergencyRed else SlateBorderLight),
                                        modifier = Modifier
                                            .height(56.dp)
                                            .testTag("country_selector_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = currentCountry.flagEmoji, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = currentCountry.callingCode, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSlate900)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSlate500, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    OutlinedTextField(
                                        value = nationalPhoneNumber,
                                        onValueChange = {
                                            nationalPhoneNumber = it
                                            phoneFieldError = null
                                            errorMessage = null
                                        },
                                        label = { Text("Phone Number") },
                                        placeholder = { Text(currentCountry.exampleNational) },
                                        singleLine = true,
                                        isError = phoneFieldError != null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = cleanTextFieldColors(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("auth_mobile_input")
                                    )
                                }

                                if (phoneFieldError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = phoneFieldError ?: "", color = EmergencyRed, fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it; errorMessage = null },
                                    label = { Text("Email Address") },
                                    placeholder = { Text("name@example.com") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Email, contentDescription = null, tint = TextSlate400)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_email_input")
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // OTP Channel Selection Card
                                Text(
                                    text = "SEND VERIFICATION OTP VIA:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextSlate600,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OtpChannelSelector(
                                    selectedChannel = regOtpChannel,
                                    onSelectChannel = { regOtpChannel = it },
                                    emailText = email.ifBlank { "Registered Email" }
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        val trimmedName = fullName.trim()
                                        val trimmedEmail = email.trim()
                                        val trimmedPhone = nationalPhoneNumber.trim()

                                        if (trimmedName.isEmpty()) {
                                            errorMessage = "Please enter your full name."
                                            return@Button
                                        }

                                        val phoneValidation = PhoneNumberValidator.validatePhoneNumber(currentCountry.isoCode, trimmedPhone)
                                        if (phoneValidation is PhoneValidationResult.Failure) {
                                            phoneFieldError = phoneValidation.errorMessage
                                            errorMessage = phoneValidation.errorMessage
                                            return@Button
                                        }
                                        val validPhone = phoneValidation as PhoneValidationResult.Success

                                        val emailValidation = EmailValidator.validate(trimmedEmail)
                                        if (emailValidation is EmailValidationResult.Failure) {
                                            errorMessage = emailValidation.errorMessage
                                            return@Button
                                        }
                                        val normalizedEmail = (emailValidation as EmailValidationResult.Success).normalizedEmail

                                        val target = when (regOtpChannel) {
                                            OtpChannel.EMAIL -> normalizedEmail
                                            OtpChannel.SMS, OtpChannel.WHATSAPP -> validPhone.normalizedPhoneNumber
                                        }
                                        activeRegTarget = target

                                        isLoading = true
                                        errorMessage = null

                                        viewModel.sendRegistrationOtp(target, regOtpChannel, trimmedName) { res ->
                                            isLoading = false
                                            res.fold(
                                                onSuccess = { dispatch ->
                                                    regOtpCode = ""
                                                    registrationStep = RegistrationStep.OTP_VERIFICATION
                                                    resetSuccessMessage = dispatch.deliveryMessage
                                                },
                                                onFailure = { err ->
                                                    errorMessage = err.message ?: "Failed to dispatch OTP."
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("send_reg_otp_button")
                                 ) {
                                     if (isLoading) {
                                         CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                         Spacer(modifier = Modifier.width(8.dp))
                                         Text("DISPATCHING OTP...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                     } else {
                                         Text("VERIFY & SEND OTP", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                     }
                                 }
                             }

                            RegistrationStep.OTP_VERIFICATION -> {
                                val isEmailChannel = (regOtpChannel == OtpChannel.EMAIL)
                                Text(
                                    text = if (isEmailChannel) "VERIFY YOUR EMAIL" else "VERIFY YOUR PHONE NUMBER",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextSlate900,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isEmailChannel) {
                                        "Enter the 6-digit code sent to your email address."
                                    } else {
                                        "Enter the 6-digit code sent to your phone number."
                                    },
                                    fontSize = 12.sp,
                                    color = TextSlate600,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                OutlinedTextField(
                                    value = regOtpCode,
                                    onValueChange = {
                                        if (it.length <= 6) regOtpCode = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "— — — — — —",
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = TextSlate400,
                                            fontSize = 18.sp,
                                            letterSpacing = 4.sp
                                        )
                                    },
                                    label = { Text("Verification Code") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 4.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reg_otp_input")
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (regOtpCode.trim().length != 6) {
                                            errorMessage = "Invalid or expired verification code. Please try again."
                                            return@Button
                                        }

                                        isLoading = true
                                        errorMessage = null

                                        viewModel.verifyRegistrationOtp(activeRegTarget, regOtpCode.trim()) { res ->
                                            isLoading = false
                                            res.fold(
                                                onSuccess = {
                                                    registrationStep = RegistrationStep.CREATE_PASSWORD
                                                    resetSuccessMessage = "Verification successful! Please create your password."
                                                },
                                                onFailure = { err ->
                                                    errorMessage = err.message ?: "Invalid or expired verification code. Please try again."
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen, contentColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("verify_reg_otp_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isEmailChannel) "VERIFYING EMAIL..." else "VERIFYING PHONE NUMBER...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text(if (isEmailChannel) "VERIFY EMAIL" else "VERIFY PHONE NUMBER", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "← Back to Details",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSlate600,
                                        modifier = Modifier
                                            .clickable { registrationStep = RegistrationStep.DETAILS }
                                            .padding(6.dp)
                                    )

                                    Text(
                                        text = "RESEND CODE",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BrandBlue,
                                        modifier = Modifier
                                            .clickable {
                                                if (isLoading) return@clickable
                                                isLoading = true
                                                errorMessage = null
                                                viewModel.sendRegistrationOtp(activeRegTarget, regOtpChannel, fullName) { res ->
                                                    isLoading = false
                                                    res.fold(
                                                        onSuccess = { d ->
                                                            regOtpCode = ""
                                                            resetSuccessMessage = d.deliveryMessage
                                                        },
                                                        onFailure = { err ->
                                                            errorMessage = err.message ?: "Failed to resend code."
                                                        }
                                                    )
                                                }
                                            }
                                            .padding(6.dp)
                                            .testTag("resend_reg_otp_button")
                                    )
                                }
                            }

                            RegistrationStep.CREATE_PASSWORD -> {
                                // Verified Badge
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SafetyGreen.copy(alpha = 0.1f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Contact Verified ($activeRegTarget)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SafetyGreen
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "CREATE YOUR ACCOUNT PASSWORD",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextSlate800
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it; errorMessage = null },
                                    label = { Text("Create Password (min. 6 characters)") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = TextSlate400)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = TextSlate400
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_password_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = regConfirmPassword,
                                    onValueChange = { regConfirmPassword = it; errorMessage = null },
                                    label = { Text("Confirm Password") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = TextSlate400)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                            Icon(
                                                imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = TextSlate400
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_confirm_password_input")
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        val trimmedPass = password.trim()
                                        val trimmedConfirm = regConfirmPassword.trim()

                                        if (trimmedPass.isEmpty()) {
                                            errorMessage = "Please enter your password."
                                            return@Button
                                        }
                                        if (trimmedPass.length < 6) {
                                            errorMessage = "Password must be at least 6 characters."
                                            return@Button
                                        }
                                        if (trimmedPass != trimmedConfirm) {
                                            errorMessage = "Passwords do not match. Please re-enter."
                                            return@Button
                                        }

                                        val phoneValidation = PhoneNumberValidator.validatePhoneNumber(currentCountry.isoCode, nationalPhoneNumber.trim())
                                        if (phoneValidation !is PhoneValidationResult.Success) {
                                            errorMessage = "Phone validation failed."
                                            return@Button
                                        }
                                        val validPhone = phoneValidation

                                        isLoading = true
                                        errorMessage = null

                                        if (selectedRole == RegistrationRole.CITIZEN) {
                                            viewModel.registerCitizen(
                                                name = fullName.trim(),
                                                countryIsoCode = validPhone.countryIsoCode,
                                                countryCallingCode = validPhone.countryCallingCode,
                                                nationalPhoneNumber = validPhone.nationalPhoneNumber,
                                                email = email.trim(),
                                                pass = trimmedPass,
                                                otpVerifiedTarget = activeRegTarget
                                            ) { res ->
                                                isLoading = false
                                                res.fold(
                                                    onSuccess = { user -> onAuthSuccess(user) },
                                                    onFailure = { err -> errorMessage = err.message ?: "Registration failed." }
                                                )
                                            }
                                        } else {
                                            viewModel.registerResponder(
                                                name = fullName.trim(),
                                                countryIsoCode = validPhone.countryIsoCode,
                                                countryCallingCode = validPhone.countryCallingCode,
                                                nationalPhoneNumber = validPhone.nationalPhoneNumber,
                                                email = email.trim(),
                                                pass = trimmedPass,
                                                responderId = responderId.trim(),
                                                otpVerifiedTarget = activeRegTarget
                                            ) { res ->
                                                isLoading = false
                                                res.fold(
                                                    onSuccess = { user -> onAuthSuccess(user) },
                                                    onFailure = { err -> errorMessage = err.message ?: "Registration failed." }
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("complete_registration_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("CREATING ACCOUNT...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text(
                                            text = if (selectedRole == RegistrationRole.CITIZEN) "COMPLETE CITIZEN REGISTRATION" else "COMPLETE RESPONDER REGISTRATION",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================
                    // 3. FORGOT PASSWORD MODE (Multi-Channel OTP Verification)
                    // =========================================================
                    else if (authMode == AuthMode.FORGOT_PASSWORD) {
                        ForgotPasswordStepIndicator(currentStep = forgotStep)
                        Spacer(modifier = Modifier.height(16.dp))

                        when (forgotStep) {
                            ForgotPasswordStep.IDENTIFY_AND_CHANNEL -> {
                                Text(
                                    text = "RESET ACCOUNT PASSWORD",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextSlate900,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enter your registered email or phone number to verify identity via OTP.",
                                    fontSize = 11.sp,
                                    color = TextSlate500,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = forgotQuery,
                                    onValueChange = { forgotQuery = it; forgotOtpTarget = ""; errorMessage = null },
                                    label = { Text("Registered Email or Phone") },
                                    placeholder = { Text("e.g. name@example.com or +91...") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSlate400)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("forgot_query_input")
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "WHERE SHOULD WE SEND YOUR OTP?",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextSlate600,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OtpChannelSelector(
                                    selectedChannel = forgotOtpChannel,
                                    onSelectChannel = { forgotOtpChannel = it },
                                    emailText = if (forgotQuery.contains("@")) forgotQuery else "Registered Mail ID"
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        val trimmedQuery = forgotQuery.trim()
                                        if (trimmedQuery.isEmpty()) {
                                            errorMessage = "Please enter your registered email address or phone number."
                                            return@Button
                                        }

                                        val targetToSend = if (forgotOtpChannel == OtpChannel.EMAIL && trimmedQuery.contains("@")) {
                                            val emailValidation = EmailValidator.validate(trimmedQuery)
                                            if (emailValidation is EmailValidationResult.Failure) {
                                                errorMessage = emailValidation.errorMessage
                                                return@Button
                                            }
                                            (emailValidation as EmailValidationResult.Success).normalizedEmail
                                        } else {
                                            trimmedQuery
                                        }

                                        isLoading = true
                                        errorMessage = null

                                        viewModel.sendForgotPasswordOtp(targetToSend, forgotOtpChannel) { res ->
                                            isLoading = false
                                            res.fold(
                                                onSuccess = { dispatch ->
                                                    forgotOtpCode = ""
                                                    forgotOtpTarget = dispatch.formattedTarget
                                                    forgotStep = ForgotPasswordStep.VERIFY_OTP
                                                    resetSuccessMessage = dispatch.deliveryMessage
                                                },
                                                onFailure = { err ->
                                                    errorMessage = err.message ?: "Failed to send reset code."
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("send_forgot_otp_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("CHECKING REGISTRY & SENDING...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("SEND RESET OTP", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    }
                                }
                            }

                            ForgotPasswordStep.VERIFY_OTP -> {
                                val isForgotEmail = (forgotOtpChannel == OtpChannel.EMAIL)
                                Text(
                                    text = if (isForgotEmail) "VERIFY YOUR EMAIL" else "VERIFY YOUR PHONE NUMBER",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextSlate900,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isForgotEmail) {
                                        "Enter the 6-digit code sent to your email address."
                                    } else {
                                        "Enter the 6-digit code sent to your phone number."
                                    },
                                    fontSize = 12.sp,
                                    color = TextSlate600,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                OutlinedTextField(
                                    value = forgotOtpCode,
                                    onValueChange = {
                                        if (it.length <= 6) forgotOtpCode = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "— — — — — —",
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = TextSlate400,
                                            fontSize = 18.sp,
                                            letterSpacing = 4.sp
                                        )
                                    },
                                    label = { Text("Verification Code") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 4.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("forgot_otp_input")
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (forgotOtpCode.trim().length != 6) {
                                            errorMessage = "Invalid or expired verification code. Please try again."
                                            return@Button
                                        }

                                        isLoading = true
                                        errorMessage = null

                                        val targetToVerify = forgotOtpTarget.ifBlank { forgotQuery.trim() }
                                        viewModel.verifyForgotPasswordOtp(targetToVerify, forgotOtpCode.trim()) { res ->
                                            isLoading = false
                                            res.fold(
                                                onSuccess = {
                                                    forgotStep = ForgotPasswordStep.RESET_PASSWORD
                                                    resetSuccessMessage = "Code verified! Please enter your new password."
                                                },
                                                onFailure = { err ->
                                                    errorMessage = err.message ?: "Invalid or expired verification code. Please try again."
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen, contentColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("verify_forgot_otp_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isForgotEmail) "VERIFYING EMAIL..." else "VERIFYING CODE...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text(if (isForgotEmail) "VERIFY EMAIL" else "VERIFY RESET CODE", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "← Back",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSlate600,
                                        modifier = Modifier
                                            .clickable { forgotStep = ForgotPasswordStep.IDENTIFY_AND_CHANNEL }
                                            .padding(6.dp)
                                            .testTag("forgot_otp_back_button")
                                    )

                                    Text(
                                        text = "RESEND CODE",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BrandBlue,
                                        modifier = Modifier
                                            .clickable {
                                                if (isLoading) return@clickable
                                                isLoading = true
                                                errorMessage = null
                                                viewModel.sendForgotPasswordOtp(forgotOtpTarget.ifBlank { forgotQuery.trim() }, forgotOtpChannel) { res ->
                                                    isLoading = false
                                                    res.fold(
                                                        onSuccess = { d ->
                                                            forgotOtpCode = ""
                                                            forgotOtpTarget = d.formattedTarget
                                                            resetSuccessMessage = d.deliveryMessage
                                                        },
                                                        onFailure = { err ->
                                                            errorMessage = err.message ?: "Failed to resend code."
                                                        }
                                                    )
                                                }
                                            }
                                            .padding(6.dp)
                                            .testTag("forgot_otp_resend_button")
                                    )
                                }
                            }

                            ForgotPasswordStep.RESET_PASSWORD -> {
                                Text(
                                    text = "SET YOUR NEW PASSWORD",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextSlate900,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = forgotNewPassword,
                                    onValueChange = { forgotNewPassword = it; errorMessage = null },
                                    label = { Text("New Password (min. 6 characters)") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = TextSlate400)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = TextSlate400
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("forgot_new_pass_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = forgotConfirmPassword,
                                    onValueChange = { forgotConfirmPassword = it; errorMessage = null },
                                    label = { Text("Confirm New Password") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = TextSlate400)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                            Icon(
                                                imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = TextSlate400
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = cleanTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("forgot_confirm_pass_input")
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        val trimmedPass = forgotNewPassword.trim()
                                        val trimmedConfirm = forgotConfirmPassword.trim()

                                        if (trimmedPass.isEmpty()) {
                                            errorMessage = "Please enter your new password."
                                            return@Button
                                        }
                                        if (trimmedPass.length < 6) {
                                            errorMessage = "Password must be at least 6 characters."
                                            return@Button
                                        }
                                        if (trimmedPass != trimmedConfirm) {
                                            errorMessage = "Passwords do not match."
                                            return@Button
                                        }

                                        isLoading = true
                                        errorMessage = null

                                        viewModel.resetPasswordWithOtp(
                                            forgotQuery.trim(),
                                            forgotOtpCode.trim(),
                                            trimmedPass,
                                            onResult = { res ->
                                            isLoading = false
                                            res.fold(
                                                onSuccess = { successMsg ->
                                                    authMode = AuthMode.LOGIN
                                                    email = if (forgotOtpTarget.contains("@")) forgotOtpTarget else forgotQuery.trim()
                                                    password = ""
                                                    resetSuccessMessage = successMsg
                                                },
                                                onFailure = { err ->
                                                    errorMessage = err.message ?: "Failed to reset password."
                                                }
                                            )
                                            },
                                            otpVerifiedTarget = forgotOtpTarget
                                        )
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("update_password_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("UPDATING PASSWORD...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("UPDATE PASSWORD & SIGN IN", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                authMode = AuthMode.LOGIN
                                errorMessage = null
                                resetSuccessMessage = null
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("back_to_login_button")
                        ) {
                            Text("Back to Sign In", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSlate600)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "By continuing, you agree to UERI Emergency Operating Terms & Dispatch Protocols",
                        fontSize = 10.sp,
                        color = TextSlate400,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Searchable Country Calling Code Picker Dialog
        if (showCountryDialog) {
            val filteredCountries = remember(countrySearchQuery, supportedCountries) {
                if (countrySearchQuery.isBlank()) {
                    supportedCountries
                } else {
                    val q = countrySearchQuery.trim().lowercase()
                    supportedCountries.filter {
                        it.countryName.lowercase().contains(q) ||
                        it.isoCode.lowercase().contains(q) ||
                        it.callingCode.contains(q)
                    }
                }
            }

            Dialog(
                onDismissRequest = {
                    showCountryDialog = false
                    countrySearchQuery = ""
                }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CleanWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Country",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextSlate900
                            )
                            Text(
                                text = "${filteredCountries.size} available",
                                fontSize = 11.sp,
                                color = TextSlate500
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = countrySearchQuery,
                            onValueChange = { countrySearchQuery = it },
                            placeholder = { Text("Search country or dial code...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextSlate400)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = cleanTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("country_search_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredCountries) { country ->
                                val isSelected = country.isoCode == selectedCountryIso
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFFEFF6FF) else Color.Transparent)
                                        .clickable {
                                            selectedCountryIso = country.isoCode
                                            showCountryDialog = false
                                            countrySearchQuery = ""
                                            phoneFieldError = null
                                            errorMessage = null
                                        }
                                        .padding(vertical = 10.dp, horizontal = 10.dp)
                                        .testTag("country_item_${country.isoCode}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = country.flagEmoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = country.countryName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSlate900)
                                        Text(text = country.isoCode, fontSize = 10.sp, color = TextSlate500)
                                    }
                                    Text(text = country.callingCode, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

@Composable
private fun OtpChannelSelector(
    selectedChannel: OtpChannel,
    onSelectChannel: (OtpChannel) -> Unit,
    emailText: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OtpChannelItem(
            channel = OtpChannel.EMAIL,
            label = "Mail ID (Email)",
            detail = emailText,
            icon = Icons.Default.Email,
            isSelected = selectedChannel == OtpChannel.EMAIL,
            onClick = { onSelectChannel(OtpChannel.EMAIL) }
        )

        OtpChannelItem(
            channel = OtpChannel.SMS,
            label = "Phone Number SMS Message (Not configured)",
            detail = "SMS delivery is unavailable in this build",
            icon = Icons.Default.Sms,
            isSelected = selectedChannel == OtpChannel.SMS,
            onClick = { onSelectChannel(OtpChannel.SMS) }
        )

        OtpChannelItem(
            channel = OtpChannel.WHATSAPP,
            label = "Phone Number WhatsApp (Not configured)",
            detail = "WhatsApp delivery is unavailable in this build",
            icon = Icons.Default.Phone,
            isSelected = selectedChannel == OtpChannel.WHATSAPP,
            onClick = { onSelectChannel(OtpChannel.WHATSAPP) }
        )
    }
}

@Composable
private fun OtpChannelItem(
    channel: OtpChannel,
    label: String,
    detail: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isAvailable = channel == OtpChannel.EMAIL
    Surface(
        onClick = onClick,
        enabled = channel == OtpChannel.EMAIL,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) BrandBlue else SlateBorderLight
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("otp_channel_${channel.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) BrandBlue else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else TextSlate600,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isSelected -> BrandBlue
                        !isAvailable -> TextSlate400
                        else -> TextSlate900
                    }
                )
                Text(
                    text = detail,
                    fontSize = 10.sp,
                    color = if (isAvailable) TextSlate500 else TextSlate400
                )
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) BrandBlue else TextSlate400, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(BrandBlue)
                    )
                }
            }
        }
    }
}

@Composable
private fun RegisterStepIndicator(currentStep: RegistrationStep) {
    val steps = listOf("Details", "Verify OTP", "Password")
    val currentIndex = when (currentStep) {
        RegistrationStep.DETAILS -> 0
        RegistrationStep.OTP_VERIFICATION -> 1
        RegistrationStep.CREATE_PASSWORD -> 2
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { idx, label ->
            val isDone = idx < currentIndex
            val isCurrent = idx == currentIndex

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> SafetyGreen
                                isCurrent -> BrandBlue
                                else -> Color(0xFFE2E8F0)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            text = "${idx + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) Color.White else TextSlate600
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) BrandBlue else TextSlate500
                )
            }
        }
    }
}

@Composable
private fun ForgotPasswordStepIndicator(currentStep: ForgotPasswordStep) {
    val steps = listOf("Channel", "Verify OTP", "New Pass")
    val currentIndex = when (currentStep) {
        ForgotPasswordStep.IDENTIFY_AND_CHANNEL -> 0
        ForgotPasswordStep.VERIFY_OTP -> 1
        ForgotPasswordStep.RESET_PASSWORD -> 2
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { idx, label ->
            val isDone = idx < currentIndex
            val isCurrent = idx == currentIndex

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> SafetyGreen
                                isCurrent -> BrandBlue
                                else -> Color(0xFFE2E8F0)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            text = "${idx + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) Color.White else TextSlate600
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) BrandBlue else TextSlate500
                )
            }
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("role_card_${title.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) BrandBlue else SlateBorderLight
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) BrandBlue else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else TextSlate500,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (isSelected) BrandBlue else TextSlate800
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = TextSlate500,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
private fun cleanTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandBlue,
    unfocusedBorderColor = SlateBorderLight,
    focusedLabelColor = BrandBlue,
    unfocusedLabelColor = TextSlate500,
    cursorColor = BrandBlue
)

@Composable
fun ResponderVerificationPendingScreen(
    user: UserAccount,
    viewModel: EmergencyViewModel,
    onApproved: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isChecking by remember { mutableStateOf(false) }
    var isApproving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CleanCanvas)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CleanWhite),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlertAmber.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AlertAmber.copy(alpha = 0.15f))
                        .border(2.dp, AlertAmber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = "Pending Badge",
                        tint = AlertAmber,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AlertAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AlertAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DISPATCH VERIFICATION PENDING",
                        color = Color(0xFFB45309),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Awaiting Department Authorization",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSlate900,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Responder Account: ${user.displayName}\nID: ${user.responderId.ifEmpty { "Pending Assignment" }}\nEmail: ${user.email}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSlate600,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFFBEB))
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "In compliance with emergency protocol, unverified personnel are restricted from viewing live victim GPS coordinates, incident audio/video feeds, and active responder telemetry until credential vetting is completed.",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        color = BrandBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        isChecking = true
                        statusMessage = null
                        viewModel.checkVerificationStatus { res ->
                            isChecking = false
                            val isVerified = res.getOrDefault(false)
                            if (isVerified) {
                                onApproved()
                            } else {
                                statusMessage = "Credentials still under review in Firestore."
                            }
                        }
                    },
                    enabled = !isChecking && !isApproving,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("check_verification_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CHECKING FIRESTORE...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CHECK VERIFICATION STATUS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        isApproving = true
                        viewModel.approvePendingResponder { res ->
                            isApproving = false
                            if (res.isSuccess) {
                                onApproved()
                            }
                        }
                    },
                    enabled = !isChecking && !isApproving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("simulate_verify_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("APPROVE DISPATCH CREDENTIALS (EVALUATION)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafetyGreen)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sign Out & Return to Login",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed,
                    modifier = Modifier
                        .clickable(onClick = onLogout)
                        .padding(8.dp)
                        .testTag("pending_logout_button")
                )
            }
        }
    }
}
