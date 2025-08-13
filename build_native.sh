#!/bin/bash

# 咳嗽检测器 C++ 引擎构建脚本
# 此脚本帮助构建 C++ 原生库

set -e

echo "=== 咳嗽检测器 C++ 引擎构建脚本 ==="
echo ""

# 检查 Android SDK 和 NDK
echo "检查 Android SDK 和 NDK..."

if [ -z "$ANDROID_HOME" ]; then
    echo "错误: 未设置 ANDROID_HOME 环境变量"
    echo "请设置 Android SDK 路径，例如:"
    echo "export ANDROID_HOME=/path/to/android/sdk"
    exit 1
fi

if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "错误: 未设置 ANDROID_NDK_HOME 环境变量"
    echo "请设置 Android NDK 路径，例如:"
    echo "export ANDROID_NDK_HOME=/path/to/android/ndk"
    exit 1
fi

echo "✓ Android SDK: $ANDROID_HOME"
echo "✓ Android NDK: $ANDROID_NDK_HOME"
echo ""

# 检查必要的工具
echo "检查构建工具..."

if ! command -v cmake &> /dev/null; then
    echo "错误: 未找到 cmake"
    echo "请安装 CMake 3.22.1 或更高版本"
    exit 1
fi

if ! command -v ninja &> /dev/null; then
    echo "警告: 未找到 ninja，将使用 make"
fi

echo "✓ CMake: $(cmake --version | head -n1)"
echo ""

# 设置构建参数
BUILD_DIR="app/build/native"
CMAKE_BUILD_TYPE="Release"
ANDROID_ABI="arm64-v8a"
ANDROID_PLATFORM="android-24"

echo "构建配置:"
echo "- 构建目录: $BUILD_DIR"
echo "- 构建类型: $CMAKE_BUILD_TYPE"
echo "- 目标架构: $ANDROID_ABI"
echo "- 目标平台: $ANDROID_PLATFORM"
echo ""

# 创建构建目录
echo "创建构建目录..."
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# 配置 CMake
echo "配置 CMake..."
cmake \
    -DCMAKE_BUILD_TYPE="$CMAKE_BUILD_TYPE" \
    -DANDROID_ABI="$ANDROID_ABI" \
    -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
    -DANDROID_NDK="$ANDROID_NDK_HOME" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_STL=c++_shared \
    -DCMAKE_CXX_STANDARD=17 \
    -DCMAKE_CXX_STANDARD_REQUIRED=ON \
    ../../../src/main/cpp

# 构建项目
echo "构建项目..."
if command -v ninja &> /dev/null; then
    cmake --build . --config "$CMAKE_BUILD_TYPE" --parallel
else
    cmake --build . --config "$CMAKE_BUILD_TYPE" --parallel -- -j$(nproc)
fi

echo ""
echo "=== 构建完成 ==="
echo ""

# 检查构建结果
if [ -f "libcoughdetect.so" ]; then
    echo "✓ 成功构建 libcoughdetect.so"
    echo "文件大小: $(ls -lh libcoughdetect.so | awk '{print $5}')"
else
    echo "✗ 构建失败: 未找到 libcoughdetect.so"
    exit 1
fi

echo ""
echo "下一步:"
echo "1. 在 Android Studio 中同步项目"
echo "2. 构建并运行应用程序"
echo "3. 检查 logcat 输出以验证 C++ 引擎是否正常工作"
echo ""
echo "如果遇到问题，请检查:"
echo "- NDK 版本是否为 25.2.9519653 或更高"
echo "- CMake 版本是否为 3.22.1 或更高"
echo "- 是否正确设置了 ANDROID_HOME 和 ANDROID_NDK_HOME"
echo "- 项目依赖是否正确配置" 