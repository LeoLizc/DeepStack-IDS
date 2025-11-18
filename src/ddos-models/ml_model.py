from consts import COLUMN_MAPPING, DROP_COLUMNS
from abc import ABC, abstractmethod

class MLModel(ABC):
    @abstractmethod
    def load(self) -> None:
        """Load the machine learning models from file system."""
        pass

    @abstractmethod
    def predict(self, df) -> str | None:
        """Make predictions using the trained model on the provided data."""
        pass

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

__all__ = ['MLModel', 'process_df']