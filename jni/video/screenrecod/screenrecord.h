
#ifndef SCREENRECORD_SCREENRECORD_H
#define SCREENRECORD_SCREENRECORD_H

#define kVersionMajor 1
#define kVersionMinor 2

#include <assert.h>
#include <ctype.h>
#include <fcntl.h>
#include <inttypes.h>
//#include <getopt.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define ATRACE_TAG ATRACE_TAG_GRAPHICS
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <binder/IPCThreadState.h>
#include <utils/Errors.h>
#include <utils/Timers.h>
#include <utils/Trace.h>

#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/ISurfaceComposer.h>
#include <ui/DisplayInfo.h>
#include <media/openmax/OMX_IVCommon.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/MediaCodec.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaMuxer.h>
#include <media/ICrypto.h>

#include "screenrecord.h"
#include "Overlay.h"
#include "FrameOutput.h"

//for databuffer
#include "DataBuffer.h"
#include <utils/threads.h>
#include <utils/Debug.h>
#include <utils/Thread.h>
#include <pthread.h>



namespace android{

class ScreenRecord:public RefBase{
public:

		// Previous signal handler state, restored after first hit.
		static struct sigaction gOrigSigactionINT;
		static struct sigaction gOrigSigactionHUP;

		ScreenRecord(const char* size,const char* bitrate, bool rotate, const char* format, const char* filepath);
		status_t parseValueWithUnit(const char* str, uint32_t* pValue);
		bool parseWidthHeight(const char* widthHeight, uint32_t* pWidth, uint32_t* pHeight);
		status_t recordScreen(const char* fileName);
		FILE* prepareRawOutput(const char* fileName);
		status_t runEncoder(const sp<MediaCodec>& encoder, const sp<MediaMuxer>& muxer, FILE* rawFp, const sp<IBinder>& mainDpy, const sp<IBinder>& virtualDpy, uint8_t orientation);
		status_t prepareVirtualDisplay(const DisplayInfo& mainDpyInfo, const sp<IGraphicBufferProducer>& bufferProducer, sp<IBinder>* pDisplayHandle);
		status_t setDisplayProjection(const sp<IBinder>& dpy, const DisplayInfo& mainDpyInfo);
		status_t prepareEncoder(float displayFps, sp<MediaCodec>* pCodec, sp<IGraphicBufferProducer>* pBufferProducer);
		bool isDeviceRotated(int orientation);
		void setSlaveNum(int num);
		void stopRecord();
		void 		start_threads();
		void 		stop_threads();
		void		signalDataTransmitor();
		void		sleep(long time);

		DataBuffer*					mSendBuffer;
		void*						mSendAddr; //sort the data which are going to send
		int							sendcount;
		void*						mCompressAddr;
		uint8_t 					orientation;

		
protected:
	virtual ~ScreenRecord();

public:
	class VideoTransmitor : public Thread {

	public:
			VideoTransmitor(ScreenRecord* host);
    		virtual ~VideoTransmitor();
		    virtual     bool        threadLoop();
			bool		sendData(unsigned char * addr,int size);
			void        threadLoop_exit();
			void        threadLoop_run();
			void		sleep(long time);
			int compressRGBToJPEG(uint8_t* inbuf, uint8_t* outbuf, int width, int height);

	private:
		sp<ScreenRecord>		mScreenRecord;
	};  // class VideoTransmitor

private:
	sp<VideoTransmitor>		mVideoTransmitor;
	
};

};


#endif /*SCREENRECORD_SCREENRECORD_H*/
