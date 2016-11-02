#include <string.h>
#include <jni.h>
#include "JNIHelp.h"
#include <stdlib.h>
#include <utils/Log.h>
#include "android_runtime/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <sys/resource.h>
#include <binder/IPCThreadState.h>

#include "CblkMemory.h"
#include <media/IAudioFlinger.h>
#include <media/HostPlay.h>

using namespace android;

sp<HostPlay> mHostPlay;
void* buffer;
JavaVM *g_jvm = NULL;
jobject g_obj = NULL;
JNIEnv *env;
jclass cls;
jmethodID mid;

void getJNIEnv(bool* needDetach) {
	*needDetach = false;
	if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
		ALOGE("pzhao-->AttachCurrentThread fail");
		return;
	}
	cls = env->GetObjectClass(g_obj);
	if (cls == NULL) {
		ALOGE("pzhao-->find class error");
		return;
	}
	mid = env->GetMethodID(cls, "fromJni", "(I)V");
	if (mid == NULL) {
		ALOGE("pzhao-->find method error");
		return;
	}
	*needDetach = true;
	ALOGE("pzhao->getJniEnv success");
}

void detachJNI() {
	if (g_jvm->DetachCurrentThread() != JNI_OK)
		ALOGD("pzhao-->DetachCurrentThread fail");
	ALOGE("pzhao->detachJniEnv success");
}

void fun(int x) {
//	JNIEnv * env;
//	jclass cls;
//	jmethodID mid;
//	ALOGE("pzhao->call back success %d", x);
//	if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
//		ALOGE("pzhao-->AttachCurrentThread fail");
//		return;
//	}
//	cls = env->GetObjectClass(g_obj);
//	if (cls == NULL) {
//		ALOGE("pzhao-->find class error");
//		goto error;
//	}
//	mid = env->GetMethodID(cls, "fromJni", "(I)V");
//	if (mid == NULL) {
//		ALOGE("pzhao-->find method error");
//		goto error;
//	}
	env->CallVoidMethod(g_obj, mid, x);
//	error: if (g_jvm->DetachCurrentThread() != JNI_OK)
//		ALOGD("pzhao-->DetachCurrentThread fail");
}

jint android_coop_HostPlay_native_setup(JNIEnv * env, jobject obj,
		jint receivebuffersize, jint sendbuffersize) {
	ALOGE("audio_test-->JNI setup");
	env->GetJavaVM(&g_jvm);
	g_obj = env->NewGlobalRef(obj);
	mHostPlay = new HostPlay();
	mHostPlay->setcallback(fun, getJNIEnv, detachJNI);
	return mHostPlay->create(receivebuffersize, sendbuffersize);
}

//add pzhao
jint android_coop_HostPlay_native_haswrite(JNIEnv * env, jobject obj) {
	if (mHostPlay != NULL) {
		return mHostPlay->haswrite;
	}
	return 0;
}

jboolean android_coop_HostPlay_native_needcheckwrited(JNIEnv * env,
		jclass clazz) {
	if (mHostPlay == NULL)
		return false;
	return mHostPlay->needcheckwrited();
}

void android_coop_HostPlay_native_setstartflag(JNIEnv * env, jobject obj,
		jint flag) {
	if (mHostPlay != NULL) {
		mHostPlay->setstartflag(flag);
	}
}

jboolean android_coop_HostPlay_native_checkstandbyflag(JNIEnv * env,
		jclass clazz) {
	if (mHostPlay == NULL)
		return true;
	return mHostPlay->standbyflag;
}

jboolean android_coop_HostPlay_native_checkreadpos(JNIEnv * env, jclass clazz) {
	if (mHostPlay == NULL)
		return false;
//	ALOGE("pzhao->TCP thread check readpos");
	return mHostPlay->checkCanRead();
}

jboolean android_coop_HostPlay_native_checkexitflagI(JNIEnv * env,
		jclass clazz) {
	if (mHostPlay == NULL)
		return true;
	return mHostPlay->exitflag1;
}

jboolean android_coop_HostPlay_native_checkexitflagII(JNIEnv * env,
		jclass clazz) {
	if (mHostPlay == NULL)
		return true;
	return mHostPlay->exitflag2;
}

void android_coop_HostPlay_native_setbuffertemp(JNIEnv* env, jobject thiz,
		jobject sendbuffer) {
	buffer = env->GetDirectBufferAddress(sendbuffer);
	jlong length = env->GetDirectBufferCapacity(sendbuffer);
	if (mHostPlay == NULL)
		return;
	mHostPlay->setBufferTemp(buffer, length >> 1); //length for 16bits
	mHostPlay->setHostMute();
}

void android_coop_HostPlay_native_setplayflag(JNIEnv * env, jclass clazz,
		jlong time_java) {
	if (mHostPlay == NULL)
		return;
	ALOGE("pzhao-->JNI::native_setplayflag");
	mHostPlay->changePlayFlag(true);
	mHostPlay->time_delay_flag = true;
	struct timeval tv;
	gettimeofday(&tv, NULL);
	long time_jni_host = tv.tv_sec * 1000000 + tv.tv_usec;
	ALOGE("pzhao-->host setPlayFlag:%fms", time_jni_host / 1000.0);
	mHostPlay->mHandle->time_delay_host(time_java);
}

void android_coop_HostPlay_native_setreadpos(JNIEnv * env, jclass clazz,
		jint pos) {
	if (mHostPlay == NULL)
		return;
//	ALOGE("pzhao->TCP thread set readpos %d",pos);
	mHostPlay->sendReadpos = pos >> 1;  //length for 16bits
}

void android_coop_HostPlay_native_read_ahead(JNIEnv * env, jobject obj,
		jint readahead) {
	if (mHostPlay == NULL)
		return;
	mHostPlay->readaheadflag = true;
	mHostPlay->readaheadcount = readahead;
}

void android_coop_HostPlay_native_exit(JNIEnv* env, jclass clazz) {
	ALOGE("audio_test-->JNI native_exit run.");
	mHostPlay->exit();
	mHostPlay.clear();
	if(buffer!=NULL)
		free(buffer);
}
void android_coop_HostPlay_native_sinagle_to_write(JNIEnv * env, jobject obj) {
	if (mHostPlay == NULL)
		return;
//	ALOGE("pzhao->singalToWrite");
	mHostPlay->singalToWrite();
}
static JNINativeMethod gMethods[] = { { "native_setup", "(II)I",
		(void*) android_coop_HostPlay_native_setup }, { "native_haswrite",
		"()I", (void*) android_coop_HostPlay_native_haswrite }, {
		"native_setstartflag", "(I)V",
		(void*) android_coop_HostPlay_native_setstartflag }, {
		"native_read_ahead", "(I)V",
		(void*) android_coop_HostPlay_native_read_ahead }, {
		"native_checkstandbyflag", "()Z",
		(void*) android_coop_HostPlay_native_checkstandbyflag }, {
		"native_checkreadpos", "()Z",
		(void*) android_coop_HostPlay_native_checkreadpos }, {
		"native_checkexitflagI", "()Z",
		(void*) android_coop_HostPlay_native_checkexitflagI }, {
		"native_checkexitflagII", "()Z",
		(void*) android_coop_HostPlay_native_checkexitflagII }, {
		"native_setplayflag", "(J)V",
		(void*) android_coop_HostPlay_native_setplayflag }, {
		"native_setreadpos", "(I)V",
		(void*) android_coop_HostPlay_native_setreadpos }, {
		"native_setbuffertemp", "(Ljava/nio/ByteBuffer;)V",
		(void*) android_coop_HostPlay_native_setbuffertemp }, { "native_exit",
		"()V", (void*) android_coop_HostPlay_native_exit }, {
		"native_needcheckwrited", "()Z",
		(void*) android_coop_HostPlay_native_needcheckwrited }, {
		"native_signaleToWrite", "()V",
		(void*) android_coop_HostPlay_native_sinagle_to_write }, };

static int register_android_middleware_AudioHost(JNIEnv *env) {
	return AndroidRuntime::registerNativeMethods(env, "com/njupt/middleware/DeviceManager",
			gMethods, NELEM(gMethods));
}

JNIEXPORT jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
	JNIEnv *e;
	int status;
	if (jvm->GetEnv((void**) &e, JNI_VERSION_1_6) != JNI_OK) {
		return JNI_ERR;
	}
	ALOGE("audio_test-->JNI: jni_onload().");
	if ((status = register_android_middleware_AudioHost(e)) < 0) {
		ALOGE("jni Mainactivity registration failure, status: %d", status);
		return JNI_ERR;
	}

	return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *jvm, void *reserved) {
	ALOGE("audio_test-->JNI: jni_OnUnload().");
	mHostPlay.clear();
	//mHostPlay = NULL;
}

