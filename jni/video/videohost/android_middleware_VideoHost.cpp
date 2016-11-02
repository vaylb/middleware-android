#include <string.h>
#include <jni.h>
#include "JNIHelp.h"
#include <stdlib.h>
#include <utils/Log.h>
#include "android_runtime/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <sys/resource.h>

//vaylb video
#include <gui/ISurfaceComposer.h>
#include <private/gui/ComposerService.h>

#include <binder/IPCThreadState.h>  
#include <binder/ProcessState.h>  
#include <binder/IServiceManager.h> 
#include <videoshare/VideoShare.h>
#include <android_runtime/android_view_Surface.h> 


using namespace android;

sp<VideoShare> mVideoShare;
void* buffer;
JavaVM *g_jvm = NULL;
jobject g_obj = NULL;
JNIEnv *env;
jclass cls;
jmethodID mid;

void getJNIEnv(bool* needDetach) {
	ALOGE("vaylb->getJniEnv run");
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
	ALOGE("vaylb->getJniEnv success");
}

void detachJNI() {
	if (g_jvm->DetachCurrentThread() != JNI_OK)
		ALOGE("pzhao-->DetachCurrentThread fail");
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

void  android_coop_HostPlay_native_setvideohook(JNIEnv * env, jobject obj, jint flag)
{
	ALOGE("vaylb-->jni::setvideohook = %d",flag);

	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {
		sm->setVideoHook(flag);
    }
}

void  android_coop_HostPlay_native_setslavenum(JNIEnv * env, jobject obj, jint num)
{
	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {	
		ALOGE("vaylb-->jni::setslavenum = %d",num);
		sm->setSlaveNum(num);
    }
}

void  android_coop_HostPlay_initVideoShareClient()
{
	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {	
		ALOGE("vaylb-->jni::initVideoShareClient");
		sm->initVideoShareClient();
    }
}

void  android_coop_HostPlay_native_setscreensplit(JNIEnv * env, jobject obj, jint flag)
{
	ALOGE("vaylb-->jni::setscreensplit = %d",flag);

	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {
		sm->setScreenSplit(flag);
    }
}

static JNINativeMethod gMethods[] = { 
		{ "native_setslavenum","(I)V", (void*) android_coop_HostPlay_native_setslavenum}, 
		{ "native_setvideohook", "(I)V", (void*) android_coop_HostPlay_native_setvideohook },
		{ "native_setscreensplit", "(I)V", (void*) android_coop_HostPlay_native_setscreensplit },
};

static int register_android_middleware_VideoHost(JNIEnv *env) {
	return AndroidRuntime::registerNativeMethods(env, "com/njupt/middleware/DeviceManager",
			gMethods, NELEM(gMethods));
}

JNIEXPORT jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
	JNIEnv *e;
	int status;
	if (jvm->GetEnv((void**) &e, JNI_VERSION_1_6) != JNI_OK) {
		return JNI_ERR;
	}
	ALOGE("vaylb-->JNI: video host jni_onload().");
	if ((status = register_android_middleware_VideoHost(e)) < 0) {
		ALOGE("vaylb->jni ViedoHost registration failure, status: %d", status);
		return JNI_ERR;
	}

	sp<ProcessState> proc(ProcessState::self());  
	sp<IServiceManager> sm = defaultServiceManager();   
	VideoShare::instantiate(); 
	//if(mVideoShare==NULL) mVideoShare = new VideoShare();
	//mVideoShare->instantiate();

	android_coop_HostPlay_initVideoShareClient();
	//ProcessState::self()->startThreadPool();
	//IPCThreadState::self()->joinThreadPool();

	return JNI_VERSION_1_6;
}


JNIEXPORT void JNI_OnUnload(JavaVM *jvm, void *reserved) {
	ALOGE("vaylb_test-->JNI: jni_OnUnload().");
}

