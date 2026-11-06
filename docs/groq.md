# Groq Java SDK
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/DebajitKumarPhukan/groq-java-sdk) ![Maven Central Version](https://img.shields.io/maven-central/v/io.github.debajitkumarphukan/groq-java-sdk) ![Coverage](.github/badges/jacoco.svg)

A modern, type-safe Java SDK for interacting with the Groq API, providing seamless access to large language models with ultra-low latency inference.

## Overview

The Groq Java SDK is a comprehensive client library that enables Java developers to easily integrate with Groq's high-performance inference engine. It provides type-safe interfaces for all Groq API endpoints including chat completions, embeddings, audio processing, batch operations, file management, Response API, and MCP (Model Context Protocol) operations.

### Key Features

- 🚀 **High Performance**: Optimized for Groq's lightning-fast inference
- 🛡️ **Type-Safe**: Full type safety with comprehensive Java models
- 🔧 **Flexible Configuration**: Customizable timeouts, retries, and headers
- 🎯 **Comprehensive Coverage**: Support for all Groq API endpoints
- 🛠️ **Production Ready**: Built-in error handling, logging, and retry mechanisms
- 📦 **Zero Dependencies**: Minimal external dependencies
- 🧠 **Advanced Reasoning**: Response API with reasoning capabilities
- 🔌 **MCP Tool Integration**: MCP protocol for external tool integration

## Quick Start

### Installation

#### Maven Dependency

```xml
<dependency>
    <groupId>io.github.debajitkumarphukan</groupId>
    <artifactId>groq-java-sdk</artifactId>
    <version>1.0.4</version>
</dependency>
```
#### Gradle Dependency

```xml
implementation group: 'io.github.debajitkumarphukan', name: 'groq-java-sdk', version: '1.0.4'
```
### Basic Usage

```java
import com.groq.sdk.client.GroqClient;
import com.groq.sdk.models.chat.ChatCompletionRequest;
import com.groq.sdk.models.chat.ChatMessage;
import java.util.List;

// Initialize client
GroqClient client = GroqClient.builder()
    .apiKey("your-api-key-here")
    .timeout(java.time.Duration.ofSeconds(30))
    .maxRetries(3)
    .build();

// Create chat completion
ChatMessage message = new ChatMessage("user", "Explain quantum computing in simple terms");
ChatCompletionRequest request = new ChatCompletionRequest("openai/gpt-oss-20b", List.of(message));
request.setMaxTokens(100);
request.setTemperature(0.7);

var response = client.chat().createCompletion(request);

if (response.isSuccessful()) {
    String content = response.getData().getChoices().get(0).getMessage().getContent();
    System.out.println("Response: " + content);
}
```
### Advanced Examples
#### Chat Completions with Multiple Models
```java
// Try multiple models with fallback
String[] models = {"openai/gpt-oss-20b", "llama-3.1-8b-instant", "qwen/qwen3-32b"};

for (String model : models) {
    try {
        ChatCompletionRequest request = new ChatCompletionRequest(model, messages);
        var response = client.chat().createCompletion(request);
        
        if (response.isSuccessful()) {
            // Process successful response
            break;
        }
    } catch (Exception e) {
        // Fallback to next model
        continue;
    }
}
```
#### Tool Calls and Function Calling
```java
import com.groq.sdk.models.chat.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// Create function definitions
FunctionDefinition weatherFunction = new FunctionDefinition(
    "get_weather",
    "Get the current weather for a location",
    Map.of(
        "type", "object",
        "properties", Map.of(
            "location", Map.of("type", "string", "description", "The city and state"),
            "unit", Map.of("type", "string", "enum", Arrays.asList("celsius", "fahrenheit"))
        ),
        "required", Arrays.asList("location")
    )
);

// Create tools
ChatTool weatherTool = new ChatTool("function", weatherFunction);
ChatTool calculatorTool = new ChatTool("function", createCalculatorFunction());

// Create request with tools
ChatMessage message = new ChatMessage("user", "What's the weather in Tokyo and calculate 15 * 8?");
ChatCompletionRequest request = new ChatCompletionRequest("llama-3.3-70b-versatile", Arrays.asList(message));
request.setTools(Arrays.asList(weatherTool, calculatorTool));
request.setToolChoice("auto"); // Let model choose which tools to use

GroqResponse<ChatCompletion> response = client.chat().createCompletion(request);

if (response.isSuccessful()) {
    ChatMessage assistantMessage = response.getData().getChoices().get(0).getMessage();
    
    if (assistantMessage.getToolCalls() != null) {
        // Handle tool calls
        for (ChatToolCall toolCall : assistantMessage.getToolCalls()) {
            String toolName = toolCall.getFunction().getName();
            String arguments = toolCall.getFunction().getArguments();
            
            // Execute the tool and get result
            String toolResult = executeTool(toolName, arguments);
            
            // Continue conversation with tool result
            ChatMessage toolMessage = ChatMessage.createToolMessage(toolCall.getId(), toolResult);
            // ... continue conversation
        }
    } else {
        // Direct response
        String content = assistantMessage.getContent();
        System.out.println("Response: " + content);
    }
}
```
#### Forced Tool Usage
```java
// Force the model to use a specific tool
ChatNamedToolChoice forcedChoice = new ChatNamedToolChoice("calculate");
request.setToolChoice(forcedChoice); // Must use the calculator tool
```
#### Response API with Reasoning
```java
import com.groq.sdk.models.responses.ResponseRequest;
import com.groq.sdk.models.responses.ReasoningConfig;

// Create response with reasoning
ResponseRequest request = new ResponseRequest("openai/gpt-oss-20b", "How are AI models trained? Be brief.");
request.setReasoning(new ReasoningConfig("low")); // low, medium, or high effort
request.setTemperature(1.0);
request.setMaxOutputTokens(500);

GroqResponse<Response> response = client.responses().create(request);

if (response.isSuccessful()) {
    Response resp = response.getData();
    
    // Process different output types
    for (ResponseOutput output : resp.getOutput()) {
        if (output instanceof ReasoningOutput) {
            ReasoningOutput reasoning = (ReasoningOutput) output;
            System.out.println("Reasoning: " + reasoning.getContent().get(0).getText());
        } else if (output instanceof MessageOutput) {
            MessageOutput message = (MessageOutput) output;
            System.out.println("Final Response: " + message.getContent().get(0).getText());
        }
    }
}
```
#### Response API with Code Interpreter and Browser Search
```java
import com.groq.sdk.models.chat.ChatTool;

// Create code interpreter tool
ChatTool codeInterpreter = new ChatTool();
codeInterpreter.setType("code_interpreter");

// Create browser search tool  
ChatTool browserSearch = new ChatTool();
browserSearch.setType("browser_search");

// Create request with multiple tools
ResponseRequest request = new ResponseRequest("openai/gpt-oss-20b", 
    "What's the current weather in San Francisco and calculate the area of a circle with radius 5?");
request.setToolChoice("required");
request.setChatTools(Arrays.asList(codeInterpreter, browserSearch));

GroqResponse<Response> response = client.responses().create(request);
```
#### MCP Operations with External Tools
```java
import com.groq.sdk.models.mcp.MCPToolDefinition;

// Create MCP tool definition
MCPToolDefinition firecrawlTool = new MCPToolDefinition(
    "firecrawl",
    "Web scraping and content extraction",
    "https://mcp.firecrawl.dev/<APIKEY>/v2/mcp",
    "never"
);

// Add authentication headers if needed
firecrawlTool.setBearerToken("your-firecrawl-token");

// Create MCP response
GroqResponse<Response> response = client.mcp().createResponse(
    "openai/gpt-oss-120b",
    "What are the latest AI research papers?",
    firecrawlTool
);

if (response.isSuccessful()) {
    Response resp = response.getData();
    
    // Process MCP outputs
    for (ResponseOutput output : resp.getOutput()) {
        if (output instanceof MCPListToolsOutput) {
            MCPListToolsOutput toolsOutput = (MCPListToolsOutput) output;
            System.out.println("Available tools: " + toolsOutput.getTools().size());
        } else if (output instanceof MCPCallOutput) {
            MCPCallOutput callOutput = (MCPCallOutput) output;
            System.out.println("Tool call: " + callOutput.getName());
            System.out.println("Output: " + callOutput.getOutput());
        } else if (output instanceof MessageOutput) {
            MessageOutput message = (MessageOutput) output;
            System.out.println("Final response: " + message.getContent().get(0).getText());
        }
    }
}
```
#### Embeddings Generation [Preview]
```java
import com.groq.sdk.models.embeddings.EmbeddingRequest;
import java.util.Arrays;
import java.util.List;

List<String> inputs = Arrays.asList(
    "The weather is nice today",
    "It's sunny outside",
    "I enjoy programming in Java"
);

EmbeddingRequest request = new EmbeddingRequest("text-embedding-ada-002", inputs);
var response = client.embeddings().create(request);

if (response.isSuccessful()) {
    System.out.println("Generated " + response.getData().getData().size() + " embeddings");
}
```
#### Audio Processing - Text-to-Speech
```java
import com.groq.sdk.models.audio.SpeechRequest;
import com.groq.sdk.models.audio.SpeechResponse;

// Text-to-Speech with PlayAI voices
SpeechRequest speechRequest = new SpeechRequest();
speechRequest.setModel("playai-tts");
speechRequest.setInput("Hello! This is a demonstration of Groq's text to speech capabilities.");
speechRequest.setVoice("Fritz-PlayAI");
speechRequest.setResponseFormat("mp3");
speechRequest.setSpeed(1.2); // Faster than normal

GroqResponse<SpeechResponse> response = client.audio().createSpeech(speechRequest);

if (response.isSuccessful()) {
    SpeechResponse speechResponse = response.getData();
    byte[] audioData = speechResponse.getAudio();
    
    // Save audio to file
    String filename = "greeting_" + System.currentTimeMillis() + ".mp3";
    Path filePath = Paths.get("output", filename);
    Files.write(filePath, audioData);
    System.out.println("Audio saved to: " + filePath.toAbsolutePath());
}
```
#### Audio Processing - Speech-to-Text Transcription
```java
import com.groq.sdk.models.audio.TranscriptionRequest;
import com.groq.sdk.models.audio.Transcription;

// Audio Transcription
TranscriptionRequest transcriptionRequest = new TranscriptionRequest();
transcriptionRequest.setModel("whisper-large-v3-turbo");
transcriptionRequest.setFile("path/to/audio/file.mp3");
transcriptionRequest.setLanguage("en");
transcriptionRequest.setResponseFormat("verbose_json");
transcriptionRequest.setTemperature(0.0);

GroqResponse<Transcription> response = client.audio().createTranscription(transcriptionRequest);

if (response.isSuccessful()) {
    Transcription transcription = response.getData();
    System.out.println("Transcribed text: " + transcription.getText());
    System.out.println("Audio duration: " + transcription.getDuration() + " seconds");
    
    if (transcription.getSegments() != null) {
        System.out.println("Segments: " + transcription.getSegments().size());
    }
}
```
#### Audio Processing - Translation
```java
import com.groq.sdk.models.audio.TranslationRequest;
import com.groq.sdk.models.audio.Translation;

// Audio Translation (convert audio to English text)
TranslationRequest translationRequest = new TranslationRequest();
translationRequest.setModel("whisper-large-v3");
translationRequest.setFile("path/to/spanish/audio.mp3");
translationRequest.setLanguage("es");
translationRequest.setPrompt("Translate this audio content from Spanish to English");
translationRequest.setResponseFormat("verbose_json");

GroqResponse<Translation> response = client.audio().createTranslation(translationRequest);

if (response.isSuccessful()) {
    Translation translation = response.getData();
    System.out.println("Translated text: " + translation.getText());
    System.out.println("Detected language: " + translation.getLanguage());
}
```
#### Vision Analysis
```java
import com.groq.sdk.models.vision.VisionRequest;
import com.groq.sdk.models.vision.VisionMessage;
import com.groq.sdk.models.vision.VisionContentPart;

// Vision analysis with remote image URL
String imageUrl = "https://example.com/image.jpg";
String prompt = "What's in this image? Describe it in detail.";

VisionRequest request = client.vision().createVisionRequestWithUrl(
    "llava-v1.5-7b-4096-preview", 
    imageUrl, 
    prompt
);
request.setMaxTokens(500);
request.setTemperature(0.1);

GroqResponse<ChatCompletion> response = client.vision().createCompletion(request);

if (response.isSuccessful()) {
    String analysis = response.getData().getChoices().get(0).getMessage().getContent();
    System.out.println("Image analysis: " + analysis);
}

// Vision analysis with local image
Path localImagePath = Paths.get("src/main/resources/images/local_image.jpg");
VisionRequest localRequest = client.vision().createVisionRequestWithLocalImage(
    "llava-v1.5-7b-4096-preview",
    localImagePath.toString(),
    "What text is visible in this image?"
);

// Vision analysis with image bytes
byte[] imageBytes = Files.readAllBytes(localImagePath);
VisionRequest bytesRequest = client.vision().createVisionRequestWithImageBytes(
    "llava-v1.5-7b-4096-preview",
    imageBytes,
    "image/jpeg",
    "Analyze this image and describe what you see"
);
```

### Project Structure
```java
groq-java-sdk/
├── src/main/java/com/groq/sdk/
│   ├── client/
│   │   └── GroqClient.java          # Main client class and entry point
│   ├── core/
│   │   └── BaseClient.java          # Base HTTP client with retry logic
│   ├── models/                      # Data models organized by feature
│   │   ├── chat/                    # Chat completion models
│   │   │   ├── ChatCompletion.java
│   │   │   ├── ChatCompletionRequest.java
│   │   │   ├── ChatMessage.java
│   │   │   ├── ChatChoice.java
│   │   │   ├── ChatTool.java        # Tool definitions
│   │   │   ├── ChatToolCall.java    # Tool call execution
│   │   │   ├── ChatNamedToolChoice.java # Forced tool usage
│   │   │   ├── FunctionDefinition.java # Function schemas
│   │   │   └── Usage.java
│   │   ├── embeddings/              # Embedding models
│   │   │   ├── EmbeddingRequest.java
│   │   │   ├── EmbeddingResponse.java
│   │   │   └── EmbeddingData.java
│   │   ├── audio/                   # Audio processing models
│   │   │   ├── SpeechRequest.java
│   │   │   ├── SpeechResponse.java
│   │   │   ├── TranscriptionRequest.java
│   │   │   ├── Transcription.java
│   │   │   ├── TranslationRequest.java
│   │   │   ├── Translation.java
│   │   │   ├── Segment.java
│   │   │   └── GroqMetadata.java
│   │   ├── vision/                  # Vision processing models
│   │   │   ├── VisionRequest.java
│   │   │   ├── VisionResponse.java
│   │   │   ├── VisionMessage.java
│   │   │   ├── VisionChoice.java
│   │   │   ├── VisionContentPart.java
│   │   │   └── VisionImageUrl.java
│   │   ├── batches/                 # Batch processing models
│   │   │   ├── Batch.java
│   │   │   ├── BatchList.java
│   │   │   ├── BatchCreateRequest.java
│   │   │   └── BatchRequestCounts.java
│   │   ├── files/                   # File management models
│   │   │   ├── FileObject.java
│   │   │   ├── FileList.java
│   │   │   ├── FileUploadRequest.java
│   │   │   ├── FileDeleteResponse.java
│   │   │   └── FilePart.java
│   │   ├── models/                  # Model management
│   │   │   ├── Model.java
│   │   │   └── ModelList.java
│   │   ├── responses/               # Response API models
│   │   │   ├── Response.java
│   │   │   ├── ResponseRequest.java
│   │   │   ├── ResponseOutput.java
│   │   │   ├── ReasoningOutput.java
│   │   │   ├── ReasoningConfig.java
│   │   │   ├── ReasoningContent.java
│   │   │   ├── MessageOutput.java
│   │   │   ├── MessageContent.java
│   │   │   ├── MessageInput.java
│   │   │   ├── Usage.java
│   │   │   └── UsageDetails.java
│   │   ├── mcp/                     # MCP models
│   │   │   ├── MCPToolDefinition.java
│   │   │   ├── MCPTool.java
│   │   │   ├── MCPListToolsOutput.java
│   │   │   ├── MCPCallOutput.java
│   │   │   └── ReasoningOutput.java
│   │   └── GroqResponse.java        # Generic API response wrapper
│   ├── resources/                   # API resource classes
│   │   ├── ChatResource.java        # Chat completion operations
│   │   ├── EmbeddingsResource.java  # Embedding operations
│   │   ├── AudioResource.java       # Audio operations
│   │   ├── VisionResource.java      # Vision operations
│   │   ├── BatchesResource.java     # Batch operations
│   │   ├── FilesResource.java       # File operations
│   │   ├── ModelsResource.java      # Model operations
│   │   ├── ResponseResource.java    # Response API operations
│   │   └── MCPResource.java         # MCP operations
│   ├── exceptions/
│   │   └── GroqException.java       # Custom exception types
│   └── util/
│       └── JsonUtils.java           # JSON serialization utilities
├── examples/
│   └── Example.java                 # Comprehensive usage examples
└── pom.xml                          # Maven build configuration
```
### API Resources
#### Available Resources
* `client.chat()` - Chat completions and conversations with tool calling support

* `client.embeddings()` - Text embedding generation

* `client.audio()` - Speech synthesis and transcription

* `client.vision()` - Image analysis and multimodal understanding

* `client.batches()` - Batch processing operations

* `client.files()` - File upload and management

* `client.models()` - Model information and listing

* `client.responses()` - Response API with reasoning, code interpreter, and browser search

* `client.mcp()` - MCP operations with external tool integration

#### Audio Features
* **Text-to-Speech (TTS):** Convert text to natural-sounding speech using PlayAI voices

* **Speech-to-Text:** Transcribe audio to text with Whisper models

* **Audio Translation:** Convert audio in any language to English text

* **Multiple Formats:** Support for MP3, WAV, FLAC, and other audio formats

* **Voice Selection:** 25+ high-quality PlayAI voices with different accents and styles

* **Speed Control:** Adjust speech speed from 0.25x to 4.0x normal speed

#### Vision Features
* **Multimodal Analysis:** Combine text and images in single requests

* **Multiple Input Types:** Support for remote URLs, local files, and image bytes

* **Detailed Descriptions:** Get comprehensive analysis of image content

* **Text Extraction:** Read and interpret text within images

* **Object Recognition:** Identify objects, scenes, and activities in images

#### Response API Features
* **Advanced Reasoning:** Show step-by-step reasoning with configurable effort levels

* **Code Interpreter:** Some models and systems on Groq have native support for automatic code execution, allowing them to perform calculations, run code snippets, and solve computational problems in real-time

* **Browser Search:** Some models on Groq have built-in support for interactive browser search, providing a more comprehensive approach to accessing real-time web content than traditional web search. Search the web for real-time information

* **Multiple Output Types:** Handle reasoning, message, and tool outputs in single response

* **Complex Conversations:** Support for multi-message inputs with system prompts

#### MCP Tool Calling Features
* **External Tool Integration:** Connect with external services via MCP protocol

* **Tool Discovery:** Automatic discovery of available tools from MCP servers

* **Authentication Support:** Bearer tokens, API keys, and custom headers

* **Multiple Tools:** Support for multiple MCP tools in single request

* **Tool Output Processing:** Structured handling of tool call results

#### Traditional Tool Calling Features
* **Automatic Tool Selection:** Model chooses which tools to use based on context

* **Parallel Tool Calls:** Multiple tools can be called simultaneously

* **Function Definitions:** JSON Schema based function definitions

#### Configuration Options
```java
GroqClient client = GroqClient.builder()
    .apiKey("your-api-key")          // Required: Your Groq API key
    .baseUrl("https://api.groq.com") // Optional: Custom base URL
    .timeout(Duration.ofSeconds(30)) // Optional: Request timeout
    .maxRetries(3)                   // Optional: Maximum retry attempts
    .defaultHeader("X-Custom", "value") // Optional: Custom headers
    .build();
```
    
#### Error Handling
The SDK provides comprehensive error handling:

```java
try {
    var response = client.chat().createCompletion(request);
    if (response.isSuccessful()) {
        // Process successful response
    } else {
        System.err.println("API Error: " + response.getStatusCode());
    }
} catch (GroqException e) {
    System.err.println("Groq API Error: " + e.getMessage());
    System.err.println("Status Code: " + e.getStatusCode());
} catch (Exception e) {
    System.err.println("Unexpected error: " + e.getMessage());
}
```
### Supported Audio Models
#### Text-to-Speech Models
* `playai-tts` - High-quality PlayAI TTS model

#### Speech-to-Text Models
* `whisper-large-v3-turbo` - Latest Whisper model for transcription

* `whisper-large-v3` - Latest Whisper model for translation

#### Supported Voices
* `Aaliyah-PlayAI, Adelaide-PlayAI, Angelo-PlayAI, Arista-PlayAI, Atlas-PlayAI`

* `Basil-PlayAI, Briggs-PlayAI, Calum-PlayAI, Celeste-PlayAI, Cheyenne-PlayAI`

* `Chip-PlayAI, Cillian-PlayAI, Deedee-PlayAI, Eleanor-PlayAI, Fritz-PlayAI`

* `Gail-PlayAI, Indigo-PlayAI, Jennifer-PlayAI, Juan-PlayAI, Judy-PlayAI`

* `Mamaw-PlayAI, Mason-PlayAI, Mikail-PlayAI, Mitch-PlayAI, Nia-PlayAI`

* `Quinn-PlayAI, Ruby-PlayAI, Thunder-PlayAI`

#### Supported Vision Models
* `meta-llama/llama-4-maverick-17b-128e-instruct` - High-performance vision model</li>
* `meta-llama/llama-4-scout-17b-16e-instruct` - Efficient vision model</li>

### Contributing
We welcome contributions from the community! Here's how you can help:

**1. Fork the repository**
```bash
git clone https://github.com/your-username/groq-java-sdk.git
cd groq-java-sdk
```
**2. Build the project**
```bash
mvn clean compile
```
**3. Run tests**
```bash
mvn test
```
### Contribution Guidelines
##### 🐛 Reporting Bugs
* Open an issue with the "[ISSUE]" at the beginning of the subject line

* Use the GitHub issue tracker

* Include detailed reproduction steps

* Provide code examples and error logs

* Specify your Java version and environment

##### 💡 Feature Requests
* Open an issue with the "[ENHANCEMENT]" at the beginning of the subject line

* Describe the use case and expected behavior

* Consider if it aligns with the SDK's scope

##### 🔧 Code Contributions
* Follow the code style

* Use 4-space indentation
 
* Follow Java naming conventions
 
* Include Javadoc for public methods

* Write tests

* Add unit tests for new features

* Ensure all tests pass

* Maintain test coverage

* Update documentation

* Update README.md for new features

* Add Javadoc comments

* Include usage examples

* Submit Pull Request

* Create a descriptive PR title

* Link related issues

* Provide clear implementation details

##### 📝 Code Review Process
* All PRs require review from maintainers

* Address review comments promptly

* Ensure CI checks pass

* Squash commits before merging

#### Development Dependencies
* Java 21 or higher

* Maven 3.6+

* Groq API key (for integration tests)

#### Running the Demo
```bash
export GROQ_API_KEY="your-api-key"
mvn compile exec:java -Dexec.mainClass="com.groq.sdk.examples.Example"
```
#### License
This project is licensed under the Apache 2.0 License - see the [LICENSE](https://github.com/DebajitKumarPhukan/groq-java-sdk/blob/master/LICENSE.txt) file for details.

#### Support
📚 API Documentation

🐛 Report Issues

💬 Community Discussions

#### Author
Project led and maintained by Debajit Kumar Phukan