import sys
import pandas as pd
import numpy as np
from consts import HEADERS
from default_model import model as default_model_instance
from ml_model import process_df, MLModel
import warnings
warnings.filterwarnings("ignore")
import os
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"   

sys.stdout = open(sys.stdout.fileno(), 'w', buffering=1)
if __name__ == "__main__":
    # Intentar importar el modelo del módulo 'modelos'
    try:
        from modelos import model
        if not isinstance(model, MLModel):
            raise TypeError("model is not an instance of MLModel")
    except (ImportError, TypeError, AttributeError):
        # Usar la instancia por defecto si la importación falla o no es MLModel
        model = default_model_instance
    
    # Cargar el modelo si es necesario
    if hasattr(model, 'load'):
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
