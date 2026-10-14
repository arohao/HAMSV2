package com.aroha.hams.data

import com.aroha.hams.dataclass.Appointment
import com.aroha.hams.dataclass.Role
import com.aroha.hams.dataclass.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseHospitalRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun currentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        firebaseUser.reload().await()
        if (!firebaseUser.isEmailVerified) {
            sendVerificationAndSignOut(firebaseUser)
            error("Verify your email address before signing in. A new verification email has been sent.")
        }
        return readUserOrPatient(firebaseUser)
    }

    suspend fun signIn(email: String, password: String): User {
        val credential = auth
            .signInWithEmailAndPassword(email.trim(), password)
            .await()
        val firebaseUser = credential.user
            ?: error("Firebase did not return a user after sign-in.")
        firebaseUser.reload().await()
        if (!firebaseUser.isEmailVerified) {
            sendVerificationAndSignOut(firebaseUser)
            error("Verify your email address before signing in. A new verification email has been sent.")
        }
        return readUserOrPatient(firebaseUser)
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        role: Role,
        specialty: String
    ): User {
        val credential = auth
            .createUserWithEmailAndPassword(email.trim(), password)
            .await()
        val firebaseUser = credential.user
            ?: error("Firebase did not return a user after registration.")
        val uid = firebaseUser.uid
        val user = User(
            id = uid,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            email = email.trim(),
            role = role,
            specialty = specialty.trim()
        )
        try {
            firestore().collection(USERS).document(uid).set(user.toDocument()).await()
        } catch (profileException: Exception) {
            try {
                auth.currentUser?.delete()?.await()
            } catch (cleanupException: Exception) {
                throw IllegalStateException(
                    "Profile creation failed and Firebase could not remove the incomplete account. " +
                        "Profile error: ${profileException.message}; cleanup error: ${cleanupException.message}",
                    profileException
                )
            }
            throw profileException
        }
        try {
            firebaseUser.sendEmailVerification().await()
        } finally {
            auth.signOut()
        }
        return user
    }

    suspend fun getDoctors(): List<User> =
        firestore().collection(USERS)
            .whereEqualTo("role", Role.DOCTOR.name)
            .get()
            .await()
            .documents
            .map { it.toUser() }
            .sortedBy(User::fullName)

    suspend fun getAppointments(user: User): List<Appointment> {
        val field = if (user.role == Role.DOCTOR) "doctorId" else "patientId"
        return firestore().collection(APPOINTMENTS)
            .whereEqualTo(field, user.id)
            .get()
            .await()
            .documents
            .map { it.toAppointment() }
            .sortedWith(compareBy(Appointment::date, Appointment::time))
    }

    suspend fun bookAppointment(
        patient: User,
        doctor: User,
        date: String,
        time: String
    ) {
        val appointmentId = "${doctor.id}_${date}_$time"
        val appointment = firestore().collection(APPOINTMENTS).document(appointmentId)
        firestore().runTransaction { transaction ->
            check(transaction.get(appointment).exists().not()) {
                "That appointment slot has already been booked. Please choose another time."
            }
            transaction.set(
                appointment,
                mapOf(
                    "patientId" to patient.id,
                    "patientName" to patient.fullName,
                    "doctorId" to doctor.id,
                    "doctorName" to doctor.fullName,
                    "specialty" to doctor.specialty,
                    "date" to date,
                    "time" to time,
                    "status" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
    }

    suspend fun signOut() {
        auth.signOut()
    }

    private suspend fun sendVerificationAndSignOut(firebaseUser: FirebaseUser) {
        try {
            firebaseUser.sendEmailVerification().await()
        } finally {
            auth.signOut()
        }
    }

    private suspend fun readUserOrPatient(firebaseUser: FirebaseUser): User {
        try {
            val snapshot = firestore().collection(USERS).document(firebaseUser.uid).get().await()
            if (snapshot.exists()) return snapshot.toUser()
        } catch (exception: FirebaseFirestoreException) {
            if (exception.code !in setOf(
                    FirebaseFirestoreException.Code.NOT_FOUND,
                    FirebaseFirestoreException.Code.UNAVAILABLE
                )
            ) {
                throw exception
            }
        }
        return firebaseUser.toPatient()
    }

    private fun FirebaseUser.toPatient(): User {
        val name = displayName.orEmpty().trim()
            .ifBlank { email.orEmpty().substringBefore("@").ifBlank { "Patient" } }
        val nameParts = name.split(Regex("\\s+"), limit = 2)
        return User(
            id = uid,
            firstName = nameParts.first(),
            lastName = nameParts.getOrElse(1) { "" },
            email = email.orEmpty(),
            role = Role.PATIENT
        )
    }

    private fun firestore(): FirebaseFirestore = database

    private fun User.toDocument(): Map<String, Any> = buildMap {
        put("id", id)
        put("firstName", firstName)
        put("lastName", lastName)
        put("email", email)
        put("role", role.name)
        put("specialty", specialty)
        put("address", address)
        put("phoneNumber", phoneNumber)
        put("createdAt", FieldValue.serverTimestamp())
    }

    private fun DocumentSnapshot.toUser(): User {
        val role = getString("role")
            ?: error("The user profile is missing its role.")
        return User(
            id = id,
            firstName = getString("firstName").orEmpty(),
            lastName = getString("lastName").orEmpty(),
            email = getString("email").orEmpty(),
            role = Role.valueOf(role),
            specialty = getString("specialty").orEmpty(),
            address = getString("address").orEmpty(),
            phoneNumber = getString("phoneNumber").orEmpty()
        )
    }

    private fun DocumentSnapshot.toAppointment(): Appointment = Appointment(
        id = id,
        patientId = getString("patientId").orEmpty(),
        patientName = getString("patientName").orEmpty(),
        doctorId = getString("doctorId").orEmpty(),
        doctorName = getString("doctorName").orEmpty(),
        specialty = getString("specialty").orEmpty(),
        date = getString("date").orEmpty(),
        time = getString("time").orEmpty(),
        status = getString("status").orEmpty()
    )

    private companion object {
        const val USERS = "users"
        const val APPOINTMENTS = "appointments"
    }
}
