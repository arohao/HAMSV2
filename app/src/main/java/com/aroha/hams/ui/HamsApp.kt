package com.aroha.hams.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aroha.hams.R
import com.aroha.hams.data.FirebaseHospitalRepository
import com.aroha.hams.dataclass.Appointment
import com.aroha.hams.dataclass.AvailabilitySlot
import com.aroha.hams.dataclass.Role
import com.aroha.hams.dataclass.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val appointmentTimes = (9 * 60..17 * 60 step 30).map { minutes ->
    "%02d:%02d".format(Locale.US, minutes / 60, minutes % 60)
}

@Composable
fun HamsApp() {
    val repository = remember { FirebaseHospitalRepository() }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<User?>(null) }
    var doctors by remember { mutableStateOf<List<User>>(emptyList()) }
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var selectedDoctor by remember { mutableStateOf<User?>(null) }
    var availableSlots by remember { mutableStateOf<List<AvailabilitySlot>>(emptyList()) }
    var doctorSchedule by remember { mutableStateOf<List<AvailabilitySlot>>(emptyList()) }
    var loadingSlots by remember { mutableStateOf(false) }
    var showAvailabilityDialog by remember { mutableStateOf(false) }
    var showAppointments by remember { mutableStateOf(false) }
    var appointmentsCalendarView by remember { mutableStateOf(false) }
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
            } else {
                doctorSchedule = repository.getDoctorSchedule(signedInUser.id)
            }
            appointments = repository.getAppointments(signedInUser)
            error = null
        } catch (exception: Exception) {
            error = exception.message ?: "Unable to load your HAMS data."
        } finally {
            loadingDashboard = false
        }
    }

    LaunchedEffect(selectedDoctor?.id, refreshKey) {
        val doctor = selectedDoctor ?: return@LaunchedEffect
        loadingSlots = true
        try {
            availableSlots = repository.getAvailableSlots(doctor.id)
                .filter { it.date >= todayIsoDate() }
            error = null
        } catch (exception: Exception) {
            error = exception.message ?: "Unable to load this doctor's schedule."
        } finally {
            loadingSlots = false
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
                    doctorSchedule = doctorSchedule,
                    showAppointments = showAppointments,
                    appointmentsCalendarView = appointmentsCalendarView,
                    loading = loadingDashboard,
                    busy = busy,
                    error = error,
                    onShowAppointments = { showAppointments = true },
                    onToggleAppointmentsView = { appointmentsCalendarView = it },
                    onShowSchedule = { showAppointments = false },
                    onBookDoctor = { selectedDoctor = it },
                    onAddAvailability = { showAvailabilityDialog = true },
                    onRemoveAvailability = { slot ->
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                repository.removeAvailability(signedInUser, slot)
                                refreshKey++
                            } catch (exception: Exception) {
                                error = exception.message ?: "Unable to remove this time."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onSignOut = {
                        scope.launch {
                            busy = true
                            try {
                                repository.signOut()
                                user = null
                                doctors = emptyList()
                                appointments = emptyList()
                                showAppointments = false
                                appointmentsCalendarView = false
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
            slots = availableSlots,
            loadingSlots = loadingSlots,
            busy = busy,
            error = error,
            onDismiss = { if (!busy) selectedDoctor = null },
            onBook = { slot ->
                val patient = user
                if (patient != null) {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            repository.bookAppointment(patient, doctor, slot)
                            selectedDoctor = null
                            showAppointments = true
                            refreshKey++
                        } catch (exception: Exception) {
                            error = exception.message ?: "Unable to book this appointment."
                            refreshKey++
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        )
    }

    if (showAvailabilityDialog) {
        val doctor = user
        if (doctor != null && doctor.role == Role.DOCTOR) {
            AvailabilityDialog(
                busy = busy,
                error = error,
                onDismiss = { if (!busy) showAvailabilityDialog = false },
                onAdd = { date, time ->
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            repository.addAvailability(doctor, date, time)
                            showAvailabilityDialog = false
                            refreshKey++
                        } catch (exception: Exception) {
                            error = exception.message ?: "Unable to add this time."
                        } finally {
                            busy = false
                        }
                    }
                }
            )
        }
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
        Image(
            painter = painterResource(R.drawable.hams_logo),
            contentDescription = "HAMS — Hospital Appointment Management System",
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(8.dp))
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
    doctorSchedule: List<AvailabilitySlot>,
    showAppointments: Boolean,
    appointmentsCalendarView: Boolean,
    loading: Boolean,
    busy: Boolean,
    error: String?,
    onShowAppointments: () -> Unit,
    onToggleAppointmentsView: (Boolean) -> Unit,
    onShowSchedule: () -> Unit,
    onBookDoctor: (User) -> Unit,
    onAddAvailability: () -> Unit,
    onRemoveAvailability: (AvailabilitySlot) -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(R.mipmap.hams_launcher),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit
            )
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
                    onClick = onShowSchedule,
                    label = { Text("Find doctors") }
                )
                FilterChip(
                    selected = showAppointments,
                    onClick = onShowAppointments,
                    label = { Text("My appointments") }
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showAppointments,
                    onClick = onShowSchedule,
                    label = { Text("My schedule") }
                )
                FilterChip(
                    selected = showAppointments,
                    onClick = onShowAppointments,
                    label = { Text("Appointments") }
                )
            }
        }
        if (showAppointments) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !appointmentsCalendarView,
                    onClick = { onToggleAppointmentsView(false) },
                    label = { Text("List") }
                )
                FilterChip(
                    selected = appointmentsCalendarView,
                    onClick = { onToggleAppointmentsView(true) },
                    label = { Text("Calendar") }
                )
            }
        }
        error?.let { ErrorText(it) }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (showAppointments) {
            if (appointmentsCalendarView) {
                AppointmentCalendar(appointments, user.role)
            } else if (appointments.isEmpty()) {
                EmptyMessage(
                    "No appointments yet."
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
        } else if (user.role == Role.DOCTOR) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Open appointment times", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onAddAvailability, enabled = !busy) {
                        Text("Add times")
                    }
                }
                if (doctorSchedule.isEmpty()) {
                    EmptyMessage("Add open times so patients can book appointments with you.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                    ) {
                        items(doctorSchedule, key = AvailabilitySlot::id) { slot ->
                            AvailabilityCard(
                                slot = slot,
                                enabled = !busy,
                                onRemove = { onRemoveAvailability(slot) }
                            )
                        }
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
private fun AvailabilityCard(
    slot: AvailabilitySlot,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "${formatDateLabel(slot.date)} at ${slot.time}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(if (slot.status == "OPEN") "Open for booking" else "Booked")
            }
            if (slot.status == "OPEN") {
                TextButton(onClick = onRemove, enabled = enabled) { Text("Remove") }
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
private fun AppointmentCalendar(appointments: List<Appointment>, viewerRole: Role) {
    val today = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var selectedDate by remember { mutableStateOf(todayIsoDate()) }
    val appointmentsByDate = appointments.groupBy(Appointment::date)
    val selectedAppointments = appointmentsByDate[selectedDate].orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                val moved = shiftedMonth(displayedYear, displayedMonth, -1)
                displayedYear = moved.first
                displayedMonth = moved.second
                selectedDate = monthDate(moved.first, moved.second, 1)
            }) {
                Text("‹ Previous")
            }
            Text(
                monthLabel(displayedYear, displayedMonth),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = {
                val moved = shiftedMonth(displayedYear, displayedMonth, 1)
                displayedYear = moved.first
                displayedMonth = moved.second
                selectedDate = monthDate(moved.first, moved.second, 1)
            }) {
                Text("Next ›")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(day, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        val cells = monthCalendarCells(displayedYear, displayedMonth)
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val date = day?.let { monthDate(displayedYear, displayedMonth, it) }
                    val dayAppointments = date?.let { appointmentsByDate[it].orEmpty() }.orEmpty()
                    val selected = date == selectedDate
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primary
                                    dayAppointments.isNotEmpty() ->
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .then(
                                if (date != null) {
                                    Modifier.clickable { selectedDate = date }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    day.toString(),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (dayAppointments.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            dayAppointments.size.toString(),
                                            color = MaterialTheme.colorScheme.surface,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            formatDateLabel(selectedDate),
            style = MaterialTheme.typography.titleSmall
        )
        if (selectedAppointments.isEmpty()) {
            EmptyMessage("No appointments on this day.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(selectedAppointments, key = Appointment::id) { appointment ->
                    AppointmentCard(appointment, viewerRole)
                }
            }
        }
    }
}

private fun monthCalendarCells(year: Int, month: Int): List<Int?> {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val offset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val cells = mutableListOf<Int?>()
    repeat(offset) { cells.add(null) }
    repeat(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) { cells.add(it + 1) }
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private fun shiftedMonth(year: Int, month: Int, amount: Int): Pair<Int, Int> {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        add(Calendar.MONTH, amount)
    }
    return calendar.get(Calendar.YEAR) to calendar.get(Calendar.MONTH)
}

private fun monthDate(year: Int, month: Int, day: Int): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
        }.time
    )

private fun monthLabel(year: Int, month: Int): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time
    )

@Composable
private fun BookingDialog(
    doctor: User,
    slots: List<AvailabilitySlot>,
    loadingSlots: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onBook: (AvailabilitySlot) -> Unit
) {
    var selectedSlot by remember(doctor.id) { mutableStateOf<AvailabilitySlot?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book Dr. ${doctor.fullName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Choose an available time")
                when {
                    loadingSlots -> CircularProgressIndicator()
                    slots.isEmpty() -> Text("This doctor hasn't published any open times yet.")
                    else -> TimeDropdown(
                        label = "Available appointment",
                        selectedValue = selectedSlot?.id,
                        options = slots.map { slot ->
                            slot.id to "${formatDateLabel(slot.date)} · ${formatTimeLabel(slot.time)}"
                        },
                        onSelect = { selectedId ->
                            selectedSlot = slots.firstOrNull { it.id == selectedId }
                        }
                    )
                }
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedSlot?.let(onBook) },
                enabled = !busy && !loadingSlots && selectedSlot != null
            ) {
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
private fun AvailabilityDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (date: String, time: String) -> Unit
) {
    val dates = remember { upcomingDates() }
    var selectedDate by remember { mutableStateOf(dates.first().first) }
    var selectedTime by remember { mutableStateOf(appointmentTimes.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an open appointment time") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    formatDateLabel(selectedDate),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                CalendarGrid(
                    dates = dates.map { it.first },
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it }
                )
                Spacer(Modifier.height(16.dp))
                TimeDropdown(
                    label = "Available start time",
                    selectedValue = selectedTime,
                    options = appointmentTimes.map { it to formatTimeLabel(it) },
                    onSelect = { selectedTime = it }
                )
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(selectedDate, selectedTime) }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add this time")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

@Composable
private fun CalendarGrid(
    dates: List<String>,
    selectedDate: String,
    onSelectDate: (String) -> Unit
) {
    val daysOfWeek = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth()) {
        daysOfWeek.forEach { day ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(day, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    val dateCells = calendarCells(dates)
    dateCells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                val selected = date == selectedDate
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (date != null) {
                                Modifier.clickable { onSelectDate(date) }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (date != null) {
                        val day = date.substringAfterLast('-').toIntOrNull()?.toString().orEmpty()
                        Text(
                            day,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun calendarCells(dates: List<String>): List<String?> {
    if (dates.isEmpty()) return emptyList()
    val dayOfWeek = Calendar.getInstance().apply {
        time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dates.first()) ?: return emptyList()
    }.get(Calendar.DAY_OF_WEEK)
    val mondayFirstOffset = (dayOfWeek + 5) % 7
    val cells = mutableListOf<String?>()
    repeat(mondayFirstOffset) { cells.add(null) }
    cells.addAll(dates)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

@Composable
private fun TimeDropdown(
    label: String,
    selectedValue: String?,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Text(
                        selectedLabel ?: "Tap to choose",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("▼", style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
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
    return List(14) {
        val date = dateFormat.format(calendar.time)
        val label = labelFormat.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        date to label
    }
}

private fun todayIsoDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)

private fun formatDateLabel(date: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        ?: return date
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(parsed)
} catch (_: Exception) {
    date
}

private fun formatTimeLabel(time: String): String = try {
    val parsed = SimpleDateFormat("HH:mm", Locale.US).parse(time)
        ?: return time
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(parsed)
} catch (_: Exception) {
    time
}
