package com.aroha.hams.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aroha.hams.data.FirebaseHospitalRepository
import com.aroha.hams.dataclass.Appointment
import com.aroha.hams.dataclass.Role
import com.aroha.hams.dataclass.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val appointmentTimes = listOf("09:00", "10:00", "11:00", "14:00", "15:00")

@Composable
fun HamsApp() {
    val repository = remember { FirebaseHospitalRepository() }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<User?>(null) }
    var doctors by remember { mutableStateOf<List<User>>(emptyList()) }
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var selectedDoctor by remember { mutableStateOf<User?>(null) }
    var showAppointments by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loadingDashboard by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var authMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            user = repository.currentUser()
        } catch (exception: Exception) {
            error = exception.message ?: "Unable to connect to Firebase."
        }
    }

    LaunchedEffect(user?.id, refreshKey) {
        val signedInUser = user ?: return@LaunchedEffect
        loadingDashboard = true
        try {
            if (signedInUser.role == Role.PATIENT) {
                doctors = repository.getDoctors()
            }
            appointments = repository.getAppointments(signedInUser)
            error = null
        } catch (exception: Exception) {
            error = exception.message ?: "Unable to load your HAMS data."
        } finally {
            loadingDashboard = false
        }
    }

    Scaffold { contentPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            val signedInUser = user
            if (signedInUser == null) {
                AuthScreen(
                    busy = busy,
                    error = error,
                    message = authMessage,
                    onAuthenticate = { firstName, lastName, email, password, role, specialty ->
                        scope.launch {
                            busy = true
                            error = null
                            authMessage = null
                            try {
                                user = if (firstName == null) {
                                    repository.signIn(email, password)
                                } else {
                                    val registeredUser = repository.register(
                                        firstName = firstName,
                                        lastName = lastName.orEmpty(),
                                        email = email,
                                        password = password,
                                        role = role,
                                        specialty = specialty
                                    )
                                    authMessage =
                                        "Account created. Check ${registeredUser.email} for a verification link, then sign in."
                                    null
                                }
                            } catch (exception: Exception) {
                                error = exception.message ?: "Authentication failed."
                            } finally {
                                busy = false
                            }
                        }
                    }
                )
            } else {
                Dashboard(
                    user = signedInUser,
                    doctors = doctors,
                    appointments = appointments,
                    showAppointments = showAppointments,
                    loading = loadingDashboard,
                    error = error,
                    onShowAppointments = { showAppointments = true },
                    onShowDoctors = { showAppointments = false },
                    onBookDoctor = { selectedDoctor = it },
                    onSignOut = {
                        scope.launch {
                            busy = true
                            try {
                                repository.signOut()
                                user = null
                                doctors = emptyList()
                                appointments = emptyList()
                                showAppointments = false
                                error = null
                            } catch (exception: Exception) {
                                error = exception.message ?: "Unable to sign out."
                            } finally {
                                busy = false
                            }
                        }
                    }
                )
            }
        }
    }

    selectedDoctor?.let { doctor ->
        BookingDialog(
            doctor = doctor,
            busy = busy,
            error = error,
            onDismiss = { if (!busy) selectedDoctor = null },
            onBook = { date, time ->
                val patient = user
                if (patient != null) {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            repository.bookAppointment(patient, doctor, date, time)
                            selectedDoctor = null
                            showAppointments = true
                            refreshKey++
                        } catch (exception: Exception) {
                            error = exception.message ?: "Unable to book this appointment."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun AuthScreen(
    busy: Boolean,
    error: String?,
    message: String?,
    onAuthenticate: (
        firstName: String?,
        lastName: String?,
        email: String,
        password: String,
        role: Role,
        specialty: String
    ) -> Unit
) {
    var registering by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.PATIENT) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("HAMS", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Hospital appointments, made simpler.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !registering,
                onClick = { registering = false; validationError = null },
                label = { Text("Sign in") }
            )
            FilterChip(
                selected = registering,
                onClick = { registering = true; validationError = null },
                label = { Text("Create account") }
            )
        }
        if (registering) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text("I am registering as a")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Role.values().forEach { option ->
                    FilterChip(
                        selected = role == option,
                        onClick = { role = option },
                        label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
            if (role == Role.DOCTOR) {
                OutlinedTextField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = { Text("Medical specialty") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (registering) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        Spacer(Modifier.height(16.dp))
        validationError?.let { ErrorText(it) }
        error?.let { ErrorText(it) }
        message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Button(
            onClick = {
                val invalid = when {
                    registering && (firstName.isBlank() || lastName.isBlank()) ->
                        "Enter your first and last name."
                    registering && role == Role.DOCTOR && specialty.isBlank() ->
                        "Enter your medical specialty."
                    registering && password != confirmPassword ->
                        "Passwords do not match."
                    email.isBlank() || !email.contains("@") ->
                        "Enter a valid email address."
                    password.length < 6 -> "Password must be at least 6 characters."
                    else -> null
                }
                validationError = invalid
                if (invalid == null) {
                    onAuthenticate(
                        if (registering) firstName else null,
                        if (registering) lastName else null,
                        email,
                        password,
                        role,
                        specialty
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (registering) "Create account" else "Sign in")
            }
        }
    }
}

@Composable
private fun Dashboard(
    user: User,
    doctors: List<User>,
    appointments: List<Appointment>,
    showAppointments: Boolean,
    loading: Boolean,
    error: String?,
    onShowAppointments: () -> Unit,
    onShowDoctors: () -> Unit,
    onBookDoctor: (User) -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("HAMS", style = MaterialTheme.typography.headlineSmall)
                Text("Hello, ${user.fullName}")
            }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
        if (user.role == Role.PATIENT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showAppointments,
                    onClick = onShowDoctors,
                    label = { Text("Find doctors") }
                )
                FilterChip(
                    selected = showAppointments,
                    onClick = onShowAppointments,
                    label = { Text("My appointments") }
                )
            }
        } else {
            Text("Your appointments", style = MaterialTheme.typography.titleLarge)
        }
        error?.let { ErrorText(it) }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (showAppointments || user.role == Role.DOCTOR) {
            if (appointments.isEmpty()) {
                EmptyMessage(
                    if (user.role == Role.DOCTOR) {
                        "No appointments yet. New patient bookings will appear here."
                    } else {
                        "You don't have any appointments yet."
                    }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                ) {
                    items(appointments, key = Appointment::id) { appointment ->
                        AppointmentCard(appointment, user.role)
                    }
                }
            }
        } else if (doctors.isEmpty()) {
            EmptyMessage("No doctors are registered yet. Please check back later.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                items(doctors, key = User::id) { doctor ->
                    DoctorCard(doctor, onBookDoctor)
                }
            }
        }
    }
}

@Composable
private fun DoctorCard(doctor: User, onBookDoctor: (User) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Dr. ${doctor.fullName}", style = MaterialTheme.typography.titleMedium)
            Text(doctor.specialty.ifBlank { "General practice" })
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onBookDoctor(doctor) }) { Text("Book appointment") }
        }
    }
}

@Composable
private fun AppointmentCard(appointment: Appointment, viewerRole: Role) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (viewerRole == Role.DOCTOR) {
                    "Patient: ${appointment.patientName}"
                } else {
                    "Dr. ${appointment.doctorName}"
                },
                style = MaterialTheme.typography.titleMedium
            )
            if (appointment.specialty.isNotBlank()) Text(appointment.specialty)
            Text("${appointment.date} at ${appointment.time}")
            Text("Status: ${appointment.status.lowercase().replaceFirstChar(Char::uppercase)}")
        }
    }
}

@Composable
private fun BookingDialog(
    doctor: User,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onBook: (date: String, time: String) -> Unit
) {
    val dates = remember { upcomingDates() }
    var selectedDate by remember { mutableStateOf(dates.first().first) }
    var selectedTime by remember { mutableStateOf(appointmentTimes.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book Dr. ${doctor.fullName}") },
        text = {
            Column {
                Text("Choose a date")
                dates.chunked(3).forEach { dateRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dateRow.forEach { (date, label) ->
                            FilterChip(
                                selected = selectedDate == date,
                                onClick = { selectedDate = date },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Choose a time")
                appointmentTimes.chunked(3).forEach { rowTimes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTimes.forEach { time ->
                            FilterChip(
                                selected = selectedTime == time,
                                onClick = { selectedTime = time },
                                label = { Text(time) }
                            )
                        }
                    }
                }
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            Button(onClick = { onBook(selectedDate, selectedTime) }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirm booking")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyMessage(message: String) {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private fun upcomingDates(): List<Pair<String, String>> {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val labelFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    return List(7) {
        val date = dateFormat.format(calendar.time)
        val label = labelFormat.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        date to label
    }
}
