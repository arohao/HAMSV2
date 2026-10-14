# HAMS

HAMS is a simple hospital appointment app for patients and doctors.

![HAMS logo](app/src/main/res/drawable-nodpi/hams_logo.png)

```mermaid
flowchart LR
    SignIn[Sign in or register] --> Patient[Patient: find doctors and book an open time]
    SignIn --> Doctor[Doctor: publish open times and view appointments]
    Patient --> Calendar[View appointments in a list or calendar]
    Doctor --> Calendar
```

## How it works

- Sign in and register with Firebase Authentication. New accounts must verify their email.
- Doctor accounts publish available appointment times.
- Patients choose an open time to book with a doctor.
- Both patients and doctors can view appointments as a list or calendar.

## Firebase
The app is configured as Firebase project.
Firebase Cloud Firestore stores profiles, schedules, and appointments.

## Build

Open this project in Android Studio and run the `app` configuration, or build from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```
