# Design-and-implementation-of-an-intelligent-and-secure-system-for-parking-management-EasePark-

This repository serves as an appendix for my bachelor's thesis.




\# EasePark Application for parking management (Bachelor's Thesis Project)



This project is a complete, real-time parking management system developed as a bachelor's thesis. It consists of a native Android application for users and a Node.js backend server that manages the logic and data.



\## Key Features

\- \*\*Real-Time Map\*\*: View parking spots and their status (Available, Pending, Active) updated in real-time.

\- \*\*Reservation System\*\*: Users can create, extend, and cancel reservations.

\- \*\*Automated Lifecycle\*\*: The backend scheduler automatically handles the transition of reservations from pending to active and to completed.

\- \*\*Secure Authentication\*\*: JWT-based authentication with support for 2FA and secure password storage using bcrypt.

\- \*\*Reactive Architecture\*\*: The Android app is built on a modern MVVM architecture with a "Single Source of Truth" repository, ensuring a consistent and responsive UI.



\## Project Components



This repository is a monorepo containing the two main parts of the system:



\* ### ➡️ \*\*\[Frontend (Android Application)](/frontend-android)\*\*

&nbsp;   \* A native Android application built with Kotlin, Google Maps SDK, and modern Jetpack libraries.



\* ### ➡️ \*\*\[Backend (Node.js Server)](/backend-nodejs)\*\*

&nbsp;   \* A RESTful API built with Node.js, Express, and TypeScript, connected to a PostgreSQL database. It handles business logic, authentication, and real-time updates via Server-Sent Events (SSE).



\## High-Level Architecture



The system is designed following a classic Client-Server architecture. The Android client communicates with the backend via a REST API for actions and maintains a persistent connection using Server-Sent Events (SSE) for real-time status updates.



`\[Client Android (MVVM)] <--- (REST API \& SSE) ---> \[Backend Node.js] <---> \[PostgreSQL Database]`





