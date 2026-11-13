#include <jni.h>

static JNINativeMethod methods[] = {
    {"dynamicEcho", "(Ljava/lang/String;)Ljava/lang/String;", (void*)dynamic_echo},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = 0;
    jclass target = 0;
    return env->RegisterNatives(target, methods, 1);
}
