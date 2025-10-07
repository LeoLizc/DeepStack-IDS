def decision_tree():
    import os
    import pickle
    from consts import model_paths
    normalize_name = 'standard-v2'

    # Load model
    with open(os.path.join(model_paths[2], f'model-dt-{normalize_name}.object.pkl'), 'rb') as file:
        model_rf = pickle.load(file)
    return model_rf

def neural_network():
    from tensorflow.keras.models import model_from_json
    import os 
    from consts import model_paths
    normalize_name = 'standard-v4'

    with open(os.path.join(model_paths[0], f'model-nn-{normalize_name}.architecture.json'), 'r') as file:
        model_nn = model_from_json(file.read())

    # Load weights
    model_nn.load_weights(os.path.join(model_paths[0], f'model-nn-{normalize_name}.weights.h5'))
    return model_nn

def random_forest():
    import os
    import pickle
    from consts import model_paths
    normalize_name = 'standard-v2'

    # Load model
    with open(os.path.join(model_paths[1], f'model-rf-{normalize_name}.object.pkl'), 'rb') as file:
        model_rf = pickle.load(file)
    return model_rf

def gradient_boost():
    import os
    import pickle
    from consts import model_paths
    normalize_name = 'standard-v2'

    # Load model
    with open(os.path.join(model_paths[4], f'model-gb-{normalize_name}.object.pkl'), 'rb') as file:
        model_gb = pickle.load(file)
    return model_gb

def knn():
    import os
    import pickle
    from consts import model_paths
    normalize_name = 'standard-v2'

    # Load model
    with open(os.path.join(model_paths[3], f'model-kn-{normalize_name}.object.pkl'), 'rb') as file:
        model_knn = pickle.load(file)
    return model_knn

def layer1_logistic():
    import os
    import pickle
    from consts import layer1_paths
    version = 'v1'

    # Load model
    with open(os.path.join(layer1_paths[0], f'model-lr-{version}.object.pkl'), 'rb') as file:
        model_logistic = pickle.load(file)
    return model_logistic

def layer1_ridgeClassifier():
    import os
    import pickle
    from consts import layer1_paths
    version = 'v1'

    # Load model
    with open(os.path.join(layer1_paths[2], f'model-rc-{version}.object.pkl'), 'rb') as file:
        model_ridge = pickle.load(file)
    return model_ridge

def layer1_neural_network():
    from tensorflow.keras.models import model_from_json
    import os 
    from consts import layer1_paths
    version = 'v1'

    with open(os.path.join(layer1_paths[1], f'model-nn-{version}.architecture.json'), 'r') as file:
        model_nn = model_from_json(file.read())

    # Load weights
    model_nn.load_weights(os.path.join(layer1_paths[1], f'model-nn-{version}.weights.h5'))
    return model_nn