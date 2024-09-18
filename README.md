# Screensaver Demo Application

## Overview

The Screensaver Demo Application is an Android service-based application designed to prevent the device's screensaver from activating by detecting user interactions and media playback. It monitors touch events and media activity to keep the screen active. This application is particularly useful for scenarios where you need to prevent the device from going to sleep or entering screensaver mode during prolonged media playback or interactive sessions.

## Features

- **Touch Detection**: Monitors touch events to keep the screen active.
- **Click Detection**: Handles click events to reset the inactivity timer.
- **Media Playback Detection**: Detects if media is playing to prevent screensaver activation.
- **Back Button Handling**: Resets the inactivity timer when the back button is pressed.

## Setup

### Prerequisites

- Android Studio
- Android SDK (API 21 or higher recommended)

### Installation

1. **Clone the Repository**

   ```sh
   git clone https://github.com/siddhartha1998/screensaver-app.git
   cd screensaver-demo
