#include <jni.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_example_NativeBridge_openFile(JNIEnv* env, jobject thiz, jstring path, jint flags) {
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_NativeBridge_signatureMismatch(JNIEnv* env, jobject thiz, jstring path) {
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_NativeBridge_protectedFallback(JNIEnv* env, jobject thiz, jstring value) {
    return 0;
}
