#include <string.h>
#include <jni.h>
#include "JNIHelp.h"
#include <stdlib.h>
#include <utils/Log.h>
#include "android_runtime/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <sys/resource.h>
#include "screenrecord.h"

using namespace android;

sp<ScreenRecord> mScreenRecord;

void  android_coop_HostPlay_native_sr_setup(JNIEnv * env, jobject obj, jstring size, jstring bitrate, jint rotate, jstring format, jstring filepath)
{
	ALOGE("vaylb-->jni::setup_screenrecord");
	if(mScreenRecord == NULL){
		const char* chars_size = env->GetStringUTFChars(size, 0);
		const char* chars_bitrate = env->GetStringUTFChars(bitrate, 0);
		const char* chars_format = env->GetStringUTFChars(format, 0);
		const char* chars_filepath = env->GetStringUTFChars(filepath, 0);
		// String16 str_size_16, str_bitrate_16, str_format_16, str_filepath_16;
		// str_size_16.append((const char16_t*)chars_size,strlen(chars_size));
		// str_bitrate_16.append((const char16_t*)chars_bitrate,strlen(chars_bitrate));
		// str_format_16.append((const char16_t*)chars_format,strlen(chars_format));
		// str_filepath_16.append((const char16_t*)chars_filepath,strlen(chars_filepath));

		mScreenRecord = new ScreenRecord(chars_size, chars_bitrate, rotate==1?true:false, chars_format, chars_filepath);

		env->ReleaseStringUTFChars(size, chars_size);
		env->ReleaseStringUTFChars(bitrate, chars_bitrate);
		env->ReleaseStringUTFChars(format, chars_format);
		env->ReleaseStringUTFChars(filepath, chars_filepath);	
	}
}

void  android_coop_HostPlay_native_sr_start(JNIEnv * env, jobject obj, jstring filepath)
{
	if(mScreenRecord != NULL){
		const char* chars_filepath = env->GetStringUTFChars(filepath, 0);
		mScreenRecord->recordScreen(chars_filepath);
		env->ReleaseStringUTFChars(filepath, chars_filepath);
	}
}

void  android_coop_HostPlay_native_sr_setslavenum(JNIEnv * env, jobject obj, jint num)
{
    if (mScreenRecord != NULL) {	
		mScreenRecord->setSlaveNum(num);
    }
}

void  android_coop_HostPlay_native_sr_stop(JNIEnv * env, jobject obj)
{
    if (mScreenRecord != NULL) {	
		mScreenRecord->stopRecord();
    }
}


static JNINativeMethod gMethods[] = { 
		{ "native_screenrecord_setup","(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V", (void*) android_coop_HostPlay_native_sr_setup}, 
		{ "native_screenrecord_start", "(Ljava/lang/String;)V", (void*) android_coop_HostPlay_native_sr_start },
		{ "native_screenrecord_setslavenum","(I)V", (void*) android_coop_HostPlay_native_sr_setslavenum}, 
		{ "native_screenrecord_stop","()V", (void*) android_coop_HostPlay_native_sr_stop}, 
};

static int register_android_middleware_screenrecord(JNIEnv *env) {
	return AndroidRuntime::registerNativeMethods(env, "com/njupt/middleware/DeviceManager",
			gMethods, NELEM(gMethods));
}

JNIEXPORT jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
	JNIEnv *e;
	int status;
	if (jvm->GetEnv((void**) &e, JNI_VERSION_1_6) != JNI_OK) {
		return JNI_ERR;
	}
	
	if ((status = register_android_middleware_screenrecord(e)) < 0) {
		ALOGE("vaylb-->jni ViedoHost registration failure, status: %d", status);
		return JNI_ERR;
	}
	ALOGE("vaylb-->JNI: screenrecord jni_onload() success.");
	return JNI_VERSION_1_6;
}