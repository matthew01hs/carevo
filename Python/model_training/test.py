import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.preprocessing import LabelEncoder


CSV_FILE = "hand_landmarks_wrist_normalized.csv"

data = pd.read_csv(CSV_FILE)

X = data.drop("label", axis=1).values
y = data["label"].values

le = LabelEncoder()
y_encoded = le.fit_transform(y)

from sklearn.model_selection import train_test_split
_, X_test, _, y_test = train_test_split(
    X, y_encoded, test_size=0.2, random_state=42, stratify=y_encoded
)


TFLITE_MODEL_FILE = "asl_letters_model.tflite"

interpreter = tf.lite.Interpreter(model_path=TFLITE_MODEL_FILE)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

correct = 0
total = len(X_test)

for i in range(total):
    # TFLite expects float32 input
    input_data = np.array([X_test[i]], dtype=np.float32)

    interpreter.set_tensor(input_details[0]['index'], input_data)
    interpreter.invoke()
    output_data = interpreter.get_tensor(output_details[0]['index'])
    
    predicted_label = np.argmax(output_data[0])
    actual_label = y_test[i]

    if predicted_label == actual_label:
        correct += 1

    print(f"Sample {i+1}: Predicted={le.inverse_transform([predicted_label])[0]}, Actual={le.inverse_transform([actual_label])[0]}")

accuracy = correct / total
print(f"\nTFLite Model Test Accuracy: {accuracy:.2f}")
