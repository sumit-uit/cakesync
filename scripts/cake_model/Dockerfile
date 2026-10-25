FROM --platform=linux/amd64 python:3.10-slim
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
# Model Maker only works with the TensorFlow 2.15 / Keras 2 stack.
RUN pip install --no-cache-dir "tensorflow==2.15.1" "keras==2.15.0" "tensorflow-text==2.15.0" "tf-models-official==2.15.0" \
    "tensorflow-addons==0.23.0" "mediapipe-model-maker==0.2.1.4" "mediapipe==0.10.14" "tensorflow-metadata==1.14.0" "tensorflow-datasets==4.9.3" "setuptools<70"
RUN python -c "from mediapipe_model_maker import object_detector, image_classifier; print('ok')"
WORKDIR /work
