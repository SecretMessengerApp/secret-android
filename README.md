# Secret for Android

## What is included in the open source client

The project in this repository contains the Secret for Android client project. You can build the project yourself. However, there are some differences with the binary Secret client available on the Play Store.
These differences are:
- the open source project does not include the API keys of Firebase, HockeyApp and other 3rd party services.

## Prerequisites
In order to build Wire for Android locally, it is necessary to install the following tools on the local machine:
- JDK 8
- Android SDK
- Android NDK

## How to build locally
1. Check out the secret-android repository.
2. Switch to latest release branch `release`
3. From the checkout folder, run `./gradlew assembleProdRelease`. This will pull in all the necessary dependencies from Maven.

## Android Studio
When importing project in Android Studio **do not allow** gradle plugin update. Our build setup requires Android Plugin for Gradle version 3.2.1.
