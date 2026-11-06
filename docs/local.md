# LLM Inference guide for Android

| **Note:** Use of the MediaPipe LLM Inference API is subject to the [Generative AI Prohibited
| Use Policy](https://policies.google.com/terms/generative-ai/use-policy).

The LLM Inference API lets you run large language models (LLMs) completely on-device
for Android applications, which you can use to perform a wide range of tasks,
such as generating text, retrieving information in natural language form, and
summarizing documents. The task provides built-in support for multiple
text-to-text large language models, so you can apply the latest on-device
generative AI models to your Android apps.

To quickly add the LLM Inference API to your Android application, follow the
[Quickstart](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#quickstart). For a basic example of an Android application running
the LLM Inference API, see the [sample application](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#sample-application). For a more
in-depth understanding of how the LLM Inference API works, refer to the
[configuration options](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#configuration-options), [model
conversion](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index#convert-pytorch), and [LoRA tuning](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#lora-customization)
sections.

You can see this task in action with the [MediaPipe Studio
demo](https://mediapipe-studio.webapps.google.com/studio/demo/llm_inference).
For more information about the capabilities, models, and configuration options
of this task, see the [Overview](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index).

## Quickstart

Use the following steps to add the LLM Inference API to your Android application.
The LLM Inference API is optimized for high-end Android devices, such as Pixel 8 and
Samsung S23 or later, and does not reliably support device emulators.

### Add dependencies

The LLM Inference API uses the `com.google.mediapipe:tasks-genai` library. Add this
dependency to the `build.gradle` file of your Android app:  

    dependencies {
        implementation 'com.google.mediapipe:tasks-genai:0.10.27'
    }

### Download a model

Download Gemma-3 1B in a 4-bit quantized format from [Hugging
Face](https://huggingface.co/litert-community/Gemma3-1B-IT). For more
information on the available models, see the [Models
documentation](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index#models).

Push the content of the <var translate="no">output_path</var> folder to the Android
device.  

    $ adb shell rm -r /data/local/tmp/llm/ # Remove any previously loaded models
    $ adb shell mkdir -p /data/local/tmp/llm/
    $ adb push <var translate="no">output_path</var> /data/local/tmp/llm/<var translate="no">model_version</var>.task

| **Note:** During development, you can use adb to push the model to your test device for a simpler workflow. For deployment, host the model on a server and download it at runtime. The model is too large to be bundled in an APK.

### Initialize the Task

Initialize the task with basic configuration options:  

    // Set the configuration options for the LLM Inference task
    val taskOptions = LlmInferenceOptions.builder()
            .setModelPath('/data/local/tmp/llm/<var translate="no"><span class="devsite-syntax-n">model_version</span></var>.task')
            .setMaxTopK(64)
            .build()

    // Create an instance of the LLM Inference task
    llmInference = LlmInference.createFromOptions(context, taskOptions)

### Run the Task

Use the `generateResponse()` method to generate a text response. This produces a
single generated response.  

    val result = llmInference.generateResponse(inputPrompt)
    logger.atInfo().log("result: $result")

To stream the response, use the `generateResponseAsync()` method.  

    val options = LlmInference.LlmInferenceOptions.builder()
      ...
      .setResultListener { partialResult, done ->
        logger.atInfo().log("partial result: $partialResult")
      }
      .build()

    llmInference.generateResponseAsync(inputPrompt)

## Sample application

| **Note:** The Google AI Edge Gallery is an Alpha release.

To see the LLM Inference APIs in action and explore a comprehensive range of
on-device Generative AI capabilities, check out the [Google AI Edge Gallery
app](https://github.com/google-ai-edge/gallery).

The Google AI Edge Gallery is an open-source Android application that serves as
an interactive playground for developers. It showcases:

- Practical examples of using the LLM Inference API for various tasks, including:
  - Ask Image: Upload an image and ask questions about it. Get descriptions, solve problems, or identify objects.
  - Prompt Lab: Summarize, rewrite, generate code, or use freeform prompts to explore single-turn LLM use cases.
  - AI Chat: Engage in multi-turn conversations.
- The ability to discover, download, and experiment with a variety of LiteRT-optimized models from the Hugging Face LiteRT Community and official Google releases (e.g. Gemma 3N).
- Real-time on-device performance benchmarks for different models (Time To First Token, decode speed, etc.).
- How to import and test your own custom `.litertlm` or `.task` models.

This app is a resource to understand the practical implementation of the LLM
Inference API and the potential of on-device Generative AI. Explore the source
code and download the app from the[Google AI Edge Gallery GitHub
repository](https://github.com/google-ai-edge/gallery).

## Configuration options

Use the following configuration options to set up an Android app:

|   Option Name    |                                                                                          Description                                                                                          | Value Range | Default Value |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|---------------|
| `modelPath`      | The path to where the model is stored within the project directory.                                                                                                                           | PATH        | N/A           |
| `maxTokens`      | The maximum number of tokens (input tokens + output tokens) the model handles.                                                                                                                | Integer     | 512           |
| `topK`           | The number of tokens the model considers at each step of generation. Limits predictions to the top k most-probable tokens.                                                                    | Integer     | 40            |
| `temperature`    | The amount of randomness introduced during generation. A higher temperature results in more creativity in the generated text, while a lower temperature produces more predictable generation. | Float       | 0.8           |
| `randomSeed`     | The random seed used during text generation.                                                                                                                                                  | Integer     | 0             |
| `loraPath`       | The absolute path to the LoRA model locally on the device. Note: this is only compatible with GPU models.                                                                                     | PATH        | N/A           |
| `resultListener` | Sets the result listener to receive the results asynchronously. Only applicable when using the async generation method.                                                                       | N/A         | N/A           |
| `errorListener`  | Sets an optional error listener.                                                                                                                                                              | N/A         | N/A           |

## Multimodal prompting

The LLM Inference API Android APIs support multimodal prompting with models that
accept text, image, and audio inputs. With multimodality enabled, users can
include a combination of images and text or audio and text in their prompts.The
LLM then provides a text response.

To get started, use a MediaPipe-compatible variant of [Gemma
3n](https://ai.google.dev/gemma/docs/gemma-3n):

- [Gemma-3n
  E2B](https://huggingface.co/google/gemma-3n-E2B-it-litert-preview): an effective 2B model of the Gemma-3n family.
- [Gemma-3n
  E4B](https://huggingface.co/google/gemma-3n-E4B-it-litert-preview): an effective 4B model of the Gemma-3n family.

For more information, see the [Gemma-3n
documentation](https://ai.google.dev/gemma/docs/gemma-3n).

Follow the steps below to enable image or audio input for LLM Inference API.

### Image input

To provide images within a prompt, convert the input images or frames to a
`com.google.mediapipe.framework.image.MPImage` object before passing it to the
LLM Inference API:  

    import com.google.mediapipe.framework.image.BitmapImageBuilder
    import com.google.mediapipe.framework.image.MPImage

    // Convert the input Bitmap object to an MPImage object to run inference
    val mpImage = BitmapImageBuilder(image).build()

To enable vision support for the LLM Inference API, set the `EnableVisionModality`
configuration option to `true` within the Graph options:  

    LlmInferenceSession.LlmInferenceSessionOptions sessionOptions =
      LlmInferenceSession.LlmInferenceSessionOptions.builder()
        ...
        .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
        .build();

Set the maximum of 10 images per session.  

    LlmInferenceOptions options = LlmInferenceOptions.builder()
      ...
      .setMaxNumImages(10)
      .build();

The following is an example implementation of the LLM Inference API set up to handle
vision and text inputs:  

    MPImage image = getImageFromAsset(BURGER_IMAGE);

    LlmInferenceSession.LlmInferenceSessionOptions sessionOptions =
      LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTopK(10)
        .setTemperature(0.4f)
        .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
        .build();

    try (LlmInference llmInference =
        LlmInference.createFromOptions(ApplicationProvider.getApplicationContext(), options);
      LlmInferenceSession session =
        LlmInferenceSession.createFromOptions(llmInference, sessionOptions)) {
      session.addQueryChunk("Describe the objects in the image.");
      session.addImage(image);
      String result = session.generateResponse();
    }

### Audio input

Enable audio support in LlmInferenceOptions  

    val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
      ...
      .setAudioModelOptions(AudioModelOptions.builder().build())
      .build()

Enable Audio support in sessionOptions  

        val sessionOptions =  LlmInferenceSessionOptions.builder()
          ...
          .setGraphOptions(GraphOptions.builder().setEnableAudioModality(true).build())
          .build()

Send audio data during inference.
Note: Audio must be mono channel formatted as .wav  


    val audioData: ByteArray = ...
    inferenceEngine.llmInferenceSession.addAudio(audioData)

The following is an example implementation of the LLM Inference API set up to handle
audio and text inputs:  

    val audioData: ByteArray = ...
    val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
      ...
      .setAudioModelOptions(AudioModelOptions.builder().build())
      .build()
    val sessionOptions =  LlmInferenceSessionOptions.builder()
      ...
      .setGraphOptions(GraphOptions.builder().setEnableAudioModality(true).build())
      .build()

    LlmInference.createFromOptions(context, inferenceOptions).use { llmInference ->
      LlmInferenceSession.createFromOptions(llmInference, sessionOptions).use { session ->
        session.addQueryChunk("Transcribe the following speech segment:")
        session.addAudio(audioData)
        val result = session.generateResponse()
      }
    }

## LoRA customization

The LLM Inference API supports LoRA (Low-Rank Adaptation) tuning using the
[PEFT](https://huggingface.co/docs/peft/main/en/index) (Parameter-Efficient
Fine-Tuning) library. LoRA tuning customizes the behavior of LLMs through a
cost-effective training process, creating a small set of trainable weights based
on new training data rather than retraining the entire model.

The LLM Inference API supports adding LoRA weights to attention layers of the
[Gemma-2 2B](https://huggingface.co/google/gemma-2-2b), [Gemma
2B](https://huggingface.co/google/gemma-2b) and
[Phi-2](https://huggingface.co/microsoft/phi-2) models. Download the model in
the `safetensors` format.

The base model must be in the `safetensors` format in order to create LoRA
weights. After LoRA training, you can convert the models into the FlatBuffers
format to run on MediaPipe.

### Prepare LoRA weights

Use the [LoRA
Methods](https://huggingface.co/docs/peft/main/en/task_guides/lora_based_methods)
guide from PEFT to train a fine-tuned LoRA model on your own dataset.

The LLM Inference API only supports LoRA on attention layers, so only specify the
attention layers in `LoraConfig`:  

    # For Gemma
    from peft import LoraConfig
    config = LoraConfig(
        r=LORA_RANK,
        target_modules=["q_proj", "v_proj", "k_proj", "o_proj"],
    )

    # For Phi-2
    config = LoraConfig(
        r=LORA_RANK,
        target_modules=["q_proj", "v_proj", "k_proj", "dense"],
    )

After training on the prepared dataset and saving the model, the fine-tuned LoRA
model weights are available in `adapter_model.safetensors`. The `safetensors`
file is the LoRA checkpoint used during model conversion.

### Model conversion

Use the MediaPipe Python Package to convert the model weights into the
Flatbuffer format. The `ConversionConfig` specifies the base model options along
with the additional LoRA options.
**Note:** Since the API only supports LoRA inference with GPU, the backend must be set to `'gpu'`.  

    import mediapipe as mp
    from mediapipe.tasks.python.genai import converter

    config = converter.ConversionConfig(
      # Other params related to base model
      ...
      # Must use gpu backend for LoRA conversion
      backend='gpu',
      # LoRA related params
      lora_ckpt=<var translate="no"><span class="devsite-syntax-n">LORA_CKPT</span></var>,
      lora_rank=<var translate="no"><span class="devsite-syntax-n">LORA_RANK</span></var>,
      lora_output_tflite_file=<var translate="no"><span class="devsite-syntax-n">LORA_OUTPUT_FILE</span></var>,
    )

    converter.convert_checkpoint(config)

The converter will produce two Flatbuffer files, one for the base model and
another for the LoRA model.

### LoRA model inference

Android supports static LoRA during initialization. To load a LoRA model,
specify the LoRA model path as well as the base LLM.  

    // Set the configuration options for the LLM Inference task
    val options = LlmInferenceOptions.builder()
            .setModelPath(<var translate="no"><span class="devsite-syntax-n">BASE_MODEL_PATH</span></var>)
            .setMaxTokens(1000)
            .setTopK(40)
            .setTemperature(0.8)
            .setRandomSeed(101)
            .setLoraPath(<var translate="no"><span class="devsite-syntax-n">LORA_MODEL_PATH</span></var>)
            .build()

    // Create an instance of the LLM Inference task
    llmInference = LlmInference.createFromOptions(context, options)

To run LLM inference with LoRA, use the same `generateResponse()` or
`generateResponseAsync()` methods as the base model.



# LLM Inference guide for Android

| **Note:** Use of the MediaPipe LLM Inference API is subject to the [Generative AI Prohibited
| Use Policy](https://policies.google.com/terms/generative-ai/use-policy).

The LLM Inference API lets you run large language models (LLMs) completely on-device
for Android applications, which you can use to perform a wide range of tasks,
such as generating text, retrieving information in natural language form, and
summarizing documents. The task provides built-in support for multiple
text-to-text large language models, so you can apply the latest on-device
generative AI models to your Android apps.

To quickly add the LLM Inference API to your Android application, follow the
[Quickstart](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#quickstart). For a basic example of an Android application running
the LLM Inference API, see the [sample application](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#sample-application). For a more
in-depth understanding of how the LLM Inference API works, refer to the
[configuration options](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#configuration-options), [model
conversion](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index#convert-pytorch), and [LoRA tuning](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#lora-customization)
sections.

You can see this task in action with the [MediaPipe Studio
demo](https://mediapipe-studio.webapps.google.com/studio/demo/llm_inference).
For more information about the capabilities, models, and configuration options
of this task, see the [Overview](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index).

## Quickstart

Use the following steps to add the LLM Inference API to your Android application.
The LLM Inference API is optimized for high-end Android devices, such as Pixel 8 and
Samsung S23 or later, and does not reliably support device emulators.

### Add dependencies

The LLM Inference API uses the `com.google.mediapipe:tasks-genai` library. Add this
dependency to the `build.gradle` file of your Android app:  

    dependencies {
        implementation 'com.google.mediapipe:tasks-genai:0.10.27'
    }

### Download a model

Download Gemma-3 1B in a 4-bit quantized format from [Hugging
Face](https://huggingface.co/litert-community/Gemma3-1B-IT). For more
information on the available models, see the [Models
documentation](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index#models).

Push the content of the <var translate="no">output_path</var> folder to the Android
device.  

    $ adb shell rm -r /data/local/tmp/llm/ # Remove any previously loaded models
    $ adb shell mkdir -p /data/local/tmp/llm/
    $ adb push <var translate="no">output_path</var> /data/local/tmp/llm/<var translate="no">model_version</var>.task

| **Note:** During development, you can use adb to push the model to your test device for a simpler workflow. For deployment, host the model on a server and download it at runtime. The model is too large to be bundled in an APK.

### Initialize the Task

Initialize the task with basic configuration options:  

    // Set the configuration options for the LLM Inference task
    val taskOptions = LlmInferenceOptions.builder()
            .setModelPath('/data/local/tmp/llm/<var translate="no"><span class="devsite-syntax-n">model_version</span></var>.task')
            .setMaxTopK(64)
            .build()

    // Create an instance of the LLM Inference task
    llmInference = LlmInference.createFromOptions(context, taskOptions)

### Run the Task

Use the `generateResponse()` method to generate a text response. This produces a
single generated response.  

    val result = llmInference.generateResponse(inputPrompt)
    logger.atInfo().log("result: $result")

To stream the response, use the `generateResponseAsync()` method.  

    val options = LlmInference.LlmInferenceOptions.builder()
      ...
      .setResultListener { partialResult, done ->
        logger.atInfo().log("partial result: $partialResult")
      }
      .build()

    llmInference.generateResponseAsync(inputPrompt)

## Sample application

| **Note:** The Google AI Edge Gallery is an Alpha release.

To see the LLM Inference APIs in action and explore a comprehensive range of
on-device Generative AI capabilities, check out the [Google AI Edge Gallery
app](https://github.com/google-ai-edge/gallery).

The Google AI Edge Gallery is an open-source Android application that serves as
an interactive playground for developers. It showcases:

- Practical examples of using the LLM Inference API for various tasks, including:
  - Ask Image: Upload an image and ask questions about it. Get descriptions, solve problems, or identify objects.
  - Prompt Lab: Summarize, rewrite, generate code, or use freeform prompts to explore single-turn LLM use cases.
  - AI Chat: Engage in multi-turn conversations.
- The ability to discover, download, and experiment with a variety of LiteRT-optimized models from the Hugging Face LiteRT Community and official Google releases (e.g. Gemma 3N).
- Real-time on-device performance benchmarks for different models (Time To First Token, decode speed, etc.).
- How to import and test your own custom `.litertlm` or `.task` models.

This app is a resource to understand the practical implementation of the LLM
Inference API and the potential of on-device Generative AI. Explore the source
code and download the app from the[Google AI Edge Gallery GitHub
repository](https://github.com/google-ai-edge/gallery).

## Configuration options

Use the following configuration options to set up an Android app:

|   Option Name    |                                                                                          Description                                                                                          | Value Range | Default Value |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|---------------|
| `modelPath`      | The path to where the model is stored within the project directory.                                                                                                                           | PATH        | N/A           |
| `maxTokens`      | The maximum number of tokens (input tokens + output tokens) the model handles.                                                                                                                | Integer     | 512           |
| `topK`           | The number of tokens the model considers at each step of generation. Limits predictions to the top k most-probable tokens.                                                                    | Integer     | 40            |
| `temperature`    | The amount of randomness introduced during generation. A higher temperature results in more creativity in the generated text, while a lower temperature produces more predictable generation. | Float       | 0.8           |
| `randomSeed`     | The random seed used during text generation.                                                                                                                                                  | Integer     | 0             |
| `loraPath`       | The absolute path to the LoRA model locally on the device. Note: this is only compatible with GPU models.                                                                                     | PATH        | N/A           |
| `resultListener` | Sets the result listener to receive the results asynchronously. Only applicable when using the async generation method.                                                                       | N/A         | N/A           |
| `errorListener`  | Sets an optional error listener.                                                                                                                                                              | N/A         | N/A           |

## Multimodal prompting

The LLM Inference API Android APIs support multimodal prompting with models that
accept text, image, and audio inputs. With multimodality enabled, users can
include a combination of images and text or audio and text in their prompts.The
LLM then provides a text response.

To get started, use a MediaPipe-compatible variant of [Gemma
3n](https://ai.google.dev/gemma/docs/gemma-3n):

- [Gemma-3n
  E2B](https://huggingface.co/google/gemma-3n-E2B-it-litert-preview): an effective 2B model of the Gemma-3n family.
- [Gemma-3n
  E4B](https://huggingface.co/google/gemma-3n-E4B-it-litert-preview): an effective 4B model of the Gemma-3n family.

For more information, see the [Gemma-3n
documentation](https://ai.google.dev/gemma/docs/gemma-3n).

Follow the steps below to enable image or audio input for LLM Inference API.

### Image input

To provide images within a prompt, convert the input images or frames to a
`com.google.mediapipe.framework.image.MPImage` object before passing it to the
LLM Inference API:  

    import com.google.mediapipe.framework.image.BitmapImageBuilder
    import com.google.mediapipe.framework.image.MPImage

    // Convert the input Bitmap object to an MPImage object to run inference
    val mpImage = BitmapImageBuilder(image).build()

To enable vision support for the LLM Inference API, set the `EnableVisionModality`
configuration option to `true` within the Graph options:  

    LlmInferenceSession.LlmInferenceSessionOptions sessionOptions =
      LlmInferenceSession.LlmInferenceSessionOptions.builder()
        ...
        .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
        .build();

Set the maximum of 10 images per session.  

    LlmInferenceOptions options = LlmInferenceOptions.builder()
      ...
      .setMaxNumImages(10)
      .build();

The following is an example implementation of the LLM Inference API set up to handle
vision and text inputs:  

    MPImage image = getImageFromAsset(BURGER_IMAGE);

    LlmInferenceSession.LlmInferenceSessionOptions sessionOptions =
      LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTopK(10)
        .setTemperature(0.4f)
        .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
        .build();

    try (LlmInference llmInference =
        LlmInference.createFromOptions(ApplicationProvider.getApplicationContext(), options);
      LlmInferenceSession session =
        LlmInferenceSession.createFromOptions(llmInference, sessionOptions)) {
      session.addQueryChunk("Describe the objects in the image.");
      session.addImage(image);
      String result = session.generateResponse();
    }

### Audio input

Enable audio support in LlmInferenceOptions  

    val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
      ...
      .setAudioModelOptions(AudioModelOptions.builder().build())
      .build()

Enable Audio support in sessionOptions  

        val sessionOptions =  LlmInferenceSessionOptions.builder()
          ...
          .setGraphOptions(GraphOptions.builder().setEnableAudioModality(true).build())
          .build()

Send audio data during inference.
Note: Audio must be mono channel formatted as .wav  


    val audioData: ByteArray = ...
    inferenceEngine.llmInferenceSession.addAudio(audioData)

The following is an example implementation of the LLM Inference API set up to handle
audio and text inputs:  

    val audioData: ByteArray = ...
    val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
      ...
      .setAudioModelOptions(AudioModelOptions.builder().build())
      .build()
    val sessionOptions =  LlmInferenceSessionOptions.builder()
      ...
      .setGraphOptions(GraphOptions.builder().setEnableAudioModality(true).build())
      .build()

    LlmInference.createFromOptions(context, inferenceOptions).use { llmInference ->
      LlmInferenceSession.createFromOptions(llmInference, sessionOptions).use { session ->
        session.addQueryChunk("Transcribe the following speech segment:")
        session.addAudio(audioData)
        val result = session.generateResponse()
      }
    }

## LoRA customization

The LLM Inference API supports LoRA (Low-Rank Adaptation) tuning using the
[PEFT](https://huggingface.co/docs/peft/main/en/index) (Parameter-Efficient
Fine-Tuning) library. LoRA tuning customizes the behavior of LLMs through a
cost-effective training process, creating a small set of trainable weights based
on new training data rather than retraining the entire model.

The LLM Inference API supports adding LoRA weights to attention layers of the
[Gemma-2 2B](https://huggingface.co/google/gemma-2-2b), [Gemma
2B](https://huggingface.co/google/gemma-2b) and
[Phi-2](https://huggingface.co/microsoft/phi-2) models. Download the model in
the `safetensors` format.

The base model must be in the `safetensors` format in order to create LoRA
weights. After LoRA training, you can convert the models into the FlatBuffers
format to run on MediaPipe.

### Prepare LoRA weights

Use the [LoRA
Methods](https://huggingface.co/docs/peft/main/en/task_guides/lora_based_methods)
guide from PEFT to train a fine-tuned LoRA model on your own dataset.

The LLM Inference API only supports LoRA on attention layers, so only specify the
attention layers in `LoraConfig`:  

    # For Gemma
    from peft import LoraConfig
    config = LoraConfig(
        r=LORA_RANK,
        target_modules=["q_proj", "v_proj", "k_proj", "o_proj"],
    )

    # For Phi-2
    config = LoraConfig(
        r=LORA_RANK,
        target_modules=["q_proj", "v_proj", "k_proj", "dense"],
    )

After training on the prepared dataset and saving the model, the fine-tuned LoRA
model weights are available in `adapter_model.safetensors`. The `safetensors`
file is the LoRA checkpoint used during model conversion.

### Model conversion

Use the MediaPipe Python Package to convert the model weights into the
Flatbuffer format. The `ConversionConfig` specifies the base model options along
with the additional LoRA options.
**Note:** Since the API only supports LoRA inference with GPU, the backend must be set to `'gpu'`.  

    import mediapipe as mp
    from mediapipe.tasks.python.genai import converter

    config = converter.ConversionConfig(
      # Other params related to base model
      ...
      # Must use gpu backend for LoRA conversion
      backend='gpu',
      # LoRA related params
      lora_ckpt=<var translate="no"><span class="devsite-syntax-n">LORA_CKPT</span></var>,
      lora_rank=<var translate="no"><span class="devsite-syntax-n">LORA_RANK</span></var>,
      lora_output_tflite_file=<var translate="no"><span class="devsite-syntax-n">LORA_OUTPUT_FILE</span></var>,
    )

    converter.convert_checkpoint(config)

The converter will produce two Flatbuffer files, one for the base model and
another for the LoRA model.

### LoRA model inference

Android supports static LoRA during initialization. To load a LoRA model,
specify the LoRA model path as well as the base LLM.  

    // Set the configuration options for the LLM Inference task
    val options = LlmInferenceOptions.builder()
            .setModelPath(<var translate="no"><span class="devsite-syntax-n">BASE_MODEL_PATH</span></var>)
            .setMaxTokens(1000)
            .setTopK(40)
            .setTemperature(0.8)
            .setRandomSeed(101)
            .setLoraPath(<var translate="no"><span class="devsite-syntax-n">LORA_MODEL_PATH</span></var>)
            .build()

    // Create an instance of the LLM Inference task
    llmInference = LlmInference.createFromOptions(context, options)

To run LLM inference with LoRA, use the same `generateResponse()` or
`generateResponseAsync()` methods as the base model.



