# Flights App

This Android application provides real-time flight tracking using the AviationStack API. The app is built using Kotlin + xml in Android Studio and features a multi-screen interface.

## Features

- Track real-time flight location, speed, and status using flight number.
- Two-screen navigation:
  - **Screen 1**: Flight tracking
  - **Screen 2**: Average flight time calculator based on real historical data.

## Project Structure

- `MainActivity.kt`: Launch screen that provides navigation to `Screen1Activity` and `Screen2Activity`.
- `Screen1Activity.kt`: Handles user input, API requests, and displays live flight information.
- `Screen2Activity.kt`: Allows users to query the average flight time between two airports using a local database populated from the AviationStack API.

## Dependencies

- `OkHttp`: For making HTTP requests.
- `Gson`: For parsing JSON responses.
- `AviationStack API`: Used to fetch real-time and historical flight data.
- `Room`: For local data persistence.
- `WorkManager`: For periodic background flight data fetching.

## Question 1 Functionality 

1. **User Input**: User enters a flight number on Screen 1.
2. **API Call**: On clicking the "Track" button, the app sends a request to the AviationStack API.
3. **Data Handling**: The app filters for flights that are currently active and extracts latitude, longitude, speed, and airport info.
4. **UI Update**: Displays the real-time flight details on the screen.
5. **Auto Refresh**: Flight data refreshes every 60 seconds using a handler and runnable.

## Question 2 Functionality

1. User inputs source and destination IATA airport codes.
2. On clicking "Get Average", the app queries the local Room database for historical flight durations.
3. If no data is found, it fetches the flight history from the AviationStack API.
4. Fetched data is stored in a local SQLite database using Room.
5. Average flight duration (in minutes) is calculated and shown.
6. Background data sync occurs every 15 minutes using WorkManager.

## API Key

The API key for the AviationStack service is hardcoded for development use:
