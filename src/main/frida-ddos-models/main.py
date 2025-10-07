import sys
import pandas as pd
import numpy as np
from consts import HEADERS
from ml import MachineModel, process_df
import warnings
warnings.filterwarnings("ignore")
import os
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"   

sys.stdout = open(sys.stdout.fileno(), 'w', buffering=1)
if __name__ == "__main__":
    model = MachineModel()
    model.load()
    print('[INFO] Model loaded successfully')
    while True:
        try:
            line = sys.stdin.readline().strip()
            if line == "__TERMINATE__":
                print("[INFO] Recibida senhal de terminacion", flush=True)
                sys.exit(0)
            if not line:  # Fin del stream
                break
            
            # Procesar respuesta
            data = line.split(',')
            if len(data) != len(HEADERS):
                print(f"[ERROR] Invalid data length: expected {len(HEADERS)}, got {len(data)}", file=sys.stderr, flush=True)
                continue
            df = pd.DataFrame([data], columns=HEADERS)
            id = df['Flow ID'].values[0]
            df = process_df(df)

            # Verificar si el dataframe contiene valores infinitos o NaN
            if df.isin([np.inf, -np.inf]).any().any() or df.isna().any().any():
                print(f"[INFO] Dataframe contains infinite or NaN values, skipping prediction for Flow ID: {id}", flush=True)
                continue

            # r = model.predict_model(df)
            r = model.predict(df)

            # Enviar respuesta
            print(f'[RESULT] {r};{id}', flush=True)
            # print(line, flush=True)
            
        except Exception as e:
            print(f"[ERROR] {str(e)}", file=sys.stderr, flush=True)
