# llama.cpp for Android

This project is dedicated to exploring high-performance large language model capabilities on mobile devices, based on the [llama.cpp](https://github.com/ggerganov/llama.cpp) project.

## Demo

[![Demo Video](https://img.youtube.com/vi/0Jis5UVzRwk/0.jpg)](https://www.youtube.com/shorts/0Jis5UVzRwk)

## Features

- Run LLMs directly on Android devices
- Native C++ implementation using Android NDK
- Inherits llama.cpp's efficient model inference
- Context compression for long text processing:
  - Support longer text with smaller memory footprint
  - Efficient context management for extended conversations
- Simple Android UI for interaction

## Known Issues and Future Plans

- Model list management (including model descriptions, download links, etc.) is currently hardcoded in the source code. This will be improved in future updates with a more flexible and maintainable solution.
- Future improvements planned:
  - Better model management system
  - Performance optimizations for mobile scenarios

## Prerequisites

- Android Studio Arctic Fox (2020.3.1) or newer
- CMake 3.18.1 or newer
- Android NDK 21.4.7075529 or newer
- Android SDK Platform 21 or newer

## Building

1. Clone the repository:
```bash
git clone https://github.com/linkaisheng/llama.cpp.android.git
cd llama.cpp.android
```

2. Open the project in Android Studio

3. Sync project with Gradle files

4. Build the project using Android Studio or run:
```bash
./gradlew assembleDebug
```

## License

This project is licensed under the same terms as llama.cpp - MIT License. See the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Original llama.cpp project
- All contributors to the llama.cpp project

## Disclaimer

This is an unofficial port of llama.cpp to Android. Please ensure you comply with the model's license terms and usage restrictions.
