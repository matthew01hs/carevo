import cv2
import csv
import time
import mediapipe as mp
import math

LABEL = "A"
OUTPUT_CSV = "hand_landmarks_wrist_normalized.csv"

saved_count = 0
last_save_time = 0

mp_hands = mp.solutions.hands
mp_drawing = mp.solutions.drawing_utils
mp_drawing_styles = mp.solutions.drawing_styles

hands = mp_hands.Hands(
    static_image_mode=False,
    max_num_hands=1,
    min_detection_confidence=0.7,
    min_tracking_confidence=0.7
)

cap = cv2.VideoCapture(0)

file_exists = False
try:
    with open(OUTPUT_CSV, "r"):
        file_exists = True
except FileNotFoundError:
    pass

def normalize_landmarks_wrist(landmarks):
    wrist = landmarks[0]
    middle_tip = landmarks[12]

    scale = math.sqrt(
        (middle_tip.x - wrist.x)**2 +
        (middle_tip.y - wrist.y)**2 +
        (middle_tip.z - wrist.z)**2
    )
    if scale == 0:
        scale = 1  

    normalized = []
    for lm in landmarks:
        norm_x = (lm.x - wrist.x) / scale
        norm_y = (lm.y - wrist.y) / scale
        norm_z = (lm.z - wrist.z) / scale
        normalized.extend([norm_x, norm_y, norm_z])
    return normalized

with open(OUTPUT_CSV, "a", newline="") as f:
    writer = csv.writer(f)

    if not file_exists:
        header = ["label"]
        for i in range(21):
            header.extend([f"x{i}", f"y{i}", f"z{i}"])
        writer.writerow(header)

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = hands.process(rgb)

        if results.multi_hand_landmarks:
            hand = results.multi_hand_landmarks[0]

            mp_drawing.draw_landmarks(
                frame,
                hand,
                mp_hands.HAND_CONNECTIONS,
                mp_drawing_styles.get_default_hand_landmarks_style(),
                mp_drawing_styles.get_default_hand_connections_style()
            )

            key = cv2.waitKey(1) & 0xFF
            now = time.time()

            if key == ord("s") and now - last_save_time > 0.08:
                normalized_landmarks = normalize_landmarks_wrist(hand.landmark)
                row = [LABEL] + normalized_landmarks

                writer.writerow(row)
                saved_count += 1
                last_save_time = now
                print(f"Saved {saved_count} samples for letter {LABEL}")

        cv2.imshow("Capture Hand", frame)

        key = cv2.waitKey(1) & 0xFF
        if key == ord("q"):
            break

cap.release()
cv2.destroyAllWindows()
hands.close()
