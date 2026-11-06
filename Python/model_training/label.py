import pandas as pd
from sklearn.preprocessing import LabelEncoder


CSV_FILE = "hand_landmarks_wrist_normalized.csv"

data = pd.read_csv(CSV_FILE)
y = data["label"].values

le = LabelEncoder()
y_encoded = le.fit_transform(y)

print("Label order in model:", le.classes_)