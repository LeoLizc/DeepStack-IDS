"""
Módulo de Machine Learning para detección de ataques DDoS.

Este módulo proporciona funcionalidades para el procesamiento de datos, 
carga de modelos y predicción de ataques DDoS utilizando un enfoque de 
aprendizaje en capas (layer-based).
"""

import os
import pickle
from consts import stats_path, encoders_path, COLUMN_MAPPING, DROP_COLUMNS, LABELS, LY1_NN_BATCH_SIZE
# from keras.models import model_from_json
from models import decision_tree, gradient_boost, random_forest, neural_network, layer1_logistic, layer1_neural_network, layer1_ridgeClassifier, knn
import random
import numpy as np
import pandas as pd
from scipy.stats import mode
from utils import suppress_stdout_stderr


def process_df(df):
    """
    Procesa un DataFrame para adaptarlo al formato esperado por los modelos.
    
    Args:
        df (pandas.DataFrame): DataFrame a procesar.
        
    Returns:
        pandas.DataFrame: DataFrame procesado con las columnas adecuadas.
        
    Esta función realiza las siguientes operaciones:
    1. Duplica la columna 'Fwd Header Len' como 'Fwd Header Len1'
    2. Renombra las columnas según el mapeo definido en COLUMN_MAPPING
    3. Reordena las columnas según el orden en COLUMN_MAPPING
    4. Elimina los espacios en blanco de los nombres de las columnas
    5. Elimina las columnas definidas en DROP_COLUMNS
    """
    df['Fwd Header Len1'] = df['Fwd Header Len']
    df = df.rename(columns=COLUMN_MAPPING)

    # Seleccionar solo las columnas mapeadas, en el orden del segundo dataset
    ordered_columns = list(COLUMN_MAPPING.values())
    df = df[ordered_columns]

    # Strip column names to remove blank spaces at start and end
    df.columns = map(str.strip, df.columns)

    # Drop columns
    df.drop(columns=DROP_COLUMNS, inplace=True)
    return df

class MachineModel():
    """
    Clase principal para el modelo de detección de ataques DDoS.
    
    Esta clase implementa un enfoque de aprendizaje en capas (layer-based):
    - Capa 0: Modelos base (DT, RF, NN, etc.)
    - Capa 1: Modelos de ensamblaje que combinan las predicciones de la capa 0
    
    La clase se encarga de cargar los modelos, procesar los datos y realizar predicciones.
    """

    def __init__(self):
        """
        Inicializa una instancia de MachineModel.
        
        Inicializa todos los atributos necesarios para el modelo:
        - label_encoder: Codificador de etiquetas para las clases
        - standard_scaler: Normalizador para los datos de entrada
        - model: Modelo principal (decision tree)
        - loaded: Indicador de si los modelos han sido cargados
        - models_layer0: Diccionario de modelos de la capa 0
        - column_names: Nombres de columnas para la entrada de la capa 1
        - models_layer1: Diccionario de modelos de la capa 1
        """
        self.label_encoder = None
        self.standard_scaler = None
        self.model = None
        self.loaded = False
        self.models_layer0 = {}
        self.column_names = []
        self.models_layer1 = {}

    def _load_encoders(self):
        """
        Carga el codificador de etiquetas desde un archivo pickle.
        
        El codificador de etiquetas se utiliza para convertir entre índices numéricos
        y etiquetas de texto para las clases de ataques.
        """
        with open(os.path.join(encoders_path, 'label-encoder.pkl'), 'rb') as file:
            self.label_encoder = pickle.load(file)

    def _load_scaler(self):
        """
        Carga el normalizador desde un archivo pickle.
        
        El normalizador (standard scaler) se utiliza para estandarizar los datos
        de entrada antes de pasarlos a los modelos.
        """
        with open(os.path.join(stats_path, 'standard-scaler.pkl'), 'rb') as file:
            self.standard_scaler = pickle.load(file)

    def _load_models_layer0(self):
        """
        Carga los modelos de la capa 0.
        
        Actualmente solo se carga el modelo de árbol de decisión (DT),
        pero el código está preparado para cargar otros modelos (NN, RF, KN, GB)
        que están comentados.
        
        También genera los nombres de columnas que se utilizarán para la entrada
        de la capa 1, basados en las etiquetas y los identificadores de los modelos.
        """
        if not self.label_encoder:
            self._load_encoders()
        
        labels = list(self.label_encoder.classes_)

        self.models_layer0 = {
            'NN': neural_network(),
            'RF': random_forest(),
            'DT': decision_tree(),
            'KN': knn(),
            'GB': gradient_boost(),
        }

        self.column_names = [f'{label}_{model_id}_PROB' for model_id in self.models_layer0.keys() for label in labels]

    def _load_models_layer1(self):
        """
        Carga los modelos de la capa 1.
        
        Actualmente los modelos están comentados, pero el código está preparado
        para cargar modelos de regresión logística, RidgeClassifier y redes neuronales.
        """
        self.models_layer1 = {
            'M1': layer1_logistic(),
            'M2': layer1_ridgeClassifier(),
            'M3': layer1_neural_network(),
        }
        pass

    def load(self):
        """
        Carga todos los componentes necesarios para el modelo.
        
        Este método carga:
        1. Codificadores de etiquetas
        2. Normalizadores
        3. Modelos de la capa 0
        4. Modelos de la capa 1
        5. El modelo principal (decision tree)
        
        Y marca el modelo como cargado estableciendo loaded=True.
        """
        self._load_encoders()
        self._load_scaler()
        self._load_models_layer0()
        self._load_models_layer1()

        self.model = decision_tree()
        self.loaded = True

    def process_data(self, df):
        """
        Procesa los datos de entrada utilizando el normalizador.
        
        Args:
            df (pandas.DataFrame): DataFrame a procesar.
            
        Returns:
            numpy.ndarray: Datos normalizados.
        """
        return self.standard_scaler.transform(df)
    
    def _layer0(self, df):
        """
        Ejecuta la predicción con los modelos de la capa 0.
        
        Args:
            df (numpy.ndarray): Datos normalizados.
            
        Returns:
            list: Lista de tuplas (model_id, predictions) con las predicciones
                 de probabilidad para cada modelo de la capa 0.
        """
        layer0_results = []

        with suppress_stdout_stderr():
            for model_id, model in self.models_layer0.items():
                if model_id == 'NN':
                    preds = model.predict(df, verbose=0)
                else:
                    preds = model.predict_proba(df)
                layer0_results.append((model_id, preds))

        return layer0_results

    def predict_layer0(self, df):
        """
        Realiza predicciones utilizando sólo los modelos de la capa 0.
        
        Este método promedia las probabilidades de predicción de todos los
        modelos de la capa 0 para cada clase y selecciona la clase con mayor
        probabilidad promedio.
        
        Args:
            df (pandas.DataFrame): DataFrame con los datos a predecir.
            
        Returns:
            list: Lista de etiquetas predichas, una por cada fila en df.
            
        Nota:
            Si los modelos no están cargados (loaded=False), retorna etiquetas aleatorias.
        """
        if not self.loaded:
            return [random.choice(LABELS) for _ in range(len(df))]
        
        processed_data = self.process_data(df)
        predictions = self._layer0(processed_data)
        
        # Initialize list to store final label predictions
        final_predictions = []
        
        # Get all available class labels
        labels = list(self.label_encoder.classes_)
        
        # For each row in the dataset
        for row_idx in range(len(processed_data)):
            # Store probabilities for each model's prediction for this row
            row_probs = {}
            
            # Collect predictions from all models for this row
            for model_id, model_preds in predictions:
                # For each class, store its probability from this model
                for class_idx, class_label in enumerate(labels):
                    prob_key = f"{class_label}"
                    if prob_key not in row_probs:
                        row_probs[prob_key] = []
                    row_probs[prob_key].append(model_preds[row_idx][class_idx])
            
            # Calculate average probability for each class
            avg_probs = {label: sum(probs)/len(probs) for label, probs in row_probs.items()}
            
            # Find the class with highest average probability
            predicted_label = max(avg_probs, key=avg_probs.get)
            final_predictions.append(predicted_label)
        
        return final_predictions

    def predict(self, df):
        """
        Realiza predicciones utilizando el sistema completo de dos capas.
        
        Este método:
        1. Procesa los datos de entrada
        2. Obtiene predicciones de la capa 0
        3. Combina estas predicciones como entrada para la capa 1
        4. Obtiene predicciones de la capa 1
        5. Determina la predicción final como la moda (valor más común) entre todos los modelos
        
        Args:
            df (pandas.DataFrame): DataFrame con los datos a predecir.
            
        Returns:
            str: Etiqueta predicha para la primera fila de df.
            
        Nota:
            Si los modelos no están cargados (loaded=False), retorna una etiqueta aleatoria.
            Actualmente sólo devuelve la predicción para la primera fila de df.
        """
        if not self.loaded:
            return random.choice(LABELS)
        
        processed_data = self.process_data(df)
        layer0_predictions = self._layer0(processed_data)

        layer1_input_data = np.concatenate([preds for _, preds in layer0_predictions], axis=1)
        layer1_input_df = pd.DataFrame(layer1_input_data, columns=self.column_names)
        
        layer1_predictions = []
        with suppress_stdout_stderr():
            for model_id, model in self.models_layer1.items():
                if model_id == 'M3':
                    preds = model.predict(layer1_input_df, batch_size=LY1_NN_BATCH_SIZE, verbose=0)
                    preds = np.argmax(preds, axis=1)
                else:
                    preds = model.predict(layer1_input_df)
                layer1_predictions.append(preds)
    
            # y_predicted is the most common prediction across all models for each row
            y_predicted, _ = mode(np.array(layer1_predictions), axis=0)
            y_predicted = y_predicted.flatten()  # Flatten to ensure it's a 1D array

        classes = list(self.label_encoder.classes_)

        y_predicted_label = [classes[label] for label in y_predicted]

        # print('prueba', y_predicted)
        return y_predicted_label[0]

    def predict_model(self, df):
        """
        Realiza predicciones utilizando solo el modelo principal.
        
        A diferencia de predict() y predict_layer0(), este método utiliza
        únicamente el modelo principal (decision tree) para hacer predicciones.
        
        Args:
            df (pandas.DataFrame): DataFrame con los datos a predecir.
            
        Returns:
            str: Etiqueta predicha para la primera fila de df.
            
        Nota:
            Si los modelos no están cargados (loaded=False), retorna una etiqueta aleatoria.
            Actualmente sólo devuelve la predicción para la primera fila de df.
        """
        if not self.loaded:
            return random.choice(LABELS)
        y_predicted = self.model.predict(self.process_data(df))
        y_predicted: str = list(self.label_encoder.classes_)[y_predicted[0]]

        # print('prueba', y_predicted)
        return y_predicted