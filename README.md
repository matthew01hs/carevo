# Carevo: AI-Based Sign Language Fingerspelling Translator

Carevo is an Android application that uses computer vision and machine learning to recognise alphabetical hand gestures and convert them into readable sentences. Users can spell out words using fingerspelling gestures, with the recognised text displayed in the application and converted into spoken audio through text-to-speech functionality.

## Features

- **29 gesture classes:** Recognises letters A–Z, Space, Delete, and Period.
- **Real-time hand tracking:** Uses MediaPipe Hands to detect and track hand landmarks through the device camera.
- **Gesture recognition:** Classifies hand gestures using a neural network trained with TensorFlow.
- **Sentence construction:** Combines recognised characters into words and sentences, with support for spaces, deletion, and periods.
- **Text-to-speech:** Converts constructed sentences into spoken audio.
- **On-device inference:** Uses TensorFlow Lite to run the trained model within the Android application.

## Technologies Used

- **Programming:** Python
- **Computer Vision:** OpenCV, MediaPipe Hands
- **Machine Learning:** TensorFlow, Keras, scikit-learn
- **Model Deployment:** TensorFlow Lite
- **Android Development:** Android Studio

## How It Works

1. **Hand Detection:** MediaPipe Hands detects the user's hand through the device camera.
2. **Landmark Extraction:** Extracts 21 hand landmarks, each containing x, y, and z coordinates. The coordinates are normalised before classification.
3. **Gesture Classification:** The processed landmarks are passed to a trained neural network to predict the corresponding gesture.
4. **Sentence Construction:** Recognised characters are displayed and combined into words and sentences. Users can insert spaces, delete characters, and add periods.
5. **Speech Output:** The constructed text is converted into spoken audio using text-to-speech.

## Dataset and Model

- Collected approximately 29,000 gesture samples across 29 classes, with approximately 1,000 samples per class.
- Extracted and normalised 21 hand landmarks from each sample.
- Trained a neural network using TensorFlow and Keras.
- Converted the trained model to TensorFlow Lite for integration into the Android application.
- Achieved **100% accuracy on the held-out test dataset** using an 80/20 stratified train-test split.

*The reported accuracy reflects performance on the collected test dataset and does not necessarily represent performance across different users, lighting conditions, or environments.*

## Repository Structure

```text
Carevo/
├── Android/
│   └── Android Studio project
├── Python/
│   ├── data collection and training model
├── README.md
└── .gitignore
```

## Running the Project

The Android application can be installed using the APK available under the repository's **Releases** section.

The Python scripts contain the data collection, processing, training, and testing components used during development.

**Note:** The Python scripts may require different dependency versions and configurations. Their original development environments are not fully documented, so additional setup may be necessary to run them.

## Limitations

- Supports alphabetical fingerspelling rather than continuous sign language translation.
- Recognition performance may be affected by lighting, hand positioning, and differences between users.
- The model was trained on a collected dataset and may require additional data and testing to improve generalisation.

